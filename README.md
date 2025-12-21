# QuickBox（快取柜）后端

QuickBox（快取柜）后端是一个临时文件共享服务的服务器端实现，提供文件上传、下载和管理功能。文件在7天后自动删除，或在下载后立即删除。

## ✨ 功能特性
*   **分片上传**：支持大文件分片上传（适配vue-simple-uploader）
*   **文件合并**：自动合并分片文件为完整文件
*   **取件码管理**：生成6位唯一取件码，支持Redis缓存和过期管理
*   **文件下载**：通过取件码下载文件（单次使用）
*   **会话管理**：上传会话过期清理（24小时）
*   **进度跟踪**：实时上传进度查询
*   **元数据管理**：文件信息存储和查询

## 🛠️ 技术栈
*   **后端框架**：Spring Boot 3.x
*   **数据库**：Redis（会话和取件码管理）
*   **文件存储**：本地文件系统
*   **构建工具**：Maven
*   **开发语言**：Java 17+

## 📁 项目结构
```angular2html
src/main/java/com/jiaruiblog/quickboxserver/
├── controller/           # 控制器层
│   └── FileUploadController.java
├── service/             # 服务层
│   ├── FileUploadService.java
│   └── impl/FileUploadServiceImpl.java
├── model/               # 数据模型
│   ├── dto/             # 数据传输对象
│   ├── request/         # 请求对象
│   └── response/        # 响应对象
├── config/              # 配置类
│   └── FileStorageConfig.java
├── exception/           # 异常处理
│   ├── BusinessException.java
│   └── ErrorCode.java
└── common/              # 通用组件
    └── ApiResult.java
```

## 🚀 快速开始
### 环境要求
- JDK 17+
- Redis 6.0+
- Maven 3.6+
### 配置说明
#### 1. Redis配置 (`application.properties`)
# 编译项目
mvn clean package

# 运行项目
java -jar target/quick-box-server.jar

# 或使用Maven直接运行
mvn spring-boot:run

服务默认运行在 http://localhost:8081

📖 API文档
1. 初始化上传会话
```
   POST /api/upload/init
```
请求体:
```json
{
"filename": "example.zip",
"totalSize": 10485760,
"totalChunks": 5
}
```
响应
```json
{
"code": 200,
"message": "success",
"data": {
    "uploadId": "ABCDEF",
    "chunkPath": "C:/app/chunks/ABCDEF",
    "expires": "2024-01-01T12:00:00"
    }
}
```


2. 上传分片
```js
   POST /api/upload/upload
```
参数:
```json
uploadId: 上传会话ID
chunkNumber: 分片序号
totalChunks: 总分片数
filename: 文件名
totalSize: 文件总大小
file: 分片文件（MultipartFile）
```

响应:
```json
{
"code": 200,
"message": "success",
"data": {
    "uploadId": "ABCDEF",
    "uploadedChunks": 3,
    "currentChunk": 3,
    "completed": false,
    "uploadedChunksList": [1, 2, 3],
    "chunkPath": "C:/app/chunks/ABCDEF",
    "timestamp": "2024-01-01T10:30:00Z"
    }
}
```


3. 合并分片
```
      POST /api/upload/merge
```


参数:
```json
accessCode: 取件码
```
响应:
```json
{
"code": 200,
"message": "success",
"data": "ABCDEF"
}
```


4. 获取文件信息
```json
   GET /api/upload/info/{accessCode}
```

响应:
```json
{
"code": 200,
"message": "success",
"data": {
    "filename": "example.zip",
    "size": 10485760,
    "lastModified": 1704067200000,
    "downloadUrl": "/download/ABCDEF/example.zip"
    }
}
```
5. 下载文件
```json
   GET /api/upload/download/{accessCode}
```

响应:

- 文件流下载

- 自动设置Content-Disposition头

- 下载后文件自动删除


## 🔧 核心功能实现
### 分片上传流程
1. 初始化：生成唯一取件码，创建分片目录，存储元数据
2. 上传分片：保存分片文件到对应目录
3. 进度查询：实时获取已上传分片列表
4. 合并文件：按序号合并所有分片，验证文件完整性
5. 清理分片：合并成功后删除临时分片文件

### 取件码管理
- 使用Redis存储取件码状态，7天自动过期
- 6位大写字母随机生成（26^6 ≈ 3亿种组合）
- 下载后立即从Redis删除，防止重复使用
### 文件清理机制
- 定时清理：每天凌晨3点清理过期会话（24小时未完成上传）
- 下载清理：文件下载后立即删除物理文件
- 过期清理：Redis自动清理7天前的取件码
## ⚠️ 注意事项
### 安全性
- 取件码仅限单次使用，下载后立即失效
- 文件路径不暴露敏感信息
- 支持文件名URL编码，防止特殊字符问题
### 性能优化
- 分片上传支持大文件处理
- 文件合并使用流式操作，减少内存占用
- Redis缓存取件码状态，提高查询效率
### 存储管理
- 默认Windows路径：C:/app/chunks/ 和 C:/app/files/
- 可配置为Linux路径：/var/uploads/chunks/ 和 /var/uploads/files/
- 确保存储目录有足够的磁盘空间
## 🐛 故障排除
### 常见问题
#### 1. Redis连接失败

- 检查Redis服务是否运行
- 验证
- application.properties 中的配置
#### 2. 文件上传失败

- 检查存储目录权限
- 确保磁盘空间充足
- 查看日志文件中的错误信息
#### 3. 分片合并失败

- 验证所有分片已上传完成
- 检查分片命名是否正确（数字序号）
- 确认元数据文件完整
## 日志查看
```Bash
# 查看Spring Boot应用日志
tail -f logs/application.log

# 查看特定错误
grep "ERROR" logs/application.log

```

## 📄 许可证
MIT License - 详见 LICENSE 文件

## 🤝 贡献指南
欢迎提交Issue和Pull Request来改进项目。

Fork本仓库
1. 创建功能分支 (git checkout -b feature/AmazingFeature)
2. 提交更改 (git commit -m 'Add some AmazingFeature')
3. 推送到分支 (git push origin feature/AmazingFeature)
4. 开启Pull Request
## 📞 支持
如有问题或建议，请通过以下方式联系：

- 提交GitHub Issue
- 查看项目文档