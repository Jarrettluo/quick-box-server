package com.jiaruiblog.quickboxserver.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaruiblog.quickboxserver.config.FileStorageConfig;
import com.jiaruiblog.quickboxserver.exception.BusinessException;
import com.jiaruiblog.quickboxserver.exception.ErrorCode;
import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import com.jiaruiblog.quickboxserver.service.FileUploadService;
import com.jiaruiblog.quickboxserver.service.folder.FolderUploadService;
import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.StorageServiceFactory;
import com.jiaruiblog.quickboxserver.storage.model.StorageConfig;
import com.jiaruiblog.quickboxserver.storage.model.StorageType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final String REDIS_KEY_PREFIX = "upload:access:";
    private static final String REDIS_FOLDER_KEY_PREFIX = "folder:";
    private static final String REDIS_FILE_METADATA_PREFIX = "file:metadata:";

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Resource
    private FolderUploadService folderUploadService;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @Override
    public UploadSession initUploadSession(ChunkUploadRequest chunkUploadRequest) {
        // 1. Generate unique uploadId
        String accessCode = generateRandomCode(6);

        // 2. Use StorageService to initialize upload session first
        StorageService storageService = storageServiceFactory.getPrimaryStorageService();

        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("filename", chunkUploadRequest.filename());
        metadata.put("totalSize", chunkUploadRequest.totalSize());
        metadata.put("totalChunks", chunkUploadRequest.totalChunks());
        metadata.put("uploadTime", Instant.now().toString());

        // 关键：传入 accessCode 作为 fileId，StorageService 会使用它作为 sessionId
        storageService.initFileUpload(
                accessCode,
                chunkUploadRequest.filename(),
                chunkUploadRequest.totalSize(),
                metadata
        );

        // 3. Store access code and metadata in Redis with expiration
        // Store metadata for download (including filename and objectKey for S3)
        Map<String, Object> fileMetadata = new java.util.HashMap<>();
        fileMetadata.put("filename", chunkUploadRequest.filename());
        fileMetadata.put("fileSize", chunkUploadRequest.totalSize());
        fileMetadata.put("storageType", storageService.getStorageType().name());

        // For S3, construct and store the full objectKey
        if (storageService.getStorageType() == StorageType.S3) {
            StorageConfig config = storageService.getConfig();
            String prefix = "";
            if (config != null && config.getS3Config() != null) {
                prefix = config.getS3Config().getPrefix() != null ? config.getS3Config().getPrefix() : "";
            }
            String objectKey = prefix.isEmpty() ? "" : prefix + "/";
            objectKey += "files/" + accessCode + "/" + chunkUploadRequest.filename();
            fileMetadata.put("objectKey", objectKey);
        }

        try {
            String metadataJson = objectMapper.writeValueAsString(fileMetadata);
            redisTemplate.opsForValue().set(
                    REDIS_FILE_METADATA_PREFIX + accessCode,
                    metadataJson,
                    fileStorageConfig.getDownloadExpirationDays(),
                    TimeUnit.DAYS
            );
        } catch (Exception e) {
            log.error("Failed to store file metadata in Redis", e);
        }

        redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + accessCode,
                "active",
                fileStorageConfig.getDownloadExpirationDays(),
                TimeUnit.DAYS
        );

        // 4. Set expiration
        LocalDateTime expires = LocalDateTime.now().plusHours(fileStorageConfig.getSessionExpirationHours());

        // 获取存储路径（用于返回给前端）
        String chunkPath = fileStorageConfig.getChunkPathWithAccessCode(accessCode);
        return new UploadSession(
                accessCode,
                chunkPath,
                expires
        );
    }

    @Override
    public UploadProgress uploadChunk(ChunkUploadRequest request, MultipartFile file) {
        try {
            // 1. Validate chunk data
            if (request.chunkNumber() == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }

            // 2. Use StorageService to upload chunk
            StorageService storageService = storageServiceFactory.getPrimaryStorageService();

            try (InputStream chunkData = file.getInputStream()) {
                storageService.uploadFileChunk(
                        request.uploadId(),
                        request.chunkNumber(),
                        chunkData,
                        file.getSize()
                );
            }

            // 3. Get updated progress
            return getUploadProgress(request.uploadId(), request.chunkNumber());

        } catch (IOException e) {
            log.error("upload chunk file is error, error msg: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATE_FAILED);
        }
    }

    public UploadProgress getUploadProgress(String accessCode, Integer chunkNumber) {
        // 1. Get StorageService
        StorageService storageService = storageServiceFactory.getPrimaryStorageService();
        Map<String, Object> sessionInfo = storageService.getSessionInfo(accessCode);

        List<Integer> uploadedChunks;
        String chunkPath;

        if (sessionInfo != null) {
            // 从 StorageService 获取进度
            @SuppressWarnings("unchecked")
            Set<String> chunks = (Set<String>) sessionInfo.get("uploadedChunks");
            uploadedChunks = chunks.stream()
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());
            chunkPath = fileStorageConfig.getChunkPathWithAccessCode(accessCode);
        } else {
            // 兼容旧逻辑：尝试从本地目录读取
            chunkPath = fileStorageConfig.getChunkPathWithAccessCode(accessCode);
            File chunkDir = new File(chunkPath);
            File[] chunkFiles = chunkDir.listFiles();
            uploadedChunks = Arrays.stream(chunkFiles != null ? chunkFiles : new File[0])
                    .filter(f -> !f.getName().equals("metadata.json"))
                    .map(f -> {
                        try {
                            return Integer.parseInt(f.getName());
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    })
                    .filter(num -> num > 0)
                    .sorted()
                    .collect(Collectors.toList());
        }

        // 2. Build progress response
        return new UploadProgress(
                accessCode,
                uploadedChunks.size(),
                chunkNumber,
                uploadedChunks.size() == chunkNumber,
                uploadedChunks,
                chunkPath,
                Instant.now()
        );
    }

    @Override
    @Transactional
    public String mergeChunks(String identifier) {
        log.info("mergeChunks called with identifier: {}", identifier);

        // 使用 StorageService 合并分片
        StorageService storageService = storageServiceFactory.getPrimaryStorageService();

        // S3StorageService.mergeFileChunks() 会处理 S3 多部分上传
        // LocalFileStorageService.mergeFileChunks() 会处理本地文件合并
        String filePath = storageService.mergeFileChunks(identifier);

        log.info("mergeChunks completed for: {}", identifier);
        return identifier;
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredSessions(LocalDateTime before) {
        File chunksRoot = new File(fileStorageConfig.getFullChunksPath());
        File[] sessionDirs = chunksRoot.listFiles();

        if (sessionDirs == null) {
            return;
        }
        for (File sessionDir : sessionDirs) {
            if (sessionDir.lastModified() < before.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                try {
                    FileUtils.deleteDirectory(sessionDir);
                    // Optional: Also delete corresponding final file if exists
                    File finalFile = new File(fileStorageConfig.getFullFinalPath() + "/" + sessionDir.getName());
                    if (finalFile.exists()) {
                        FileUtils.deleteDirectory(finalFile);
                    }
                } catch (IOException e) {
                    log.error("remove {} is failed", sessionDir, e);
                }
            }
        }
    }

    @Override
    public File getFileByAccessCode(String accessCode) {
        // Check if it's a folder access code first
        String folderKey = REDIS_FOLDER_KEY_PREFIX + accessCode;
        String folderJson = (String) redisTemplate.opsForValue().get(folderKey);
        if (folderJson != null) {
            // It's a folder upload - get the actual folder path from folder info
            try {
                Map<?, ?> folderInfo = objectMapper.readValue(folderJson, Map.class);
                String folderPath = (String) folderInfo.get("folderPath");
                if (folderPath != null) {
                    // For S3 storage paths (e.g., "uploads/folders/xxx"), skip local file check
                    // since the folder exists in S3, not on local filesystem
                    if (folderPath.startsWith("uploads/")) {
                        // S3 path - trust the metadata exists if we successfully retrieved folderJson
                        // Return a File pointing to system temp dir (only used for isDirectory() check)
                        // The actual folder info comes from folderUploadService.getFolderInfo()
                        return new File(System.getProperty("java.io.tmpdir"));
                    }
                    File folder = new File(folderPath);
                    if (folder.exists() && folder.isDirectory()) {
                        return folder;
                    }
                }
            } catch (Exception e) {
                log.warn("解析文件夹信息失败: {}", accessCode, e);
            }
            throw new IllegalArgumentException("Folder not found or not ready");
        }

        // Check if access code exists in Redis
        if (Boolean.FALSE.equals(redisTemplate.hasKey(REDIS_KEY_PREFIX + accessCode))) {
            throw new IllegalArgumentException("Invalid or expired access code");
        }

        // 获取存储服务类型
        StorageService storageService = storageServiceFactory.getPrimaryStorageService();

        if (storageService.getStorageType() == StorageType.S3) {
            // S3 场景：返回 null，Controller 层会使用 S3StorageService.downloadFile()
            return null;
        } else {
            // Local 场景：保持原有逻辑
            File uploadDir = new File(fileStorageConfig.getFinalPathWithAccessCode(accessCode));
            if (!uploadDir.exists()) {
                throw new IllegalArgumentException("Invalid access code");
            }

            File[] files = uploadDir.listFiles();
            if (files == null || files.length == 0) {
                throw new IllegalArgumentException("No file found for this access code");
            }

            return files[0];
        }
    }

    @Override
    public FileInfo getFileInfo(String accessCode) {
        File file = getFileByAccessCode(accessCode);
        String downloadUrl;

        // Check if it's a folder access code first
        String folderKey = REDIS_FOLDER_KEY_PREFIX + accessCode;
        String folderJson = (String) redisTemplate.opsForValue().get(folderKey);

        if (folderJson != null || (file != null && file.isDirectory())) {
            // Folder case - get detailed folder info from FolderUploadService
            downloadUrl = "/api/upload/folder/download/" + accessCode;
            com.jiaruiblog.quickboxserver.model.folder.FolderInfoResponse folderInfo =
                    folderUploadService.getFolderInfo(accessCode);

            FileInfo result = new FileInfo();
            result.setType("folder");
            result.setFilename(folderInfo.getFolderName());
            result.setDownloadUrl(downloadUrl);
            result.setFolderName(folderInfo.getFolderName());
            result.setTotalFiles(folderInfo.getTotalFiles());
            result.setTotalSize(folderInfo.getTotalSize());
            result.setCreateTime(folderInfo.getCreateTime());
            result.setExpireTime(folderInfo.getExpireTime());
            result.setRemainingExpireSeconds(folderInfo.getRemainingExpireSeconds());
            result.setExpired(folderInfo.getExpired());
            result.setDownloaded(folderInfo.getDownloaded());

            // Map file list
            if (folderInfo.getFiles() != null) {
                List<FileInfo.FileDetail> fileDetails = folderInfo.getFiles().stream()
                        .map(fi -> {
                            FileInfo.FileDetail detail = new FileInfo.FileDetail();
                            detail.setFileName(fi.getFileName());
                            detail.setFileSize(fi.getFileSize());
                            detail.setRelativePath(fi.getRelativePath());
                            detail.setDownloadUrl(fi.getDownloadUrl());
                            return detail;
                        })
                        .collect(Collectors.toList());
                result.setFiles(fileDetails);
            }

            return result;
        } else if (file != null) {
            // Local file case
            downloadUrl = "/download/" + accessCode + "/" + file.getName();
            FileInfo result = new FileInfo();
            result.setType("file");
            result.setFilename(file.getName());
            result.setSize(file.length());
            result.setLastModified(file.lastModified());
            result.setDownloadUrl(downloadUrl);
            return result;
        } else {
            // S3 file case - get info from Redis metadata
            Map<String, Object> metadata = getFileMetadata(accessCode);

            if (metadata != null) {
                downloadUrl = "/download/" + accessCode;
                FileInfo result = new FileInfo();
                result.setType("file");
                result.setFilename(metadata.get("filename") != null ? metadata.get("filename").toString() : accessCode);
                result.setSize(metadata.get("fileSize") != null ? Long.parseLong(metadata.get("fileSize").toString()) : 0);
                result.setDownloadUrl(downloadUrl);
                return result;
            }

            throw new IllegalArgumentException("File not found for access code: " + accessCode);
        }
    }

    @Override
    public Map<String, Object> getFileMetadata(String accessCode) {
        String metadataKey = REDIS_FILE_METADATA_PREFIX + accessCode;
        String metadataJson = (String) redisTemplate.opsForValue().get(metadataKey);

        if (metadataJson != null) {
            try {
                return objectMapper.readValue(metadataJson, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse file metadata for {}", accessCode, e);
            }
        }
        return null;
    }

    public static String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    public void handleFileDownloaded(String accessCode) {
        try {
            // Delete the physical file
            File file = getFileByAccessCode(accessCode);
            Files.delete(file.toPath());

            // Delete the directory
            File dir = file.getParentFile();
            if (dir.exists()) {
                FileUtils.deleteDirectory(dir);
            }

            // Invalidate the access code in Redis immediately
            redisTemplate.delete(REDIS_KEY_PREFIX + accessCode);
        } catch (IOException e) {
            throw new RuntimeException("File cleanup failed: " + e.getMessage(), e);
        }
    }

    // 新增方法：确保存储目录存在
    @PostConstruct
    public void initStorageDirectories() {
        try {
            Path chunksPath = Paths.get(fileStorageConfig.getFullChunksPath());
            Path finalPath = Paths.get(fileStorageConfig.getFullFinalPath());

            Files.createDirectories(chunksPath);
            Files.createDirectories(finalPath);

            log.info("Storage directories initialized:");
            log.info("  Chunks path: {}", chunksPath.toAbsolutePath());
            log.info("  Final path: {}", finalPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
            throw new RuntimeException("Storage initialization failed", e);
        }
    }
}