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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final String REDIS_KEY_PREFIX = "upload:access:";

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private FileStorageConfig fileStorageConfig;

    @Override
    public UploadSession initUploadSession(ChunkUploadRequest chunkUploadRequest) {
        // 1. Generate unique uploadId
        String accessCode = generateRandomCode(6);

        // 2. Store access code in Redis with expiration
        redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + accessCode,
                "active",
                fileStorageConfig.getDownloadExpirationDays(),
                TimeUnit.DAYS
        );

        // 3. Create chunk directory
        String chunkPath = fileStorageConfig.getChunkPathWithAccessCode(accessCode);
        File chunkDir = new File(chunkPath);
        if (!chunkDir.exists()) {
            boolean mkdir = chunkDir.mkdirs();
            if (!mkdir) {
                log.error("Failed to create chunk directory: {}", chunkPath);
                throw new BusinessException(ErrorCode.OPERATE_FAILED);
            }
        }

        // 4. Persist file metadata to chunk directory
        String filename = chunkUploadRequest.filename();
        Long totalSize = chunkUploadRequest.totalSize();
        Integer totalChunks = chunkUploadRequest.totalChunks();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("filename", filename);
            metadata.put("totalSize", totalSize);
            metadata.put("totalChunks", totalChunks);
            metadata.put("uploadTime", Instant.now().toString());

            File metadataFile = new File(chunkPath, "metadata.json");
            objectMapper.writeValue(metadataFile, metadata);
        } catch (java.io.IOException e) {
            log.error("Failed to persist file metadata: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATE_FAILED);
        }

        // 5. Set expiration
        LocalDateTime expires = LocalDateTime.now().plusHours(fileStorageConfig.getSessionExpirationHours());

        // 返回绝对路径，确保前端和后端路径一致性
        String absoluteChunkPath = new File(chunkPath).getAbsolutePath();
        return new UploadSession(
                accessCode,
                absoluteChunkPath,
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

            // 2. Save chunk to target location
            String chunkPath = fileStorageConfig.getChunkPathWithAccessCode(request.uploadId());
            File chunkDir = new File(chunkPath);

            // 确保目录存在
            if (!chunkDir.exists()) {
                boolean mkdirs = chunkDir.mkdirs();
                if (!mkdirs) {
                    log.error("Failed to create chunk directory: {}", chunkPath);
                    throw new BusinessException(ErrorCode.OPERATE_FAILED);
                }
            }

            File chunkFile = new File(chunkDir, request.chunkNumber().toString());

            // 使用绝对路径确保文件保存到正确位置
            log.debug("Saving chunk file to absolute path: {}", chunkFile.getAbsolutePath());

            // 确保父目录存在
            if (!chunkFile.getParentFile().exists()) {
                chunkFile.getParentFile().mkdirs();
            }

            file.transferTo(chunkFile);

            // 3. Get updated progress
            return getUploadProgress(request.uploadId(), request.chunkNumber());

        } catch (IOException e) {
            log.error("upload chunk file is error, error msg: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATE_FAILED);
        }
    }

    public UploadProgress getUploadProgress(String accessCode, Integer chunkNumber) {
        // 1. Get chunk directory
        String chunkPath = fileStorageConfig.getChunkPathWithAccessCode(accessCode);
        File chunkDir = new File(chunkPath);

        // 2. List all uploaded chunks
        File[] chunkFiles = chunkDir.listFiles();
        List<Integer> uploadedChunks = Arrays.stream(chunkFiles != null ? chunkFiles : new File[0])
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

        // 3. Build progress response
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
        System.out.println(identifier + "=================");
        // 1. Prepare paths
        String chunkDirPath = fileStorageConfig.getChunkPathWithAccessCode(identifier);
        String finalDirPath = fileStorageConfig.getFinalPathWithAccessCode(identifier);
        System.out.println(chunkDirPath);
        System.out.println(finalDirPath);
        // Check if directory already exists
        File finalDir = new File(finalDirPath);
        if (!finalDir.exists()) {
            boolean mkdir = finalDir.mkdirs();
            System.out.println(mkdir + "结果" );
            if (!mkdir) {
                // Add more detailed error information
                throw new BusinessException(ErrorCode.OPERATE_FAILED,
                        "Failed to create directory: " + finalDirPath);
            }
        } else {
            System.out.println("Directory already exists: " + finalDirPath);
        }

        // 2. Read metadata
        String filename;
        long totalSize;
        int totalChunks;
        File metadataFile = new File(chunkDirPath, "metadata.json");
        try {
            System.out.println(metadataFile);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> metadata = objectMapper.readValue(metadataFile, Map.class);
            filename = metadata.get("filename");
            totalSize = Long.parseLong(String.valueOf(metadata.getOrDefault("totalSize", "0")));
            totalChunks = Integer.parseInt(String.valueOf(metadata.getOrDefault("totalChunks", "0")));
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 3. Get all chunks
        File[] chunkFiles = new File(chunkDirPath).listFiles((dir, name) -> !name.equals("metadata.json"));
        if (chunkFiles == null || chunkFiles.length == 0) {
            throw new IllegalStateException("No chunks found");
        }

        // 4. Validate chunk count
        if (chunkFiles.length != totalChunks) {
            throw new IllegalStateException("Chunk count mismatch. Expected: "
                    + totalChunks + ", Actual: " + chunkFiles.length);
        }

        // 5. Sort chunks numerically
        Arrays.sort(chunkFiles, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

        // 6. Merge files and calculate total size
        File outputFile = new File(finalDirPath, filename);
        long mergedSize = 0;
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (File chunk : chunkFiles) {
                long chunkSize = Files.copy(chunk.toPath(), fos);
                mergedSize += chunkSize;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATE_FAILED);
        }

        // 7. Validate total size
        if (mergedSize != totalSize) {
            boolean delete = outputFile.delete();
            if (!delete) {
                log.error("chunk files are remove failed!");
            }
            throw new IllegalStateException("File size mismatch. Expected: " + totalSize + ", Actual: " + mergedSize);
        }
        try {
            // 8. Cleanup chunks
            FileUtils.deleteDirectory(new File(chunkDirPath));
            return identifier;
        } catch (IOException e) {
            throw new RuntimeException("Merge failed: " + e.getMessage(), e);
        }
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
                    log.error("remove {} is failed", sessionDir, e.getCause());
                }
            }
        }
    }

    @Override
    public File getFileByAccessCode(String accessCode) {
        // Check if access code exists in Redis
        if (Boolean.FALSE.equals(redisTemplate.hasKey(REDIS_KEY_PREFIX + accessCode))) {
            throw new IllegalArgumentException("Invalid or expired access code");
        }

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

    @Override
    public FileInfo getFileInfo(String accessCode) {
        File file = getFileByAccessCode(accessCode);
        return new FileInfo(
                file.getName(),
                file.length(),
                file.lastModified(),
                "/download/" + accessCode + "/" + file.getName()
        );
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