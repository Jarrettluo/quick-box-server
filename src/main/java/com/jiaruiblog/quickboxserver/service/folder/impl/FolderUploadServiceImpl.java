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
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private final List<FolderEventListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicLong totalFolders = new AtomicLong(0);

    private final AtomicLong totalFiles = new AtomicLong(0);

    private final AtomicLong totalSize = new AtomicLong(0);

    private final AtomicLong activeUploads = new AtomicLong(0);

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
                request.getStructureJson()
            );

            // 创建上传会话
            FolderUploadSession session = new FolderUploadSession();
            // 存入会话的id，以便下次上传的时候可以取到
            session.setSessionId(sessionId);

            session.setFolderId(folderId);
            session.setFolderName(request.getFolderName());
            session.setAccessCode(accessCode);
            session.setTotalFiles(request.getTotalFiles());
            session.setTotalSize(request.getTotalSize());
            session.setStructureJson(request.getStructureJson());
            session.setAutoZip(request.getAutoZip());
            session.setKeepStructure(request.getKeepStructure());
            session.setZipUpload(request.getIsZipUpload());
            session.setStorageBackend(storageService.getStorageName());
            session.setCreateTime(LocalDateTime.now());
            session.setExpireTime(LocalDateTime.now().plusSeconds(request.getExpireSeconds()));
            session.setStatus(FolderUploadResponse.UploadStatus.INITIALIZED);

            if (request.getMetadata() != null) {
                session.setMetadata(request.getMetadata());
            }

            // 保存到Redis
            saveSessionToRedis(session);
            saveFolderInfoToRedis(createFolderInfoFromSession(session));

            // 保存到内存
            uploadSessions.put(sessionId, session);

            // 更新统计
            activeUploads.incrementAndGet();

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);
            log.info("文件夹上传初始化成功: {} -> {}", sessionId, accessCode);

            // 触发事件
            fireFolderUploadStarted(response);

            return response;
        } catch (Exception e) {
            log.error("初始化文件夹上传失败", e);
            throw new BusinessException(ErrorCode.FOLDER_UPLOAD_INIT_FAILED, e.getMessage());
        }
    }

    @Override
    public FolderUploadResponse uploadFolderChunk(FolderChunkUploadRequest request) {
        log.info("上传文件夹分片: {} - {}", request.getSessionId(), request.getChunkNumber());

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

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(session.getStorageBackend());

            // 更新会话状态
            session.setStatus(FolderUploadResponse.UploadStatus.UPLOADING);
            session.setLastUpdateTime(LocalDateTime.now());

            // 上传分片
            // TODO，前端是一个文件一个文件的分片过来的；导致后端在生成chunk的时候必须按照单个文件的identify进行区分
            storageService.uploadFolderChunk(
                session.getSessionId(),
                request.getChunkNumber(),
                request.getFile().getInputStream(),
                request.getCurrentChunkSize()
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
            response.setUploadedFiles(calculateUploadedFiles(session));

            log.debug("文件夹分片上传成功: {} - {} ({} bytes)",
                request.getSessionId(), request.getChunkNumber(), request.getCurrentChunkSize());

            // 触发事件
            fireFolderChunkUploaded(response, request.getChunkNumber());

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
            activeUploads.decrementAndGet();
            completedUploads.incrementAndGet();
            totalFolders.incrementAndGet();
            totalFiles.addAndGet(session.getTotalFiles());
            totalSize.addAndGet(session.getTotalSize());

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);
            response.setFolderPath(folderPath);

            log.info("文件夹分片合并成功: {} -> {}", sessionId, folderPath);

            // 触发事件
            fireFolderUploadCompleted(response);

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

            // 更新统计
            activeUploads.decrementAndGet();

            // 创建响应
            FolderUploadResponse response = createResponseFromSession(session);

            log.info("文件夹上传取消成功: {}", sessionId);

            // 触发事件
            fireFolderUploadCancelled(response);
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
        response.setUploadedFiles(calculateUploadedFiles(session));

        // 计算上传速度（如果可能）
        if (session.getLastUpdateTime() != null && session.getCreateTime() != null) {
            long duration = java.time.Duration.between(session.getCreateTime(), LocalDateTime.now()).getSeconds();
            if (duration > 0) {
                response.setUploadSpeed(session.getUploadedSize() / duration);
            }
        }

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

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(folderInfo.getStorageBackend());

            // 获取文件夹详细信息
            if (folderInfo.getFolderPath() != null) {
                com.jiaruiblog.quickboxserver.storage.model.FolderInfo storageFolderInfo =
                    storageService.getFolderInfo(folderInfo.getFolderPath());

                if (storageFolderInfo != null) {
                    response.setTotalFiles(storageFolderInfo.getTotalFiles());
                    response.setTotalFolders(storageFolderInfo.getTotalFolders());
                    response.setTotalSize(storageFolderInfo.getTotalSize());
                }
            }

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

            // 触发事件
            FolderInfoResponse response = createInfoResponseFromFolderInfo(folderInfo);
            fireFolderDownloadStarted(response);

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
    public InputStream downloadFolderFile(String accessCode, String relativePath) {
        log.debug("下载文件夹文件: {} -> {}", accessCode, relativePath);

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

            // 获取存储服务
            StorageService storageService = storageServiceFactory.getStorageService(folderInfo.getStorageBackend());

            // 下载文件
            return storageService.downloadFolderFile(folderInfo.getFolderPath(), relativePath);
        } catch (BusinessException e) {
            log.error("下载文件夹文件业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("下载文件夹文件失败", e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, e.getMessage());
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
        if (redisTemplate.hasKey(REDIS_PREFIX_ACCESS_CODE + accessCode)) {
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
        folderInfo.setZipUpload(session.isZipUpload());
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
        response.setStorageBackend(session.getStorageBackend());
        response.setIsZipUpload(session.isZipUpload());
        response.setFolderPath(session.getFolderPath());
        response.setMetadata(session.getMetadata());

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
        response.setDownloadCount(folderInfo.getDownloadCount());
        response.setLastDownloadTime(folderInfo.getLastDownloadTime());
        response.setStorageBackend(folderInfo.getStorageBackend());
        response.setFolderPath(folderInfo.getFolderPath());
        response.setIsZipUpload(folderInfo.isZipUpload());
        response.setKeepStructure(folderInfo.getKeepStructure());
        response.setStructureJson(folderInfo.getStructureJson());
        response.setMetadata(folderInfo.getMetadata());

        // 计算剩余过期时间
        if (folderInfo.getExpireTime() != null) {
            long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), folderInfo.getExpireTime()).getSeconds();
            response.setRemainingExpireSeconds(Math.max(0, remainingSeconds));
            response.setExpired(remainingSeconds <= 0);
        }

        return response;
    }

    private int calculateUploadedFiles(FolderUploadSession session) {
        // 简单估算：假设每个分片对应一个文件
        // 实际实现中应该根据具体上传情况计算
        if (session.isZipUpload()) {
            // ZIP上传：分片数不一定等于文件数
            return (int) (session.getUploadedChunks() * 1.0 / session.getTotalFiles() * session.getTotalFiles());
        } else {
            // 普通文件夹上传：每个文件可能有多个分片
            return Math.min(session.getUploadedChunks(), session.getTotalFiles());
        }
    }

    // ==================== 事件触发方法 ====================

    private void fireFolderUploadStarted(FolderUploadResponse response) {
        for (FolderEventListener listener : listeners) {
            try {
                listener.onFolderUploadStarted(response);
            } catch (Exception e) {
                log.error("触发文件夹上传开始事件失败", e);
            }
        }
    }

    private void fireFolderChunkUploaded(FolderUploadResponse response, int chunkNumber) {
        for (FolderEventListener listener : listeners) {
            try {
                listener.onFolderChunkUploaded(response, chunkNumber);
            } catch (Exception e) {
                log.error("触发文件夹分片上传事件失败", e);
            }
        }
    }

    private void fireFolderUploadCompleted(FolderUploadResponse response) {
        for (FolderEventListener listener : listeners) {
            try {
                listener.onFolderUploadCompleted(response);
            } catch (Exception e) {
                log.error("触发文件夹上传完成事件失败", e);
            }
        }
    }

    private void fireFolderUploadCancelled(FolderUploadResponse response) {
        for (FolderEventListener listener : listeners) {
            try {
                listener.onFolderUploadCancelled(response);
            } catch (Exception e) {
                log.error("触发文件夹上传取消事件失败", e);
            }
        }
    }

    private void fireFolderDownloadStarted(FolderInfoResponse response) {
        for (FolderEventListener listener : listeners) {
            try {
                listener.onFolderDownloadStarted(response);
            } catch (Exception e) {
                log.error("触发文件夹下载开始事件失败", e);
            }
        }
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
        private boolean zipUpload = false;
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
        private boolean zipUpload = false;
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

    // ==================== 未实现的方法 ====================

    @Override
    public void updateFolderMetadata(String accessCode, String metadata) {
        // TODO: 实现更新文件夹元数据
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public String getFolderDownloadUrl(String accessCode) {
        // TODO: 实现获取文件夹下载URL
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public String getFileDownloadUrl(String accessCode, String relativePath) {
        // TODO: 实现获取文件下载URL
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public void cleanupExpiredFolders() {
        // TODO: 实现清理过期文件夹
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public void deleteFolder(String accessCode) {
        // TODO: 实现删除文件夹
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public void batchDeleteFolders(List<String> accessCodes) {
        // TODO: 实现批量删除文件夹
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public FolderStats getFolderStats() {
        // TODO: 实现获取文件夹统计信息
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public UploadSessionStats getUploadSessionStats() {
        // TODO: 实现获取上传会话统计
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public StorageUsageStats getStorageUsageStats() {
        // TODO: 实现获取存储使用情况
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public boolean validateFolderName(String folderName) {
        // TODO: 实现验证文件夹名称
        return StringUtils.hasText(folderName) && folderName.length() <= 255;
    }

    @Override
    public boolean validateFilePath(String relativePath) {
        // TODO: 实现验证文件路径
        return StringUtils.hasText(relativePath) && !relativePath.contains("..");
    }

    @Override
    public String calculateFolderHash(String accessCode) {
        // TODO: 实现计算文件夹哈希值
        throw new UnsupportedOperationException("未实现");
    }

    @Override
    public void addFolderEventListener(FolderEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeFolderEventListener(FolderEventListener listener) {
        listeners.remove(listener);
    }
}