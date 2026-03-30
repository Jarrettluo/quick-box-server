package com.jiaruiblog.quickboxserver.storage.impl;

import com.jiaruiblog.quickboxserver.storage.model.*;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * S3/MinIO对象存储服务实现
 */
@Slf4j
public class S3StorageService extends AbstractStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final String prefix;
    private final boolean pathStyleAccess;
    private final int partSize;

    private final Map<String, S3UploadSession> uploadSessions = new ConcurrentHashMap<>();

    public S3StorageService(StorageConfig config) {
        super(config);

        StorageConfig.S3Config s3Config = config.getS3Config();
        if (s3Config == null) {
            throw new IllegalArgumentException("S3配置不能为空");
        }

        this.bucketName = s3Config.getBucketName();
        this.prefix = s3Config.getPrefix() != null ? s3Config.getPrefix() : "";
        this.pathStyleAccess = s3Config.isPathStyleAccess();
        this.partSize = s3Config.getPartSize();

        // 构建S3客户端
        this.s3Client = buildS3Client(s3Config);
        this.s3Presigner = buildS3Presigner(s3Config);

        log.info("初始化S3存储服务: {} -> {}/{}", storageName, s3Config.getEndpoint(), bucketName);
    }

    private S3Client buildS3Client(StorageConfig.S3Config s3Config) {
        try {
            software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder();

            // 设置凭证
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Config.getAccessKey(),
                s3Config.getSecretKey()
            );

            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));

            // 设置端点（用于MinIO或自定义S3兼容服务）
            if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
                builder.endpointOverride(new URL(s3Config.getEndpoint()).toURI());
            }

            // 设置区域
            if (s3Config.getRegion() != null && !s3Config.getRegion().isEmpty()) {
                builder.region(Region.of(s3Config.getRegion()));
            }

            // 设置路径样式访问
            if (pathStyleAccess) {
                builder.serviceConfiguration(s -> s.pathStyleAccessEnabled(true));
            }

            return builder.build();
        } catch (Exception e) {
            log.error("构建S3客户端失败", e);
            throw new RuntimeException("构建S3客户端失败", e);
        }
    }

    private S3Presigner buildS3Presigner(StorageConfig.S3Config s3Config) {
        try {
            // 创建凭证提供者
            AwsCredentials credentials = AwsBasicCredentials.create(
                    s3Config.getAccessKey(),
                    s3Config.getSecretKey()
            );
            AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            // 构建 S3Presigner
            S3Presigner.Builder builder = S3Presigner.builder()
                    .credentialsProvider(credentialsProvider);

            // 设置端点（使用 URI 而不是 URL）
            if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
                String endpoint = s3Config.getEndpoint();
                // 确保端点有正确的协议
                if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                    endpoint = "https://" + endpoint;
                }
                builder.endpointOverride(new URI(endpoint));
            }

            // 设置区域
            if (s3Config.getRegion() != null && !s3Config.getRegion().isEmpty()) {
                builder.region(Region.of(s3Config.getRegion()));
            }

            return builder.build();

        } catch (URISyntaxException e) {
            log.error("S3端点URL格式错误: {}", s3Config.getEndpoint(), e);
            throw new RuntimeException("无效的S3端点配置", e);
        } catch (Exception e) {
            log.error("构建S3预签名客户端失败", e);
            throw new RuntimeException("构建S3预签名客户端失败", e);
        }
    }

    // ==================== 文件操作实现 ====================

    @Override
    public String initFileUpload(String fileId, String fileName, long fileSize, Map<String, Object> metadata) {
        validateFilePath(fileId);

        String sessionId = UUID.randomUUID().toString();
        String objectKey = buildObjectKey("files", fileId, fileName);

        S3UploadSession session = new S3UploadSession();
        session.setSessionId(sessionId);
        session.setFileId(fileId);
        session.setFileName(fileName);
        session.setFileSize(fileSize);
        session.setObjectKey(objectKey);
        session.setCreateTime(LocalDateTime.now());
        session.setMetadata(metadata);

        uploadSessions.put(sessionId, session);

        log.info("初始化S3文件上传: {} -> {}", sessionId, objectKey);
        return sessionId;
    }

    @Override
    public void uploadFileChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize) {
        S3UploadSession session = getUploadSession(sessionId);

        try {
            String chunkKey = buildChunkKey(session.getObjectKey(), chunkNumber);

            // 上传分片
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(chunkKey)
                .contentLength(chunkSize)
                .build();

            s3Client.putObject(request, RequestBody.fromInputStream(chunkData, chunkSize));

            // 更新会话状态
            session.getUploadedChunks().add(String.valueOf(chunkNumber));
            session.setUploadedSize(session.getUploadedSize() + chunkSize);
            session.setLastUpdateTime(LocalDateTime.now());

            log.debug("上传S3文件分片成功: {} - {} ({} bytes)", sessionId, chunkNumber, chunkSize);
        } catch (Exception e) {
            log.error("上传S3文件分片失败", e);
            throw new RuntimeException("上传S3文件分片失败", e);
        }
    }

    @Override
    public String mergeFileChunks(String sessionId) {
        S3UploadSession session = getUploadSession(sessionId);

        try {
            // 创建多部分上传
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(session.getObjectKey())
                .build();

            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            String uploadId = createResponse.uploadId();

            // 上传所有分片
            List<CompletedPart> completedParts = new ArrayList<>();
            for (String chunkNumber : session.getUploadedChunks()) {
                String chunkKey = buildChunkKey(session.getObjectKey(), Integer.parseInt(chunkNumber));

                // 复制分片到多部分上传
                UploadPartCopyRequest copyRequest = UploadPartCopyRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(chunkKey)
                    .destinationBucket(bucketName)
                    .destinationKey(session.getObjectKey())
                    .uploadId(uploadId)
                    .partNumber(Integer.parseInt(chunkNumber) + 1) // S3 part numbers start from 1
                    .build();

                UploadPartCopyResponse copyResponse = s3Client.uploadPartCopy(copyRequest);

                CompletedPart part = CompletedPart.builder()
                    .partNumber(Integer.parseInt(chunkNumber) + 1)
                    .eTag(copyResponse.copyPartResult().eTag())
                    .build();

                completedParts.add(part);

                // 删除临时分片
                deleteObject(chunkKey);
            }

            // 完成多部分上传
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(session.getObjectKey())
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

            s3Client.completeMultipartUpload(completeRequest);

            // 保存文件元数据
            saveFileMetadata(session.getObjectKey(), session);

            // 清理上传会话
            uploadSessions.remove(sessionId);

            String objectKey = session.getObjectKey();
            log.info("合并S3文件分片成功: {} -> {}", sessionId, objectKey);

            return objectKey;
        } catch (Exception e) {
            log.error("合并S3文件分片失败", e);
            throw new RuntimeException("合并S3文件分片失败", e);
        }
    }

    @Override
    public InputStream downloadFile(String filePath) {
        validateFilePath(filePath);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            return response;
        } catch (Exception e) {
            log.error("下载S3文件失败", e);
            throw new RuntimeException("下载S3文件失败", e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        validateFilePath(filePath);

        try {
            // 删除文件
            deleteObject(filePath);

            // 删除元数据文件
            String metadataKey = buildMetadataKey(filePath);
            deleteObject(metadataKey);

            log.info("删除S3文件成功: {}", filePath);
        } catch (Exception e) {
            log.error("删除S3文件失败", e);
            throw new RuntimeException("删除S3文件失败", e);
        }
    }

    @Override
    public FileInfo getFileInfo(String filePath) {
        validateFilePath(filePath);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

            HeadObjectResponse response = s3Client.headObject(request);

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(extractFileName(filePath));
            fileInfo.setFilePath(filePath);
            fileInfo.setFileSize(response.contentLength());
            fileInfo.setMimeType(response.contentType());
            fileInfo.setCreateTime(LocalDateTime.from(response.lastModified()));
            fileInfo.setModifyTime(LocalDateTime.from(response.lastModified()));

            // 尝试获取元数据
            String metadataKey = buildMetadataKey(filePath);
            try {
                Map<String, Object> metadata = loadMetadata(metadataKey);
                if (metadata != null) {
                    fileInfo.setFileId((String) metadata.get("fileId"));
                    fileInfo.setAccessCode((String) metadata.get("accessCode"));
                    fileInfo.setUploadSessionId((String) metadata.get("sessionId"));
                    fileInfo.setMetadataJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata));
                }
            } catch (Exception e) {
                log.warn("读取S3文件元数据失败: {}", metadataKey, e);
            }

            return fileInfo;
        } catch (NoSuchKeyException e) {
            log.error("S3文件不存在: {}", filePath);
            throw new RuntimeException("文件不存在: " + filePath);
        } catch (Exception e) {
            log.error("获取S3文件信息失败", e);
            throw new RuntimeException("获取文件信息失败", e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        validateFilePath(filePath);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("检查S3文件存在失败", e);
            throw new RuntimeException("检查文件存在失败", e);
        }
    }

    // ==================== 文件夹操作实现 ====================

    @Override
    public String initFolderUpload(String folderId, String folderName, int totalFiles, long totalSize, String structureJson) {
        validateFolderPath(folderId);

        String sessionId = UUID.randomUUID().toString();
        String folderKey = buildObjectKey("folders", folderId, folderName) + "/";

        S3UploadSession session = new S3UploadSession();
        session.setSessionId(sessionId);
        session.setFolderId(folderId);
        session.setFolderName(folderName);
        session.setTotalFiles(totalFiles);
        session.setTotalSize(totalSize);
        session.setStructureJson(structureJson);
        session.setObjectKey(folderKey);
        session.setCreateTime(LocalDateTime.now());
        session.setFolderUpload(true);

        uploadSessions.put(sessionId, session);

        log.info("初始化S3文件夹上传: {} -> {} ({} files)", sessionId, folderKey, totalFiles);
        return sessionId;
    }

    @Override
    public void uploadFolderChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize,
                                  String relativePath, String filename) {
        S3UploadSession session = getUploadSession(sessionId);

        try {
            // 编码文件名
            String encodedFileName = encodeChunkFileName(relativePath, filename, chunkNumber);
            String chunkKey = buildFolderChunkKey(session.getObjectKey(), encodedFileName);

            // 上传分片
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(chunkKey)
                .contentLength(chunkSize)
                .build();

            s3Client.putObject(request, RequestBody.fromInputStream(chunkData, chunkSize));

            // 更新会话状态
            session.getUploadedChunks().add(encodedFileName);
            session.setUploadedSize(session.getUploadedSize() + chunkSize);
            session.setLastUpdateTime(LocalDateTime.now());

            log.debug("上传S3文件夹分片成功: {} - {} ({} bytes)", sessionId, chunkNumber, chunkSize);
        } catch (Exception e) {
            log.error("上传S3文件夹分片失败", e);
            throw new RuntimeException("上传S3文件夹分片失败", e);
        }
    }

    /**
     * 编码：将 relativePath 和 filename 编码为 chunk 文件名
     * 使用Base64编码relativePath以避免下划线混淆问题
     * @param relativePath 相对路径 (可为 null 或空)
     * @param filename 文件名
     * @param chunkNumber 分片号
     * @return 编码后的文件名，如 "bXlXZm9sZGVyL3N1YmRpcg==__c.txt_1" 或 "_||c.txt_1" (空路径时)
     */
    public static String encodeChunkFileName(String relativePath, String filename, int chunkNumber) {
        String encodedPath;
        if (relativePath == null || relativePath.isEmpty()) {
            encodedPath = "_";
        } else {
            // 如果relativePath包含URL编码字符（如%2F），先解码
            String pathToEncode = relativePath;
            if (relativePath.contains("%")) {
                try {
                    pathToEncode = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    pathToEncode = relativePath;
                }
            }
            // 使用URL-safe Base64编码relativePath，避免下划线混淆
            encodedPath = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(pathToEncode.getBytes(StandardCharsets.UTF_8));
        }
        // 使用 "__" 作为编码路径和文件名的分隔符（避免Windows非法字符 | ），"_" 作为分片号分隔符
        return encodedPath + "__" + filename + "_" + chunkNumber;
    }

    /**
     * 解码：从 chunk 文件名还原 relativePath 和 filename
     * @param encodedFileName 编码后的文件名，如 "bXlXZm9sZGVyL3N1YmRpcg==||c.txt_1"
     * @return String[3] = [relativePath, filename, chunkNumberStr]
     */
    public static String[] decodeChunkFileName(String encodedFileName) {
        // 找到最后一个下划线（分片号分隔符）
        int lastUnderscore = encodedFileName.lastIndexOf('_');
        String pathAndFile = encodedFileName.substring(0, lastUnderscore);
        String chunkNumberStr = encodedFileName.substring(lastUnderscore + 1);

        // 找到双下划线（路径和文件名分隔符）
        int doubleUnderscore = pathAndFile.indexOf("__");
        if (doubleUnderscore == -1) {
            throw new IllegalArgumentException("Invalid chunk filename format: " + encodedFileName);
        }

        String encodedPath = pathAndFile.substring(0, doubleUnderscore);
        String filename = pathAndFile.substring(doubleUnderscore + 2);

        // 解码relativePath：空路径标记还原为空字符串，Base64编码的路径进行解码
        String relativePath;
        if ("_".equals(encodedPath)) {
            relativePath = "";
        } else {
            relativePath = new String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8);
        }

        return new String[]{relativePath, filename, chunkNumberStr};
    }

    private String buildFolderChunkKey(String objectKey, String encodedFileName) {
        // folders/{folderId}/{folderName}/chunks/{encodedFileName}
        return objectKey + "chunks/" + encodedFileName;
    }

    @Override
    public String mergeFolderChunks(String sessionId) {
        S3UploadSession session = getUploadSession(sessionId);
        if (!session.isFolderUpload()) {
            throw new IllegalArgumentException("不是文件夹上传会话");
        }

        try {
            String chunksPrefix = session.getObjectKey() + "chunks/";

            // 列出所有分片
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(chunksPrefix)
                .build();

            List<S3Object> chunkObjects = s3Client.listObjectsV2(listRequest).contents();

            // 按文件分组
            Map<String, List<S3Object>> fileChunksMap = new HashMap<>();
            for (S3Object obj : chunkObjects) {
                String key = obj.key();
                String encodedFileName = key.substring(chunksPrefix.length());
                // 去掉 chunkNumber 获取文件的唯一标识
                int lastUnderscore = encodedFileName.lastIndexOf('_');
                String fileKey = encodedFileName.substring(0, lastUnderscore);
                fileChunksMap.computeIfAbsent(fileKey, k -> new ArrayList<>()).add(obj);
            }

            // 合并每个文件
            int mergedFiles = 0;
            for (Map.Entry<String, List<S3Object>> entry : fileChunksMap.entrySet()) {
                String fileKey = entry.getKey();
                List<S3Object> chunks = entry.getValue();

                // 解码获取 relativePath 和 filename
                String[] decoded = decodeChunkFileName(fileKey + "_1");
                String relativePath = decoded[0];
                String filename = decoded[1];

                // 构建目标路径
                String targetKey = session.getObjectKey();
                if (relativePath != null && !relativePath.isEmpty()) {
                    targetKey = targetKey + relativePath.replace("/", "_") + "_";
                }
                targetKey = targetKey + filename;

                // 创建多部分上传
                CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(targetKey)
                    .build();
                CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
                String uploadId = createResponse.uploadId();

                // 上传所有分片
                List<CompletedPart> completedParts = new ArrayList<>();
                int partNumber = 1;
                for (S3Object chunk : chunks) {
                    String chunkKey = chunk.key();
                    int chunkNum = Integer.parseInt(chunkKey.substring(chunkKey.lastIndexOf('_') + 1));

                    UploadPartCopyRequest copyRequest = UploadPartCopyRequest.builder()
                        .sourceBucket(bucketName)
                        .sourceKey(chunkKey)
                        .destinationBucket(bucketName)
                        .destinationKey(targetKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber++)
                        .build();

                    UploadPartCopyResponse copyResponse = s3Client.uploadPartCopy(copyRequest);
                    completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber - 1)
                        .eTag(copyResponse.copyPartResult().eTag())
                        .build());
                }

                // 完成多部分上传
                CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(targetKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
                s3Client.completeMultipartUpload(completeRequest);

                // 删除临时分片
                for (S3Object chunk : chunks) {
                    deleteObject(chunk.key());
                }

                mergedFiles++;
            }

            // 删除 chunks 目录前缀本身
            deleteObject(chunksPrefix);

            // 保存文件夹元数据
            saveFolderMetadata(session.getObjectKey(), session);

            // 清理上传会话
            uploadSessions.remove(sessionId);

            String folderKey = session.getObjectKey();
            log.info("合并S3文件夹分片成功: {} -> {} ({} files)", sessionId, folderKey, mergedFiles);

            return folderKey;
        } catch (Exception e) {
            log.error("合并S3文件夹分片失败", e);
            throw new RuntimeException("合并S3文件夹分片失败", e);
        }
    }

    @Override
    public InputStream downloadFolderAsZip(String folderPath) {
        validateFolderPath(folderPath);

        try {
            // 列出文件夹中的所有对象
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPath)
                .build();

            List<S3Object> objects = s3Client.listObjectsV2(listRequest).contents();

            // 在内存中创建 ZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            for (S3Object obj : objects) {
                String key = obj.key();
                // 跳过文件夹本身的 key 和 metadata 文件
                if (key.equals(folderPath) || key.endsWith(".metadata.json") || key.endsWith("/")) {
                    continue;
                }

                // 计算相对路径作为 ZIP 条目名
                String keyAfterFolder = key.substring(folderPath.length());
                // key格式: chunks/{encodedFileName}，需要去掉chunks/前缀
                if (!keyAfterFolder.startsWith("chunks/")) {
                    continue;
                }
                String encodedFileName = keyAfterFolder.substring("chunks/".length());

                // 解码获取relativePath和filename（需要加后缀用于解码）
                String[] decoded = decodeChunkFileName(encodedFileName + "_1");
                String relativePath = decoded[0];
                String filename = decoded[1];

                // 构建ZIP条目名：relativePath/filename
                String entryName;
                if (relativePath == null || relativePath.isEmpty()) {
                    entryName = filename;
                } else {
                    entryName = relativePath + "/" + filename;
                }

                // 下载文件内容
                GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

                try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
                    byte[] fileContent = response.readAllBytes();

                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setSize(fileContent.length);
                    zos.putNextEntry(entry);
                    zos.write(fileContent);
                    zos.closeEntry();
                }
            }

            zos.finish();
            zos.flush();

            byte[] zipContent = baos.toByteArray();
            log.info("S3文件夹ZIP创建成功: {} ({} bytes)", folderPath, zipContent.length);

            return new ByteArrayInputStream(zipContent);
        } catch (Exception e) {
            log.error("下载S3文件夹为ZIP失败", e);
            throw new RuntimeException("下载文件夹为ZIP失败", e);
        }
    }

    @Override
    public FolderInfo getFolderInfo(String folderPath) {
        validateFolderPath(folderPath);

        try {
            // 检查文件夹是否存在（通过检查元数据文件）
            String metadataKey = buildMetadataKey(folderPath);
            if (!objectExists(metadataKey)) {
                throw new RuntimeException("文件夹不存在: " + folderPath);
            }

            // 加载文件夹元数据
            Map<String, Object> metadata = loadMetadata(metadataKey);
            if (metadata == null) {
                throw new RuntimeException("文件夹元数据不存在: " + folderPath);
            }

            FolderInfo folderInfo = new FolderInfo();
            folderInfo.setFolderId((String) metadata.get("folderId"));
            folderInfo.setFolderName((String) metadata.get("folderName"));
            folderInfo.setFolderPath(folderPath);
            folderInfo.setTotalFiles((Integer) metadata.get("totalFiles"));
            folderInfo.setTotalSize(((Number) metadata.get("totalSize")).longValue());
            folderInfo.setStructureJson((String) metadata.get("structureJson"));
            folderInfo.setCreateTime(LocalDateTime.parse((String) metadata.get("createTime")));

            // 统计文件夹内容
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPath)
                .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            int fileCount = 0;
            int folderCount = 0;
            long totalSize = 0;

            for (S3Object s3Object : listResponse.contents()) {
                if (!s3Object.key().equals(folderPath) && !s3Object.key().endsWith("/")) {
                    fileCount++;
                    totalSize += s3Object.size();
                } else if (s3Object.key().endsWith("/") && !s3Object.key().equals(folderPath)) {
                    folderCount++;
                }
            }

            folderInfo.setTotalFiles(fileCount);
            folderInfo.setTotalFolders(folderCount);
            folderInfo.setTotalSize(totalSize);

            return folderInfo;
        } catch (Exception e) {
            log.error("获取S3文件夹信息失败", e);
            throw new RuntimeException("获取文件夹信息失败", e);
        }
    }

    @Override
    public boolean folderExists(String folderPath) {
        validateFolderPath(folderPath);

        try {
            // 检查元数据文件是否存在
            String metadataKey = buildMetadataKey(folderPath);
            return objectExists(metadataKey);
        } catch (Exception e) {
            log.error("检查S3文件夹存在失败", e);
            throw new RuntimeException("检查文件夹存在失败", e);
        }
    }

    // ==================== 管理操作实现 ====================

    @Override
    public void cleanupExpiredSessions(LocalDateTime before) {
        log.info("清理S3过期会话: {}", before);

        int cleanedCount = 0;
        Iterator<Map.Entry<String, S3UploadSession>> iterator = uploadSessions.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, S3UploadSession> entry = iterator.next();
            S3UploadSession session = entry.getValue();

            if (session.getCreateTime().isBefore(before)) {
                try {
                    // 清理临时分片
                    for (String chunkIdentifier : session.getUploadedChunks()) {
                        String chunkKey;
                        if (session.isFolderUpload()) {
                            chunkKey = buildFolderChunkKey(session.getObjectKey(), chunkIdentifier);
                        } else {
                            chunkKey = buildChunkKey(session.getObjectKey(), Integer.parseInt(chunkIdentifier));
                        }
                        deleteObject(chunkKey);
                    }

                    iterator.remove();
                    cleanedCount++;
                    log.debug("清理过期S3上传会话: {}", entry.getKey());
                } catch (Exception e) {
                    log.error("清理S3上传会话失败: {}", entry.getKey(), e);
                }
            }
        }

        log.info("S3会话清理完成: {} 个会话", cleanedCount);
    }

    @Override
    protected void updateHealthStatus() {
        try {
            healthStatus.setLastCheckTime(LocalDateTime.now());

            // 检查S3服务可用性
            HeadBucketRequest request = HeadBucketRequest.builder()
                .bucket(bucketName)
                .build();

            s3Client.headBucket(request);

            // 获取存储桶信息
            GetBucketLocationResponse locationResponse = s3Client.getBucketLocation(
                GetBucketLocationRequest.builder().bucket(bucketName).build()
            );

            // 获取存储桶大小（近似值）
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucketName).build()
            );

            long totalObjects = listResponse.keyCount();
            long totalSize = listResponse.contents().stream()
                .mapToLong(S3Object::size)
                .sum();

            healthStatus.setStatus(StorageHealth.HealthStatus.HEALTHY);
            healthStatus.setErrorMessage(null);
            healthStatus.setSuccessCount(healthStatus.getSuccessCount() + 1);

            log.debug("S3存储服务健康检查通过: {}", storageName);
        } catch (Exception e) {
            healthStatus.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
            healthStatus.setErrorMessage("S3服务不可用: " + e.getMessage());
            healthStatus.setFailureCount(healthStatus.getFailureCount() + 1);
            log.error("S3存储服务健康检查失败", e);
        }
    }

    @Override
    protected void updateStorageUsage() {
        try {
            // 列出所有对象
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            long fileCount = 0;
            long folderCount = 0;
            long totalSize = 0;

            for (S3Object s3Object : response.contents()) {
                if (s3Object.key().endsWith("/")) {
                    folderCount++;
                } else {
                    fileCount++;
                    totalSize += s3Object.size();
                }
            }

            // S3不提供总空间信息，使用配置的最大值或默认值
            long totalSpace = config.getMaxFileSize() * 1000; // 假设最大文件大小的1000倍
            long usedSpace = totalSize;
            long availableSpace = totalSpace - usedSpace;

            usage.setTotalSpace(totalSpace);
            usage.setUsedSpace(usedSpace);
            usage.setAvailableSpace(availableSpace);
            usage.setFileCount(fileCount);
            usage.setFolderCount(folderCount);
            usage.setSessionCount((long) uploadSessions.size());

            usage.calculatePercentages();

            log.debug("更新S3存储使用情况: {} files, {} folders, {} bytes used",
                fileCount, folderCount, usedSpace);
        } catch (Exception e) {
            log.error("更新S3存储使用情况失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private S3UploadSession getUploadSession(String sessionId) {
        S3UploadSession session = uploadSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在: " + sessionId);
        }
        return session;
    }

    private String buildObjectKey(String type, String id, String name) {
        StringBuilder key = new StringBuilder();

        if (prefix != null && !prefix.isEmpty()) {
            key.append(prefix).append("/");
        }

        key.append(type).append("/")
           .append(id).append("/")
           .append(name);

        return key.toString();
    }

    private String buildChunkKey(String objectKey, int chunkNumber) {
        return objectKey + ".chunk." + chunkNumber;
    }

    private String buildMetadataKey(String objectKey) {
        return objectKey + ".metadata.json";
    }

    private String extractFileName(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < objectKey.length() - 1) {
            return objectKey.substring(lastSlash + 1);
        }
        return objectKey;
    }

    private void deleteObject(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.deleteObject(request);
        } catch (Exception e) {
            log.warn("删除S3对象失败: {}", key, e);
        }
    }

    private boolean objectExists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("检查S3对象存在失败: {}", key, e);
            return false;
        }
    }

    private void saveFileMetadata(String objectKey, S3UploadSession session) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileId", session.getFileId());
            metadata.put("fileName", session.getFileName());
            metadata.put("fileSize", session.getFileSize());
            metadata.put("createTime", session.getCreateTime().toString());
            metadata.put("sessionId", session.getSessionId());
            metadata.put("metadata", session.getMetadata());

            String metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
            String metadataKey = buildMetadataKey(objectKey);

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .contentType("application/json")
                .build();

            s3Client.putObject(request, RequestBody.fromString(metadataJson));
        } catch (Exception e) {
            log.error("保存S3文件元数据失败", e);
        }
    }

    private void saveFolderMetadata(String folderKey, S3UploadSession session) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("folderId", session.getFolderId());
            metadata.put("folderName", session.getFolderName());
            metadata.put("totalFiles", session.getTotalFiles());
            metadata.put("totalSize", session.getTotalSize());
            metadata.put("structureJson", session.getStructureJson());
            metadata.put("createTime", session.getCreateTime().toString());
            metadata.put("sessionId", session.getSessionId());
            metadata.put("type", "folder");

            String metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
            String metadataKey = buildMetadataKey(folderKey);

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .contentType("application/json")
                .build();

            s3Client.putObject(request, RequestBody.fromString(metadataJson));
        } catch (Exception e) {
            log.error("保存S3文件夹元数据失败", e);
        }
    }

    private Map<String, Object> loadMetadata(String metadataKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, Map.class);
        } catch (Exception e) {
            log.warn("加载S3元数据失败: {}", metadataKey, e);
            return null;
        }
    }

    private String mergeFileChunksInternal(String sessionId, String targetKey) {
        S3UploadSession session = getUploadSession(sessionId);

        // 创建多部分上传
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(targetKey)
            .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        String uploadId = createResponse.uploadId();

        // 上传所有分片
        List<CompletedPart> completedParts = new ArrayList<>();
        for (String chunkNumber : session.getUploadedChunks()) {
            String chunkKey = buildChunkKey(session.getObjectKey(), Integer.parseInt(chunkNumber));

            UploadPartCopyRequest copyRequest = UploadPartCopyRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(chunkKey)
                .destinationBucket(bucketName)
                .destinationKey(targetKey)
                .uploadId(uploadId)
                .partNumber(Integer.parseInt(chunkNumber) + 1)
                .build();

            UploadPartCopyResponse copyResponse = s3Client.uploadPartCopy(copyRequest);

            CompletedPart part = CompletedPart.builder()
                .partNumber(Integer.parseInt(chunkNumber) + 1)
                .eTag(copyResponse.copyPartResult().eTag())
                .build();

            completedParts.add(part);

            // 删除临时分片
            deleteObject(chunkKey);
        }

        // 完成多部分上传
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(targetKey)
            .uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build();

        s3Client.completeMultipartUpload(completeRequest);

        return targetKey;
    }

    private void extractZipToFolder(String zipKey, String folderKey) {
        // TODO: 实现S3上的ZIP解压
        // 这需要下载ZIP文件，解压，然后上传每个文件到S3
        // 由于S3不支持直接解压，这里需要实现流式解压和上传
        log.warn("S3 ZIP解压功能暂未实现: {} -> {}", zipKey, folderKey);
    }

    // ==================== 内部类 ====================

    @lombok.Data
    private static class S3UploadSession {
        private String sessionId;
        private String fileId;
        private String fileName;
        private String folderId;
        private String folderName;
        private long fileSize;
        private int totalFiles;
        private long totalSize;
        private String structureJson;
        private String objectKey;
        private LocalDateTime createTime;
        private LocalDateTime lastUpdateTime;
        private Map<String, Object> metadata;
        private Set<String> uploadedChunks = new TreeSet<>();
        private long uploadedSize = 0;
        private boolean folderUpload = false;
    }
}