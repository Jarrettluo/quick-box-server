package com.jiaruiblog.quickboxserver.service.impl;

import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.response.FileCheckResult;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import com.jiaruiblog.quickboxserver.service.FileUploadService;
import jakarta.annotation.Resource;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final String REDIS_KEY_PREFIX = "upload:access:";
    private static final long DOWNLOAD_EXPIRATION_DAYS = 7;

    @Resource
    RedisTemplate redisTemplate;

    /**
     * <p>        // 实现逻辑：
     * // 1. 检查文件是否已存在(通过MD5)
     * // 2. 检查是否有未完成的上传记录
     * // 3. 返回检查结果</p>
     *
     * @param fileMd5  文件md5
     * @param filename 文件名
     * @param fileSize 文件的大小
     * @return com.jiaruiblog.quickboxserver.model.response.FileCheckResult
     **/
    @Override
    public FileCheckResult prepareUpload(String fileMd5, String filename, long fileSize) {
        // 1. Generate random access code (6 chars)
        String accessCode = generateRandomCode(6);

        // 2. Check if file already exists by identifier (MD5)
        String targetPath = "/var/uploads/" + accessCode + "/" + filename;
        boolean exists = new File(targetPath).exists();


        // Store access code in Redis with 7-day expiration
        redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + accessCode,
                "active",
                DOWNLOAD_EXPIRATION_DAYS,
                TimeUnit.DAYS
        );

        // 3. Prepare chunk directory if needed
        String chunkPath = "/var/uploads/chunks/" + accessCode;
        new File(chunkPath).mkdirs();

        return new FileCheckResult(
                exists,
                exists ? "/download/" + accessCode : null,
                Collections.emptyList(),
                accessCode,  // Using accessCode as uploadId
                chunkPath
        );
    }

    /***
     * <p>        // 实现逻辑：
     *         // 1. 生成唯一uploadId
     *         // 2. 创建分片存储目录
     *         // 3. 保存上传记录到数据库
     *         // 4. 返回会话信息</p>
     * @param accessCode 唯一key
     * @param filename 文件名
     * @return com.jiaruiblog.quickboxserver.model.response.UploadSession
     **/
    @Override
    public UploadSession initUploadSession(String accessCode, String filename) {
        // . Create chunk directory
        String chunkPath = "/var/uploads/chunks/" + accessCode;
        new File(chunkPath).mkdirs();

        // 3. Set expiration (24 hours from now)
        LocalDateTime expires = LocalDateTime.now().plusHours(24);

        return new UploadSession(
                accessCode,
                chunkPath,
                expires
        );
    }

    /***
     * <p>        // 实现逻辑：
     *         // 1. 查询上传记录
     *         // 2. 获取已上传分片列表
     *         // 3. 构造进度响应</p>
     * @param accessCode 唯一ID
     * @param chunkNumber 序号
     * @return com.jiaruiblog.quickboxserver.model.response.UploadProgress
     **/
    @Override
    public UploadProgress getUploadProgress(String accessCode, Integer chunkNumber) {

        // 1. Get chunk directory
        String chunkPath = "/var/uploads/chunks/" + accessCode;
        File chunkDir = new File(chunkPath);

        // 2. List all uploaded chunks
        File[] chunkFiles = chunkDir.listFiles();
        List<Integer> uploadedChunks = Arrays.stream(chunkFiles != null ? chunkFiles : new File[0])
                .map(f -> Integer.parseInt(f.getName()))
                .sorted()
                .collect(Collectors.toList());

        // 3. Build progress response
        return new UploadProgress(
                accessCode,
                uploadedChunks.size(),
                null, // totalChunks unknown at this stage
                false,
                null,
                uploadedChunks,
                chunkPath,
                Instant.now()
        );
    }

    @Override
    public UploadProgress uploadChunk(ChunkUploadRequest request, MultipartFile file) {
        // 实现逻辑：
        // 1. 验证分片数据(MD5校验等)
        // 2. 保存分片到指定位置
        // 3. 更新上传进度
        // 4. 返回最新进度

        try {
            // 1. Validate chunk data
            if (request.chunkNumber() == null || file.isEmpty()) {
                throw new IllegalArgumentException("Invalid chunk data");
            }

            // 2. Save chunk to target location
            String chunkPath = "/var/uploads/chunks/" + request.identifier();
            File chunkFile = new File(chunkPath, request.chunkNumber().toString());

            file.transferTo(chunkFile);

            // 3. Get updated progress
            return getUploadProgress(request.identifier(), request.chunkNumber());

        } catch (IOException e) {
            throw new RuntimeException("Chunk upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public String mergeChunks(String identifier) {
        try {
            // 1. Prepare paths
            String chunkDirPath = "/var/uploads/chunks/" + identifier;
            String finalDirPath = "/var/uploads/" + identifier;
            new File(finalDirPath).mkdirs();

            // 2. Get all chunks
            File[] chunkFiles = new File(chunkDirPath).listFiles();
            if (chunkFiles == null || chunkFiles.length == 0) {
                throw new IllegalStateException("No chunks found");
            }

            // 3. Sort chunks numerically
            Arrays.sort(chunkFiles, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

            // 4. Merge files
            File outputFile = new File(finalDirPath, chunkFiles[0].getName() + ".merged");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                for (File chunk : chunkFiles) {
                    Files.copy(chunk.toPath(), fos);
                }
            }

            // 5. Cleanup chunks
            FileUtils.deleteDirectory(new File(chunkDirPath));

            return "/download/" + identifier + "/" + outputFile.getName();

        } catch (IOException e) {
            throw new RuntimeException("Merge failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    public void cleanupExpiredSessions(LocalDateTime before) {
        // 实现逻辑：
        // 1. 查询过期未完成的会话
        // 2. 删除相关分片文件
        // 3. 清理数据库记录
        File chunksRoot = new File("/var/uploads/chunks/");
        File[] sessionDirs = chunksRoot.listFiles();

        if (sessionDirs != null) {
            for (File sessionDir : sessionDirs) {
                // Check last modified time (simplified expiration check)
                if (sessionDir.lastModified() < before.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                    try {
                        FileUtils.deleteDirectory(sessionDir);
                        // Optional: Also delete corresponding final file if exists
                        File finalFile = new File("/var/uploads/" + sessionDir.getName());
                        if (finalFile.exists()) {
                            FileUtils.deleteDirectory(finalFile);
                        }
                    } catch (IOException e) {
                        // Log error but continue with other directories
                    }
                }
            }
        }
    }

    @Override
    public File getFileByAccessCode(String accessCode) {
        // Check if access code exists in Redis
        if (!redisTemplate.hasKey(REDIS_KEY_PREFIX + accessCode)) {
            throw new IllegalArgumentException("Invalid or expired access code");
        }

        File uploadDir = new File("/var/uploads/" + accessCode);
        if (!uploadDir.exists()) {
            throw new IllegalArgumentException("Invalid access code");
        }

        File[] files = uploadDir.listFiles();
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No file found for this access code");
        }

        return files[0]; // Assuming single file per access code
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

    // New method to handle file download and cleanup
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

}