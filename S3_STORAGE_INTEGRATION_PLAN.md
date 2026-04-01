# S3 存储集成重构方案

## 问题描述

### 当前症状
- `application.yml` 配置 `storage.type: s3`
- 但文件分片上传后仍然存储在本地 `./uploads` 目录

### 日志证据
```
YKBDPD=================
C:\Project\java\quick-box-server\.\uploads\chunks\YKBDPD
C:\Project\java\quick-box-server\.\uploads\files\YKBDPD
true结果
C:\Project\java\quick-box-server\.\uploads\chunks\YKBDPD\metadata.json
```

## 根本原因

### 配置冲突
项目存在两套独立的配置体系：

| 配置类 | 前缀 | 用途 | 使用者 |
|--------|------|------|--------|
| `StorageProperties` | `storage` | S3/Local 存储类型选择 | `StorageServiceFactory` |
| `FileStorageConfig` | `file.storage` | 本地文件路径配置 | `FileUploadServiceImpl` |

### 调用链路分析

**期望的调用链路**：
```
FileUploadController
  → FileUploadServiceImpl
    → StorageServiceFactory.getPrimaryStorageService()
      → S3StorageService (当 storage.type=s3)
        → MinIO/S3 API
```

**实际的调用链路**：
```
FileUploadController
  → FileUploadServiceImpl
    → FileStorageConfig (直接读取 file.storage 配置)
      → java.io.File (本地文件系统操作)
```

### 关键代码位置

`FileUploadServiceImpl.java` 第 52-53 行：
```java
@Resource
private FileStorageConfig fileStorageConfig;  // ← 错误：应该用 StorageService
```

`StorageServiceFactory.java` 第 43 行：
```java
log.info("初始化存储服务工厂，存储类型: {}", storageProperties.getType());
// 正确创建了 S3StorageService，但从未被 FileUploadServiceImpl 调用
```

## 重构方案

### 目标
让 `FileUploadServiceImpl` 通过 `StorageServiceFactory` 获取存储服务，实现 S3/Local 存储类型透明切换。

### 修改内容

#### 1. 修改 FileUploadServiceImpl.java

**注入方式变更**：
```java
// 原代码
@Resource
private FileStorageConfig fileStorageConfig;

// 修改为
@Resource
private StorageServiceFactory storageServiceFactory;
```

**initUploadSession() 方法**：
```java
// 原逻辑：使用 FileStorageConfig 创建本地目录，保存 metadata.json
// 新逻辑：
@Override
public UploadSession initUploadSession(ChunkUploadRequest chunkUploadRequest) {
    String accessCode = generateRandomCode(6);

    // Redis 存储取件码
    redisTemplate.opsForValue().set(
        REDIS_KEY_PREFIX + accessCode,
        "active",
        fileStorageConfig.getDownloadExpirationDays(),
        TimeUnit.DAYS
    );

    // 使用 StorageService 初始化上传会话
    StorageService storageService = storageServiceFactory.getPrimaryStorageService();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("filename", chunkUploadRequest.filename());
    metadata.put("totalSize", chunkUploadRequest.totalSize());
    metadata.put("totalChunks", chunkUploadRequest.totalChunks());
    metadata.put("uploadTime", Instant.now().toString());

    // 关键：initFileUpload 返回的 sessionId 即为 accessCode
    String sessionId = storageService.initFileUpload(
        accessCode,
        chunkUploadRequest.filename(),
        chunkUploadRequest.totalSize(),
        metadata
    );

    // 返回会话信息（路径需要从 storageService 获取）
    LocalDateTime expires = LocalDateTime.now().plusHours(fileStorageConfig.getSessionExpirationHours());
    return new UploadSession(accessCode, "", expires);
}
```

**uploadChunk() 方法**：
```java
// 原逻辑：使用 file.transferTo(new File(chunkPath, chunkNumber))
// 新逻辑：
@Override
public UploadProgress uploadChunk(ChunkUploadRequest request, MultipartFile file) {
    if (request.chunkNumber() == null || file.isEmpty()) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }

    StorageService storageService = storageServiceFactory.getPrimaryStorageService();

    // 将 MultipartFile 转为 InputStream
    try (InputStream chunkData = file.getInputStream()) {
        storageService.uploadFileChunk(
            request.uploadId(),           // sessionId = accessCode
            request.chunkNumber(),
            chunkData,
            file.getSize()
        );
    } catch (IOException e) {
        throw new BusinessException(ErrorCode.OPERATE_FAILED);
    }

    return getUploadProgress(request.uploadId(), request.chunkNumber());
}
```

**mergeChunks() 方法**：
```java
// 原逻辑：读取本地 chunks 目录，按序号合并文件到 files 目录
// 新逻辑：
@Override
@Transactional
public String mergeChunks(String identifier) {
    StorageService storageService = storageServiceFactory.getPrimaryStorageService();

    // 调用存储服务的合并方法
    // S3StorageService.mergeFileChunks() 会：
    // 1. 列出 S3 上所有分片
    // 2. 创建 Multipart Upload
    // 3. 复制各分片到目标文件
    // 4. 完成合并并清理分片
    String objectKey = storageService.mergeFileChunks(identifier);

    return identifier;
}
```

**getUploadProgress() 方法**：
需要适配 `StorageService` 接口。`LocalFileStorageService` 和 `S3StorageService` 都维护了 `uploadSessions`，可以通过会话 ID 查询已上传的分片。

**getFileByAccessCode() 方法**：
```java
// 原逻辑：File file = new File(fileStorageConfig.getFinalPathWithAccessCode(accessCode));
// 新逻辑：需要从存储服务获取文件信息
@Override
public File getFileByAccessCode(String accessCode) {
    // 先检查 Redis 中的取件码

    // 获取存储服务
    StorageService storageService = storageServiceFactory.getPrimaryStorageService();
    StorageType type = storageService.getStorageType();

    if (type == StorageType.S3) {
        // S3 场景：返回 null 或包装 S3 对象引用
        // 需要修改接口返回类型或使用新的 DTO
        return null;
    } else {
        // Local 场景：保持原有逻辑
        File uploadDir = new File(fileStorageConfig.getFinalPathWithAccessCode(accessCode));
        File[] files = uploadDir.listFiles();
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No file found for this access code");
        }
        return files[0];
    }
}
```

#### 2. 修改 FileUploadController.java

确保控制器正确传递 accessCode 作为 sessionId。

#### 3. 可能需要调整的接口

`StorageService` 的接口设计需要与 `FileUploadServiceImpl` 的使用方式兼容：

```java
// StorageService 接口现有方法
String initFileUpload(String fileId, String fileName, long fileSize, Map<String, Object> metadata);
void uploadFileChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize);
String mergeFileChunks(String sessionId);
```

现有接口设计是合理的，需要确认：
- `fileId` 可以直接使用 `accessCode`
- `sessionId` 在单文件上传场景下也是 `accessCode`

### 注意事项

1. **向后兼容**：本地存储模式（`storage.type: local`）也需要通过 `StorageService` 访问，保持行为一致。

2. **S3 元数据管理**：`S3StorageService` 将 metadata 保存为 `{objectKey}.metadata.json`，与本地存储的 `metadata.json` 位置不同，需确认前端是否依赖此路径。

3. **文件下载路径**：`getFileByAccessCode()` 在 S3 场景下不能返回 `java.io.File`，需要考虑：
   - 修改返回类型为 `StorageItem` 或类似的通用类型
   - 或者在 Controller 层区分处理

4. **测试验证**：重构后需要验证：
   - `storage.type=local` 正常工作
   - `storage.type=s3` 文件上传到 MinIO/S3
   - 分片上传进度查询正确
   - 文件合并成功
   - 文件下载正常

## 实施步骤

1. 备份现有代码
2. 修改 `FileUploadServiceImpl` 注入 `StorageServiceFactory`
3. 逐个方法重构（init → upload → progress → merge → download）
4. 测试 local 模式正常工作
5. 测试 s3 模式正常工作
6. 清理废弃的 `FileStorageConfig` 依赖（如果不再需要）

## 相关文件清单

| 文件 | 修改类型 |
|------|----------|
| `src/main/java/.../service/impl/FileUploadServiceImpl.java` | 重构核心 |
| `src/main/java/.../controller/FileUploadController.java` | 可能需要调整 |
| `src/main/java/.../storage/StorageService.java` | 接口确认 |
| `src/main/java/.../storage/impl/S3StorageService.java` | 确认接口兼容 |
| `src/main/java/.../storage/impl/LocalFileStorageService.java` | 确认接口兼容 |

---

**文档创建时间**：2026-03-31
**优先级**：高（配置与实际行为不一致是严重问题）
**预计工作量**：中（需要仔细测试两种存储模式）
