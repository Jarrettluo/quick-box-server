package com.jiaruiblog.quickboxserver.storage.impl;

import com.jiaruiblog.quickboxserver.storage.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 本地文件系统存储服务实现
 */
@Slf4j
public class LocalFileStorageService extends AbstractStorageService {

    private final Path basePath;
    private final Path chunkPath;
    private final Path filePath;
    private final boolean useTempFiles;
    private final String tempFilePrefix;

    private final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

    public LocalFileStorageService(StorageConfig config) {
        super(config);

        StorageConfig.LocalConfig localConfig = config.getLocalConfig();
        if (localConfig == null) {
            throw new IllegalArgumentException("本地存储配置不能为空");
        }

        this.basePath = Paths.get(localConfig.getBasePath()).toAbsolutePath();
        this.chunkPath = StringUtils.hasText(localConfig.getChunkPath())
            ? Paths.get(localConfig.getChunkPath()).toAbsolutePath()
            : this.basePath.resolve("chunks");
        this.filePath = StringUtils.hasText(localConfig.getFilePath())
            ? Paths.get(localConfig.getFilePath()).toAbsolutePath()
            : this.basePath.resolve("files");
        this.useTempFiles = localConfig.isUseTempFiles();
        this.tempFilePrefix = localConfig.getTempFilePrefix();

        // 创建必要的目录
        createDirectoriesIfNeeded(localConfig.isCreateDirectories());

        log.info("初始化本地文件系统存储服务: {}", storageName);
        log.info("基础路径: {}", basePath);
        log.info("分片路径: {}", chunkPath);
        log.info("文件路径: {}", filePath);
    }

    private void createDirectoriesIfNeeded(boolean createDirectories) {
        if (!createDirectories) {
            return;
        }

        try {
            Files.createDirectories(basePath);
            Files.createDirectories(chunkPath);
            Files.createDirectories(filePath);
            log.info("创建存储目录成功");
        } catch (IOException e) {
            log.error("创建存储目录失败", e);
            throw new RuntimeException("无法创建存储目录", e);
        }
    }

    // ==================== 文件操作实现 ====================

    @Override
    public String initFileUpload(String fileId, String fileName, long fileSize, Map<String, Object> metadata) {
        validateFilePath(fileId);

        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = chunkPath.resolve(sessionId);

        try {
            Files.createDirectories(sessionDir);

            // 保存元数据
            Map<String, Object> sessionMetadata = new HashMap<>();
            sessionMetadata.put("fileId", fileId);
            sessionMetadata.put("fileName", fileName);
            sessionMetadata.put("fileSize", fileSize);
            sessionMetadata.put("createTime", LocalDateTime.now().toString());
            sessionMetadata.put("metadata", metadata);

            String metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sessionMetadata);
            Files.writeString(sessionDir.resolve("metadata.json"), metadataJson);

            // 创建上传会话
            UploadSession session = new UploadSession();
            session.setSessionId(sessionId);
            session.setFileId(fileId);
            session.setFileName(fileName);
            session.setFileSize(fileSize);
            session.setCreateTime(LocalDateTime.now());
            session.setMetadata(metadata);
            session.setChunkCount(0);
            session.setUploadedChunks(new HashSet<>());

            uploadSessions.put(sessionId, session);

            log.info("初始化文件上传会话: {} -> {}", sessionId, fileName);
            return sessionId;
        } catch (IOException e) {
            log.error("初始化文件上传失败", e);
            throw new RuntimeException("初始化文件上传失败", e);
        }
    }

    @Override
    public void uploadFileChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize) {
        validateUploadSession(sessionId);

        UploadSession session = uploadSessions.get(sessionId);
        Path chunkFile = chunkPath.resolve(sessionId).resolve(String.valueOf(chunkNumber));

        try {
            // 使用文件锁确保并发安全
            ReentrantLock lock = fileLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
            lock.lock();

            try {
                // 检查分片是否已上传
                if (session.getUploadedChunks().contains(chunkNumber)) {
                    log.debug("分片已上传: {} - {}", sessionId, chunkNumber);
                    return;
                }

                // 保存分片文件
                Files.copy(chunkData, chunkFile, StandardCopyOption.REPLACE_EXISTING);

                // 更新会话状态
                session.getUploadedChunks().add(chunkNumber);
                session.setChunkCount(session.getChunkCount() + 1);
                session.setUploadedSize(session.getUploadedSize() + chunkSize);
                session.setLastUpdateTime(LocalDateTime.now());

                log.debug("上传文件分片成功: {} - {} ({} bytes)", sessionId, chunkNumber, chunkSize);

                // 触发事件
                FileInfo fileInfo = createFileInfoFromSession(session);
                fireFileChunkUploaded(fileInfo, chunkNumber, chunkSize);
            } finally {
                lock.unlock();
            }
        } catch (IOException e) {
            log.error("上传文件分片失败", e);
            throw new RuntimeException("上传文件分片失败", e);
        }
    }

    @Override
    public String mergeFileChunks(String sessionId) {
        validateUploadSession(sessionId);

        UploadSession session = uploadSessions.get(sessionId);
        Path sessionDir = chunkPath.resolve(sessionId);
        Path finalFile = filePath.resolve(session.getFileId()).resolve(session.getFileName());

        try {
            // 创建最终文件目录
            Files.createDirectories(finalFile.getParent());

            // 获取所有分片文件并按序号排序
            List<Path> chunkFiles;
            try (Stream<Path> stream = Files.list(sessionDir)) {
                chunkFiles = stream
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return !fileName.equals("metadata.json") && fileName.matches("\\d+");
                    })
                    .sorted(Comparator.comparingInt(path -> Integer.parseInt(path.getFileName().toString())))
                    .collect(Collectors.toList());
            }

            // 验证分片完整性
            if (chunkFiles.size() != session.getUploadedChunks().size()) {
                throw new IllegalStateException("分片数量不匹配");
            }

            // 合并分片
            try (OutputStream outputStream = Files.newOutputStream(finalFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                for (Path chunkFile : chunkFiles) {
                    try (InputStream inputStream = Files.newInputStream(chunkFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }

            // 验证文件大小
            long actualSize = Files.size(finalFile);
            if (actualSize != session.getFileSize()) {
                throw new IllegalStateException(String.format("文件大小不匹配: 期望 %d, 实际 %d", session.getFileSize(), actualSize));
            }

            // 清理分片文件
            cleanupChunkFiles(sessionId);

            // 移除上传会话
            uploadSessions.remove(sessionId);
            fileLocks.remove(sessionId);

            String filePathStr = finalFile.toString();
            log.info("合并文件分片成功: {} -> {}", sessionId, filePathStr);

            // 触发事件
            FileInfo fileInfo = createFileInfoFromSession(session);
            fileInfo.setFilePath(filePathStr);
            fireFileUploadCompleted(fileInfo);

            return filePathStr;
        } catch (IOException e) {
            log.error("合并文件分片失败", e);
            throw new RuntimeException("合并文件分片失败", e);
        }
    }

    @Override
    public InputStream downloadFile(String filePath) {
        validateFilePath(filePath);
        Path path = Paths.get(filePath);

        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new FileNotFoundException("文件不存在: " + filePath);
            }

            FileInfo fileInfo = getFileInfo(filePath);
            fireFileDownloadStarted(fileInfo);

            return new FileInputStream(path.toFile()) {
                private long bytesRead = 0;

                @Override
                public int read() throws IOException {
                    int result = super.read();
                    if (result != -1) {
                        bytesRead++;
                    }
                    return result;
                }

                @Override
                public int read(byte[] b) throws IOException {
                    int result = super.read(b);
                    if (result != -1) {
                        bytesRead += result;
                    }
                    return result;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int result = super.read(b, off, len);
                    if (result != -1) {
                        bytesRead += result;
                    }
                    return result;
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    fireFileDownloadCompleted(fileInfo, bytesRead);
                }
            };
        } catch (IOException e) {
            log.error("下载文件失败", e);
            fireFileDownloadFailed(createBasicFileInfo(filePath), e.getMessage());
            throw new RuntimeException("下载文件失败", e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        validateFilePath(filePath);
        Path path = Paths.get(filePath);

        try {
            if (!Files.exists(path)) {
                log.warn("文件不存在，无需删除: {}", filePath);
                return;
            }

            FileInfo fileInfo = getFileInfo(filePath);
            Files.delete(path);

            // 尝试删除空目录
            deleteEmptyParentDirectories(path.getParent());

            log.info("删除文件成功: {}", filePath);
            fireFileDeleted(fileInfo);
        } catch (IOException e) {
            log.error("删除文件失败", e);
            throw new RuntimeException("删除文件失败", e);
        }
    }

    @Override
    public FileInfo getFileInfo(String filePath) {
        validateFilePath(filePath);
        Path path = Paths.get(filePath);

        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new FileNotFoundException("文件不存在: " + filePath);
            }

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            FileInfo fileInfo = new FileInfo();

            fileInfo.setFileName(path.getFileName().toString());
            fileInfo.setFilePath(filePath);
            fileInfo.setFileSize(Files.size(path));
            fileInfo.setMimeType(getMimeType(fileInfo.getFileName()));
            fileInfo.setCreateTime(LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()));
            fileInfo.setModifyTime(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
            fileInfo.setAccessTime(LocalDateTime.ofInstant(attrs.lastAccessTime().toInstant(), ZoneId.systemDefault()));

            // 尝试从元数据文件获取更多信息
            Path metadataFile = path.getParent().resolve("metadata.json");
            if (Files.exists(metadataFile)) {
                try {
                    String metadataJson = Files.readString(metadataFile);
                    Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, Map.class);

                    fileInfo.setFileId((String) metadata.get("fileId"));
                    fileInfo.setAccessCode((String) metadata.get("accessCode"));
                    fileInfo.setUploadSessionId((String) metadata.get("sessionId"));
                    fileInfo.setMetadataJson(metadataJson);

                    if (metadata.containsKey("expireTime")) {
                        fileInfo.setExpireTime(LocalDateTime.parse((String) metadata.get("expireTime")));
                    }
                    if (metadata.containsKey("downloaded")) {
                        fileInfo.setDownloaded((Boolean) metadata.get("downloaded"));
                    }
                    if (metadata.containsKey("downloadCount")) {
                        fileInfo.setDownloadCount((Integer) metadata.get("downloadCount"));
                    }
                } catch (Exception e) {
                    log.warn("读取元数据文件失败: {}", metadataFile, e);
                }
            }

            return fileInfo;
        } catch (IOException e) {
            log.error("获取文件信息失败", e);
            throw new RuntimeException("获取文件信息失败", e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        validateFilePath(filePath);
        Path path = Paths.get(filePath);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    // ==================== 文件夹操作实现 ====================

    @Override
    public String initFolderUpload(String folderId, String folderName, int totalFiles, long totalSize, String structureJson) {
        validateFolderPath(folderId);

        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = chunkPath.resolve(sessionId);

        try {
            Files.createDirectories(sessionDir);

            // 保存文件夹元数据
            Map<String, Object> sessionMetadata = new HashMap<>();
            sessionMetadata.put("folderId", folderId);
            sessionMetadata.put("folderName", folderName);
            sessionMetadata.put("totalFiles", totalFiles);
            sessionMetadata.put("totalSize", totalSize);
            sessionMetadata.put("structureJson", structureJson);
            sessionMetadata.put("createTime", LocalDateTime.now().toString());
            sessionMetadata.put("type", "folder");

            String metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sessionMetadata);
            Files.writeString(sessionDir.resolve("metadata.json"), metadataJson);

            log.info("初始化文件夹上传会话: {} -> {} ({} files, {} bytes)",
                sessionId, folderName, totalFiles, totalSize);
            return sessionId;
        } catch (IOException e) {
            log.error("初始化文件夹上传失败", e);
            throw new RuntimeException("初始化文件夹上传失败", e);
        }
    }

    @Override
    public void uploadFolderChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize) {
        // 文件夹上传使用ZIP格式，处理方式与文件相同
        uploadFileChunk(sessionId, chunkNumber, chunkData, chunkSize);
    }

    @Override
    public String mergeFolderChunks(String sessionId) {
        Path sessionDir = chunkPath.resolve(sessionId);
        Path metadataFile = sessionDir.resolve("metadata.json");

        try {
            // 读取文件夹元数据
            String metadataJson = Files.readString(metadataFile);
            Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, Map.class);

            String folderId = (String) metadata.get("folderId");
            String folderName = (String) metadata.get("folderName");
            Path finalFolder = filePath.resolve(folderId).resolve(folderName);

            // 创建目标文件夹
            Files.createDirectories(finalFolder);

            // 合并ZIP文件
            Path zipFile = sessionDir.resolve("folder.zip");
            try (OutputStream outputStream = Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                // 获取所有分片文件并按序号排序
                List<Path> chunkFiles;
                try (Stream<Path> stream = Files.list(sessionDir)) {
                    chunkFiles = stream
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return !fileName.equals("metadata.json") && !fileName.equals("folder.zip") && fileName.matches("\\d+");
                        })
                        .sorted(Comparator.comparingInt(path -> Integer.parseInt(path.getFileName().toString())))
                        .collect(Collectors.toList());
                }

                // 合并分片
                for (Path chunkFile : chunkFiles) {
                    try (InputStream inputStream = Files.newInputStream(chunkFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }

            // 解压ZIP文件
            extractZipFile(zipFile, finalFolder);

            // 保存文件夹结构信息
            String structureJson = (String) metadata.get("structureJson");
            if (structureJson != null) {
                Files.writeString(finalFolder.resolve("structure.json"), structureJson);
            }

            // 保存完整元数据
            metadata.put("extractTime", LocalDateTime.now().toString());
            metadata.put("folderPath", finalFolder.toString());
            String updatedMetadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
            Files.writeString(finalFolder.resolve("metadata.json"), updatedMetadataJson);

            // 清理分片文件
            cleanupChunkFiles(sessionId);

            String folderPathStr = finalFolder.toString();
            log.info("合并文件夹分片成功: {} -> {} ({} files)", sessionId, folderPathStr, metadata.get("totalFiles"));

            return folderPathStr;
        } catch (IOException e) {
            log.error("合并文件夹分片失败", e);
            throw new RuntimeException("合并文件夹分片失败", e);
        }
    }

    @Override
    public InputStream downloadFolderAsZip(String folderPath) {
        validateFolderPath(folderPath);
        Path path = Paths.get(folderPath);

        try {
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                throw new FileNotFoundException("文件夹不存在: " + folderPath);
            }

            // 创建临时ZIP文件
            Path tempZip = Files.createTempFile("quickbox_folder_", ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                zipDirectory(path, path, zos);
            }

            // 返回ZIP文件流
            return new FileInputStream(tempZip.toFile()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    // 删除临时文件
                    Files.deleteIfExists(tempZip);
                }
            };
        } catch (IOException e) {
            log.error("下载文件夹为ZIP失败", e);
            throw new RuntimeException("下载文件夹为ZIP失败", e);
        }
    }

    @Override
    public FolderInfo getFolderInfo(String folderPath) {
        validateFolderPath(folderPath);
        Path path = Paths.get(folderPath);

        try {
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                throw new FileNotFoundException("文件夹不存在: " + folderPath);
            }

            FolderInfo folderInfo = new FolderInfo();
            folderInfo.setFolderName(path.getFileName().toString());
            folderInfo.setFolderPath(folderPath);

            // 读取元数据文件
            Path metadataFile = path.resolve("metadata.json");
            if (Files.exists(metadataFile)) {
                try {
                    String metadataJson = Files.readString(metadataFile);
                    Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, Map.class);

                    folderInfo.setFolderId((String) metadata.get("folderId"));
                    folderInfo.setTotalFiles((Integer) metadata.get("totalFiles"));
                    folderInfo.setTotalSize(((Number) metadata.get("totalSize")).longValue());
                    folderInfo.setStructureJson((String) metadata.get("structureJson"));
//                    folderInfo.setMetadataJson(metadataJson);

                    if (metadata.containsKey("accessCode")) {
                        folderInfo.setAccessCode((String) metadata.get("accessCode"));
                    }
                    if (metadata.containsKey("createTime")) {
                        folderInfo.setCreateTime(LocalDateTime.parse((String) metadata.get("createTime")));
                    }
                    if (metadata.containsKey("expireTime")) {
                        folderInfo.setExpireTime(LocalDateTime.parse((String) metadata.get("expireTime")));
                    }
                } catch (Exception e) {
                    log.warn("读取文件夹元数据文件失败: {}", metadataFile, e);
                }
            }

            // 统计文件夹内容
            if (folderInfo.getTotalFiles() == null) {
                FolderStats stats = calculateFolderStats(path);
                folderInfo.setTotalFiles(stats.fileCount);
                folderInfo.setTotalFolders(stats.folderCount);
                folderInfo.setTotalSize(stats.totalSize);
            }

            // 获取修改时间
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            folderInfo.setModifyTime(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
            folderInfo.setAccessTime(LocalDateTime.ofInstant(attrs.lastAccessTime().toInstant(), ZoneId.systemDefault()));

            return folderInfo;
        } catch (IOException e) {
            log.error("获取文件夹信息失败", e);
            throw new RuntimeException("获取文件夹信息失败", e);
        }
    }

    @Override
    public boolean folderExists(String folderPath) {
        validateFolderPath(folderPath);
        Path path = Paths.get(folderPath);
        return Files.exists(path) && Files.isDirectory(path);
    }

    // ==================== 管理操作实现 ====================

    @Override
    public void cleanupExpiredSessions(LocalDateTime before) {
        log.info("开始清理过期会话: {}", before);

        int cleanedCount = 0;
        long freedSpace = 0;

        try {
            // 清理上传会话
            Iterator<Map.Entry<String, UploadSession>> iterator = uploadSessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, UploadSession> entry = iterator.next();
                UploadSession session = entry.getValue();

                if (session.getCreateTime().isBefore(before)) {
                    try {
                        freedSpace += cleanupChunkFiles(entry.getKey());
                        iterator.remove();
                        fileLocks.remove(entry.getKey());
                        cleanedCount++;
                        log.debug("清理过期上传会话: {}", entry.getKey());
                    } catch (IOException e) {
                        log.error("清理上传会话失败: {}", entry.getKey(), e);
                    }
                }
            }

            // 清理过期文件
            cleanedCount += cleanupExpiredFiles(before);

            log.info("清理完成: {} 个会话/文件，释放 {} 字节", cleanedCount, freedSpace);
        } catch (Exception e) {
            log.error("清理过期会话失败", e);
        }
    }

    @Override
    protected void updateHealthStatus() {
        try {
            healthStatus.setLastCheckTime(LocalDateTime.now());

            // 检查目录是否存在且可写
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                healthStatus.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
                healthStatus.setErrorMessage("基础目录不存在或不是目录");
                return;
            }

            if (!Files.isWritable(basePath)) {
                healthStatus.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
                healthStatus.setErrorMessage("基础目录不可写");
                return;
            }

            // 检查磁盘空间
            FileStore fileStore = Files.getFileStore(basePath);
            long totalSpace = fileStore.getTotalSpace();
            long usableSpace = fileStore.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            healthStatus.setTotalSpace(totalSpace);
            healthStatus.setAvailableSpace(usableSpace);
            healthStatus.setUsedSpace(usedSpace);

            // 根据可用空间百分比设置状态
            double availablePercentage = (usableSpace * 100.0) / totalSpace;
            if (availablePercentage < 5.0) {
                healthStatus.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
                healthStatus.setErrorMessage("磁盘空间严重不足: " + String.format("%.1f%%", availablePercentage));
            } else if (availablePercentage < 10.0) {
                healthStatus.setStatus(StorageHealth.HealthStatus.DEGRADED);
                healthStatus.setErrorMessage("磁盘空间不足: " + String.format("%.1f%%", availablePercentage));
            } else {
                healthStatus.setStatus(StorageHealth.HealthStatus.HEALTHY);
                healthStatus.setErrorMessage(null);
            }

            healthStatus.setSuccessCount(healthStatus.getSuccessCount() + 1);
        } catch (IOException e) {
            healthStatus.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
            healthStatus.setErrorMessage("健康检查失败: " + e.getMessage());
            healthStatus.setFailureCount(healthStatus.getFailureCount() + 1);
            log.error("更新健康状态失败", e);
        }
    }

    @Override
    protected void updateStorageUsage() {
        try {
            // 统计文件目录
            FolderStats fileStats = calculateFolderStats(filePath);

            // 统计分片目录
            FolderStats chunkStats = calculateFolderStats(chunkPath);

            usage.setTotalSpace(Files.getFileStore(basePath).getTotalSpace());
            usage.setUsedSpace(fileStats.totalSize + chunkStats.totalSize);
            usage.setAvailableSpace(usage.getTotalSpace() - usage.getUsedSpace());
//            usage.setFileCount(fileStats.fileCount);
//            usage.setFolderCount(fileStats.folderCount);
            usage.setSessionCount((long) uploadSessions.size());

            // 计算最大/最小文件大小
            usage.setMaxFileSize(findMaxFileSize(filePath));
            usage.setMinFileSize(findMinFileSize(filePath));

            usage.calculatePercentages();

            log.debug("更新存储使用情况: {} files, {} folders, {} bytes used",
                fileStats.fileCount, fileStats.folderCount, usage.getUsedSpace());
        } catch (IOException e) {
            log.error("更新存储使用情况失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private void validateUploadSession(String sessionId) {
        if (!uploadSessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("上传会话不存在: " + sessionId);
        }
    }

    private FileInfo createFileInfoFromSession(UploadSession session) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(session.getFileId());
        fileInfo.setFileName(session.getFileName());
        fileInfo.setFileSize(session.getFileSize());
        fileInfo.setUploadSessionId(session.getSessionId());
        fileInfo.setCreateTime(session.getCreateTime());
        return fileInfo;
    }

    private FileInfo createBasicFileInfo(String filePath) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFilePath(filePath);
        fileInfo.setFileName(Paths.get(filePath).getFileName().toString());
        return fileInfo;
    }

    private long cleanupChunkFiles(String sessionId) throws IOException {
        Path sessionDir = chunkPath.resolve(sessionId);
        if (!Files.exists(sessionDir)) {
            return 0;
        }

        long freedSpace = 0;
        try (Stream<Path> stream = Files.walk(sessionDir)) {
            freedSpace = stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        long size = Files.size(path);
                        Files.delete(path);
                        return size;
                    } catch (IOException e) {
                        log.warn("删除分片文件失败: {}", path, e);
                        return 0;
                    }
                })
                .sum();
        }

        // 删除空目录
        try {
            Files.deleteIfExists(sessionDir);
        } catch (IOException e) {
            log.warn("删除会话目录失败: {}", sessionDir, e);
        }

        return freedSpace;
    }

    private int cleanupExpiredFiles(LocalDateTime before) throws IOException {
        int cleanedCount = 0;

        if (!Files.exists(filePath)) {
            return 0;
        }

        try (Stream<Path> stream = Files.list(filePath)) {
            List<Path> fileDirs = stream
                .filter(Files::isDirectory)
                .collect(Collectors.toList());

            for (Path fileDir : fileDirs) {
                Path metadataFile = fileDir.resolve("metadata.json");
                if (Files.exists(metadataFile)) {
                    try {
                        String metadataJson = Files.readString(metadataFile);
                        Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(metadataJson, Map.class);

                        if (metadata.containsKey("expireTime")) {
                            LocalDateTime expireTime = LocalDateTime.parse((String) metadata.get("expireTime"));
                            if (expireTime.isBefore(before)) {
                                // 删除过期文件
                                deleteDirectoryRecursively(fileDir);
                                cleanedCount++;
                                log.debug("清理过期文件: {}", fileDir);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("读取文件元数据失败: {}", metadataFile, e);
                    }
                }
            }
        }

        return cleanedCount;
    }

    private void deleteEmptyParentDirectories(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            if (!stream.findAny().isPresent()) {
                // 目录为空，删除它
                Files.delete(directory);
                // 递归向上删除空目录
                deleteEmptyParentDirectories(directory.getParent());
            }
        }
    }

    private void extractZipFile(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private void zipDirectory(Path sourceDir, Path currentDir, ZipOutputStream zos) throws IOException {
        try (Stream<Path> stream = Files.list(currentDir)) {
            for (Path path : stream.collect(Collectors.toList())) {
                if (Files.isDirectory(path)) {
                    // 添加目录条目
                    String entryName = sourceDir.relativize(path).toString().replace('\\', '/') + "/";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.closeEntry();

                    // 递归处理子目录
                    zipDirectory(sourceDir, path, zos);
                } else {
                    // 添加文件条目
                    String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private FolderStats calculateFolderStats(Path directory) throws IOException {
        FolderStats stats = new FolderStats();

        if (!Files.exists(directory)) {
            return stats;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file)) {
                    stats.fileCount++;
                    try {
                        stats.totalSize += Files.size(file);
                    } catch (IOException e) {
                        log.warn("获取文件大小失败: {}", file, e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(directory)) {
                    stats.folderCount++;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return stats;
    }

    private Long findMaxFileSize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0L;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .max()
                .orElse(0L);
        }
    }

    private Long findMinFileSize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return Long.MAX_VALUE;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return Long.MAX_VALUE;
                    }
                })
                .min()
                .orElse(Long.MAX_VALUE);
        }
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ==================== 内部类 ====================

    private static class UploadSession {
        private String sessionId;
        private String fileId;
        private String fileName;
        private long fileSize;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdateTime;
        private Map<String, Object> metadata;
        private int chunkCount;
        private long uploadedSize;
        private Set<Integer> uploadedChunks;

        // getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

        public long getUploadedSize() { return uploadedSize; }
        public void setUploadedSize(long uploadedSize) { this.uploadedSize = uploadedSize; }

        public Set<Integer> getUploadedChunks() { return uploadedChunks; }
        public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    }

    private static class FolderStats {
        int fileCount = 0;
        int folderCount = 0;
        long totalSize = 0;
    }
}