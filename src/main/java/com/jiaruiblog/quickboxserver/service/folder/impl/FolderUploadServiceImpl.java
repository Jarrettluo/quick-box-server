package com.jiaruiblog.quickboxserver.service.folder.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaruiblog.quickboxserver.exception.BusinessException;
import com.jiaruiblog.quickboxserver.exception.ErrorCode;
import com.jiaruiblog.quickboxserver.model.folder.FolderChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.folder.FolderInfoResponse;
import com.jiaruiblog.quickboxserver.model.folder.FolderUploadRequest;
import com.jiaruiblog.quickboxserver.model.folder.FolderUploadResponse;
import com.jiaruiblog.quickboxserver.service.folder.FolderUploadService;
import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.StorageServiceFactory;
import com.jiaruiblog.quickboxserver.storage.strategy.ConfigurableStorageStrategy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件夹上传服务实现
 */
@AllArgsConstructor
@Slf4j
@Service
public class FolderUploadServiceImpl implements FolderUploadService {

    private RedisTemplate<String, Object> redisTemplate;

    private StorageServiceFactory storageServiceFactory;

    private ConfigurableStorageStrategy storageStrategy;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, FolderUploadSession> uploadSessions = new ConcurrentHashMap<>();

    private final Map<String, FolderInfo> folderInfos = new ConcurrentHashMap<>();

    private final AtomicLong completedUploads = new AtomicLong(0);


    // Redis键前缀
    private static final String REDIS_PREFIX_FOLDER = "folder:";

    private static final String REDIS_PREFIX_SESSION = "folder_session:";

    private static final String REDIS_PREFIX_ACCESS_CODE = "access_code:";

    @Override
    public FolderUploadResponse initFolderUpload(FolderUploadRequest request) {
        log.info("初始化文件夹上传: {}", request.getFolderName());

        try {
            // 生成文件夹ID和取件码
            String folderId = UUID.randomUUID().toString();
            String accessCode = generateAccessCode();

            // 选择存储服务
            StorageService storageService = storageStrategy.selectStorageServiceForFolder();

            // 初始化文件夹上传会话
            String sessionId = storageService.initFolderUpload(
                folderId,
                request.getFolderName(),
                request.getTotalFiles(),
                request.getTotalSize(),
                null
            );

            // 创建上传会话
            FolderUploadSession session = new FolderUploadSession();
            session.setSessionId(sessionId);
            session.setFolderId(folderId);
            session.setFolderName(request.getFolderName());

            session.setAccessCode(accessCode);

            session.setTotalFiles(request.getTotalFiles());
            session.setTotalSize(request.getTotalSize());

            session.setStorageBackend(storageService.getStorageName());
            session.setCreateTime(LocalDateTime.now());
            session.setExpireTime(LocalDateTime.now().plusSeconds(request.getExpireSeconds()));
            session.setStatus(FolderUploadResponse.UploadStatus.INITIALIZED);

            // 保存到Redis
            saveSessionToRedis(session);
            saveFolderInfoToRedis(createFolderInfoFromSession(session));

            // 保存到内存
            uploadSessions.put(sessionId, session);

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);
            log.info("文件夹上传初始化成功: {} -> {}", sessionId, accessCode);

            return response;
        } catch (Exception e) {
            log.error("初始化文件夹上传失败", e);
            throw new BusinessException(ErrorCode.FOLDER_UPLOAD_INIT_FAILED, e.getMessage());
        }
    }

    @Override
    public FolderUploadResponse uploadFolderChunk(FolderChunkUploadRequest request) {
        log.info("上传文件夹分片: {} - {}", request.getSessionId(), request.getChunkNumber());
        log.info("当前分片上传的request内容是：{}", request);
        try {
            // 获取上传会话
            FolderUploadSession session = getSession(request.getSessionId());
            if (session == null) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
            }

            // 检查会话状态
            if (session.getStatus() != FolderUploadResponse.UploadStatus.INITIALIZED &&
                session.getStatus() != FolderUploadResponse.UploadStatus.UPLOADING &&
                session.getStatus() != FolderUploadResponse.UploadStatus.MERGING) {
                throw new BusinessException(ErrorCode.SESSION_INVALID_STATE);
            }
            log.info("获取的 session 信息是：{}", session);

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(session.getStorageBackend());

            // 更新会话状态
            session.setStatus(FolderUploadResponse.UploadStatus.UPLOADING);
            session.setLastUpdateTime(LocalDateTime.now());

            // 上传分片
            storageService.uploadFolderChunk(
                session.getSessionId(),
                request.getChunkNumber(),
                request.getFile().getInputStream(),
                request.getCurrentChunkSize(),
                request.getRelativePath(),
                request.getFilename()
            );

            // 更新会话进度
            session.setUploadedChunks(session.getUploadedChunks() + 1);
            session.setUploadedSize(session.getUploadedSize() + request.getCurrentChunkSize());

            // 如果是最后一个分片，更新状态
            if (request.isLastChunk()) {
                session.setStatus(FolderUploadResponse.UploadStatus.MERGING);
            }

            // 保存到Redis
            saveSessionToRedis(session);

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);

            log.debug("文件夹分片上传成功: {} - {} ({} bytes)",
                request.getSessionId(), request.getChunkNumber(), request.getCurrentChunkSize());

            return response;
        } catch (BusinessException e) {
            log.error("上传文件夹分片业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("上传文件夹分片失败", e);
            throw new BusinessException(ErrorCode.FOLDER_CHUNK_UPLOAD_FAILED, e.getMessage());
        }
    }

    @Override
    public FolderUploadResponse mergeFolderChunks(String sessionId) {
        log.info("合并文件夹分片: {}", sessionId);

        try {
            // 获取上传会话
            FolderUploadSession session = getSession(sessionId);
            if (session == null) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
            }

            // 检查会话状态
            if (session.getStatus() != FolderUploadResponse.UploadStatus.MERGING) {
                throw new BusinessException(ErrorCode.SESSION_INVALID_STATE);
            }

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(session.getStorageBackend());

            // 合并分片
            log.info("合并文件夹的时候的session_id: {}", sessionId);
            String folderPath = storageService.mergeFolderChunks(session.getSessionId());

            // 更新会话状态
            session.setStatus(FolderUploadResponse.UploadStatus.COMPLETED);
            session.setFolderPath(folderPath);
            session.setCompleteTime(LocalDateTime.now());
            session.setLastUpdateTime(LocalDateTime.now());

            // 更新文件夹信息
            FolderInfo folderInfo = getFolderInfoFromRedis(session.getAccessCode());
            if (folderInfo != null) {
                folderInfo.setFolderPath(folderPath);
                folderInfo.setStatus(FolderUploadResponse.UploadStatus.COMPLETED);
//                folderInfo.setCompleteTime(LocalDateTime.now());
                saveFolderInfoToRedis(folderInfo);
                folderInfos.put(session.getAccessCode(), folderInfo);
            }

            // 从上传会话中移除
            uploadSessions.remove(sessionId);
            redisTemplate.delete(REDIS_PREFIX_SESSION + sessionId);

            // 更新统计
            completedUploads.incrementAndGet();

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);
            response.setFolderPath(folderPath);

            log.info("文件夹分片合并成功: {} -> {}", sessionId, folderPath);

            return response;
        } catch (BusinessException e) {
            log.error("合并文件夹分片业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("合并文件夹分片失败", e);
            throw new BusinessException(ErrorCode.FOLDER_MERGE_FAILED, e.getMessage());
        }
    }

    @Override
    public void cancelFolderUpload(String sessionId) {
        log.info("取消文件夹上传: {}", sessionId);

        try {
            // 获取上传会话
            FolderUploadSession session = getSession(sessionId);
            if (session == null) {
                return;
            }

            // 更新会话状态
            session.setStatus(FolderUploadResponse.UploadStatus.CANCELLED);
            session.setLastUpdateTime(LocalDateTime.now());

            // 从上传会话中移除
            uploadSessions.remove(sessionId);
            redisTemplate.delete(REDIS_PREFIX_SESSION + sessionId);

            // 删除Redis中的文件夹信息
            redisTemplate.delete(REDIS_PREFIX_FOLDER + session.getAccessCode());
            redisTemplate.delete(REDIS_PREFIX_ACCESS_CODE + session.getAccessCode());

            log.info("文件夹上传取消成功: {}", sessionId);
        } catch (Exception e) {
            log.error("取消文件夹上传失败", e);
            throw new BusinessException(ErrorCode.FOLDER_CANCEL_FAILED, e.getMessage());
        }
    }

    @Override
    public FolderUploadResponse getUploadProgress(String sessionId) {
        FolderUploadSession session = getSession(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        FolderUploadResponse response = createResponseFromSession(session);
        return response;
    }

    @Override
    public FolderInfoResponse getFolderInfo(String accessCode) {
        log.debug("获取文件夹信息: {}", accessCode);

        try {
            // 从Redis获取文件夹信息
            FolderInfo folderInfo = getFolderInfoFromRedis(accessCode);
            if (folderInfo == null) {
                throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
            }

            // 检查是否过期
            if (folderInfo.getExpireTime() != null && folderInfo.getExpireTime().isBefore(LocalDateTime.now())) {
                folderInfo.setExpired(true);
                saveFolderInfoToRedis(folderInfo);
                throw new BusinessException(ErrorCode.FOLDER_EXPIRED);
            }

            // 检查是否已下载
            if (folderInfo.getDownloaded() != null && folderInfo.getDownloaded()) {
                throw new BusinessException(ErrorCode.FOLDER_ALREADY_DOWNLOADED);
            }

            // 创建响应
            FolderInfoResponse response = createInfoResponseFromFolderInfo(folderInfo);

            // 计算剩余过期时间
            if (folderInfo.getExpireTime() != null) {
                long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), folderInfo.getExpireTime()).getSeconds();
                response.setRemainingExpireSeconds(Math.max(0, remainingSeconds));
                response.setExpired(remainingSeconds <= 0);
            }

            log.debug("获取文件夹信息成功: {}", accessCode);
            return response;
        } catch (BusinessException e) {
            log.error("获取文件夹信息业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("获取文件夹信息失败", e);
            throw new BusinessException(ErrorCode.FOLDER_INFO_FAILED, e.getMessage());
        }
    }

    @Override
    public boolean validateAccessCode(String accessCode) {
        try {
            FolderInfo folderInfo = getFolderInfoFromRedis(accessCode);
            if (folderInfo == null) {
                return false;
            }

            // 检查是否过期
            if (folderInfo.getExpireTime() != null && folderInfo.getExpireTime().isBefore(LocalDateTime.now())) {
                return false;
            }

            // 检查是否已下载
            if (folderInfo.getDownloaded() != null && folderInfo.getDownloaded()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("验证取件码失败", e);
            return false;
        }
    }

    @Override
    public InputStream downloadFolderAsZip(String accessCode) {
        log.info("下载文件夹为ZIP: {}", accessCode);

        try {
            // 获取文件夹信息
            FolderInfo folderInfo = getFolderInfoFromRedis(accessCode);
            if (folderInfo == null) {
                throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
            }

            // 检查是否过期
            if (folderInfo.getExpireTime() != null && folderInfo.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.FOLDER_EXPIRED);
            }

            // 检查是否已下载
            if (folderInfo.getDownloaded() != null && folderInfo.getDownloaded()) {
                throw new BusinessException(ErrorCode.FOLDER_ALREADY_DOWNLOADED);
            }

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(folderInfo.getStorageBackend());

            // 下载文件夹
            InputStream zipStream = storageService.downloadFolderAsZip(folderInfo.getFolderPath());

            // 更新文件夹信息
            folderInfo.setDownloaded(true);
            folderInfo.setDownloadCount(folderInfo.getDownloadCount() + 1);
            folderInfo.setLastDownloadTime(LocalDateTime.now());
            saveFolderInfoToRedis(folderInfo);

            log.info("文件夹下载开始: {}", accessCode);
            return zipStream;
        } catch (BusinessException e) {
            log.error("下载文件夹业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("下载文件夹失败", e);
            throw new BusinessException(ErrorCode.FOLDER_DOWNLOAD_FAILED, e.getMessage());
        }
    }

    @Override
    public String generateAccessCode() {
        // 生成6位大写字母随机码
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder code = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }

        String accessCode = code.toString();

        // 检查是否已存在
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_PREFIX_ACCESS_CODE + accessCode))) {
            // 递归生成新的取件码
            return generateAccessCode();
        }

        return accessCode;
    }

    // ==================== 辅助方法 ====================

    private FolderUploadSession getSession(String sessionId) {
        // 首先从内存中获取
        FolderUploadSession session = uploadSessions.get(sessionId);
        if (session != null) {
            return session;
        }

        // 从Redis中获取
        String redisKey = REDIS_PREFIX_SESSION + sessionId;
        String sessionJson = (String) redisTemplate.opsForValue().get(redisKey);
        if (sessionJson != null) {
            try {
                session = objectMapper.readValue(sessionJson, FolderUploadSession.class);
                uploadSessions.put(sessionId, session);
                return session;
            } catch (Exception e) {
                log.error("解析上传会话JSON失败", e);
            }
        }

        return null;
    }

    private void saveSessionToRedis(FolderUploadSession session) {
        try {
            String sessionJson = objectMapper.writeValueAsString(session);
            String redisKey = REDIS_PREFIX_SESSION + session.getSessionId();

            // 计算过期时间（会话创建时间 + 24小时）
            long expireSeconds = 24 * 60 * 60;
            if (session.getExpireTime() != null) {
                expireSeconds = java.time.Duration.between(LocalDateTime.now(), session.getExpireTime()).getSeconds();
            }

            redisTemplate.opsForValue().set(redisKey, sessionJson, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("保存上传会话到Redis失败", e);
        }
    }

    private FolderInfo getFolderInfoFromRedis(String accessCode) {
        // 首先从内存中获取
        FolderInfo folderInfo = folderInfos.get(accessCode);
        if (folderInfo != null) {
            return folderInfo;
        }

        // 从Redis中获取
        String redisKey = REDIS_PREFIX_FOLDER + accessCode;
        String folderJson = (String) redisTemplate.opsForValue().get(redisKey);
        if (folderJson != null) {
            try {
                folderInfo = objectMapper.readValue(folderJson, FolderInfo.class);
                folderInfos.put(accessCode, folderInfo);
                return folderInfo;
            } catch (Exception e) {
                log.error("解析文件夹信息JSON失败", e);
            }
        }

        return null;
    }

    private void saveFolderInfoToRedis(FolderInfo folderInfo) {
        try {
            String folderJson = objectMapper.writeValueAsString(folderInfo);
            String redisKey = REDIS_PREFIX_FOLDER + folderInfo.getAccessCode();

            // 计算过期时间
            long expireSeconds = 7 * 24 * 60 * 60; // 默认7天
            if (folderInfo.getExpireTime() != null) {
                expireSeconds = java.time.Duration.between(LocalDateTime.now(), folderInfo.getExpireTime()).getSeconds();
            }

            redisTemplate.opsForValue().set(redisKey, folderJson, expireSeconds, TimeUnit.SECONDS);

            // 保存取件码映射
            redisTemplate.opsForValue().set(
                REDIS_PREFIX_ACCESS_CODE + folderInfo.getAccessCode(),
                folderInfo.getFolderId(),
                expireSeconds,
                TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("保存文件夹信息到Redis失败", e);
        }
    }

    private FolderInfo createFolderInfoFromSession(FolderUploadSession session) {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.setFolderId(session.getFolderId());
        folderInfo.setFolderName(session.getFolderName());
        folderInfo.setAccessCode(session.getAccessCode());
        folderInfo.setTotalFiles(session.getTotalFiles());
        folderInfo.setTotalSize(session.getTotalSize());
        folderInfo.setStructureJson(session.getStructureJson());
        folderInfo.setAutoZip(session.getAutoZip());
        folderInfo.setKeepStructure(session.getKeepStructure());
        folderInfo.setStorageBackend(session.getStorageBackend());
        folderInfo.setCreateTime(session.getCreateTime());
        folderInfo.setExpireTime(session.getExpireTime());
        folderInfo.setStatus(session.getStatus());
        folderInfo.setMetadata(session.getMetadata());
        return folderInfo;
    }

    private FolderUploadResponse createResponseFromSession(FolderUploadSession session) {
        FolderUploadResponse response = new FolderUploadResponse();
        response.setSessionId(session.getSessionId());
        response.setFolderId(session.getFolderId());
        response.setFolderName(session.getFolderName());
        response.setAccessCode(session.getAccessCode());
        response.setStatus(session.getStatus());
        response.setCreateTime(session.getCreateTime());
        response.setExpireTime(session.getExpireTime());
        response.setTotalFiles(session.getTotalFiles());
        response.setTotalSize(session.getTotalSize());
        response.setUploadedSize(session.getUploadedSize());
        response.setFolderPath(session.getFolderPath());

        // 计算进度
        if (session.getTotalSize() > 0) {
            response.setProgress((session.getUploadedSize() * 100.0) / session.getTotalSize());
        } else if (session.getTotalFiles() > 0) {
            response.setProgress((session.getUploadedChunks() * 100.0) / session.getTotalFiles());
        }

        return response;
    }

    private FolderInfoResponse createInfoResponseFromFolderInfo(FolderInfo folderInfo) {
        FolderInfoResponse response = new FolderInfoResponse();
        response.setFolderId(folderInfo.getFolderId());
        response.setFolderName(folderInfo.getFolderName());
        response.setAccessCode(folderInfo.getAccessCode());
        response.setTotalFiles(folderInfo.getTotalFiles());
        response.setTotalSize(folderInfo.getTotalSize());
        response.setCreateTime(folderInfo.getCreateTime());
        response.setExpireTime(folderInfo.getExpireTime());
        response.setDownloaded(folderInfo.getDownloaded());

        // 计算剩余过期时间
        if (folderInfo.getExpireTime() != null) {
            long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), folderInfo.getExpireTime()).getSeconds();
            response.setRemainingExpireSeconds(Math.max(0, remainingSeconds));
            response.setExpired(remainingSeconds <= 0);
        }

        return response;
    }

    // ==================== 内部类 ====================

    @lombok.Data
    private static class FolderUploadSession {
        private String sessionId;
        private String storageSessionId;
        private String folderId;
        private String folderName;
        private String accessCode;
        private Integer totalFiles;
        private Long totalSize;
        private String structureJson;
        private Boolean autoZip = true;
        private Boolean keepStructure = true;
        private String storageBackend;
        private LocalDateTime createTime;
        private LocalDateTime expireTime;
        private LocalDateTime lastUpdateTime;
        private LocalDateTime completeTime;
        private FolderUploadResponse.UploadStatus status;
        private int uploadedChunks = 0;
        private long uploadedSize = 0;
        private String folderPath;
        private String metadata;
    }

    @lombok.Data
    private static class FolderInfo {
        private String folderId;
        private String folderName;
        private String accessCode;
        private Integer totalFiles;
        private Long totalSize;
        private String structureJson;
        private Boolean autoZip = true;
        private Boolean keepStructure = true;
        private String storageBackend;
        private LocalDateTime createTime;
        private LocalDateTime expireTime;
        private Boolean downloaded = false;
        private Integer downloadCount = 0;
        private LocalDateTime lastDownloadTime;
        private String folderPath;
        private FolderUploadResponse.UploadStatus status;
        private Boolean expired = false;
        private String metadata;
    }

    // ==================== 文件夹删除和清理 ====================

    @Override
    public void cleanupExpiredFolders() {
        log.info("开始清理过期文件夹");

        try {
            String pattern = REDIS_PREFIX_FOLDER + "*";
            var keys = redisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                log.info("没有需要清理的过期文件夹");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int cleanedCount = 0;

            for (String key : keys) {
                try {
                    String folderJson = (String) redisTemplate.opsForValue().get(key);
                    if (folderJson == null) {
                        continue;
                    }

                    FolderInfo folderInfo = objectMapper.readValue(folderJson, FolderInfo.class);

                    if (folderInfo.getExpireTime() != null && folderInfo.getExpireTime().isBefore(now)) {
                        if (folderInfo.getFolderPath() != null) {
                            StorageService storageService = storageServiceFactory.getStorageService(folderInfo.getStorageBackend());
                            storageService.deleteFolder(folderInfo.getFolderPath());
                        }

                        redisTemplate.delete(key);
                        redisTemplate.delete(REDIS_PREFIX_ACCESS_CODE + folderInfo.getAccessCode());
                        folderInfos.remove(folderInfo.getAccessCode());

                        cleanedCount++;
                        log.debug("已清理过期文件夹: {}", folderInfo.getAccessCode());
                    }
                } catch (Exception e) {
                    log.error("清理文件夹失败: {}", key, e);
                }
            }

            log.info("清理过期文件夹完成，共清理 {} 个", cleanedCount);
        } catch (Exception e) {
            log.error("清理过期文件夹失败", e);
            throw new BusinessException(ErrorCode.FOLDER_CLEANUP_FAILED, e.getMessage());
        }
    }

    @Override
    public void deleteFolder(String accessCode) {
        log.info("删除文件夹: {}", accessCode);

        try {
            FolderInfo folderInfo = getFolderInfoFromRedis(accessCode);
            if (folderInfo == null) {
                throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
            }

            if (folderInfo.getFolderPath() != null) {
                StorageService storageService = storageServiceFactory.getStorageService(folderInfo.getStorageBackend());
                storageService.deleteFolder(folderInfo.getFolderPath());
            }

            redisTemplate.delete(REDIS_PREFIX_FOLDER + accessCode);
            redisTemplate.delete(REDIS_PREFIX_ACCESS_CODE + accessCode);
            folderInfos.remove(accessCode);

            log.info("删除文件夹成功: {}", accessCode);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除文件夹失败", e);
            throw new BusinessException(ErrorCode.FOLDER_DELETE_FAILED, e.getMessage());
        }
    }

    @Override
    public void batchDeleteFolders(List<String> accessCodes) {
        log.info("批量删除文件夹: {} 个", accessCodes.size());

        try {
            for (String accessCode : accessCodes) {
                deleteFolder(accessCode);
            }

            log.info("批量删除文件夹完成: {} 个", accessCodes.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量删除文件夹失败", e);
            throw new BusinessException(ErrorCode.FOLDER_DELETE_FAILED, e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean validateFilePath(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        try {
            String decoded = URLDecoder.decode(relativePath, StandardCharsets.UTF_8.name());
            if (decoded.contains("..") || decoded.contains("\\")) {
                return false;
            }
            if (decoded.contains("/../") || decoded.startsWith("../") || decoded.endsWith("/..")) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}