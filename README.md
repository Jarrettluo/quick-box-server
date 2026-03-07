# QuickBox（快取柜）后端

QuickBox（快取柜）后端是一个临时文件共享服务的服务器端实现，提供文件上传、下载和管理功能。文件在7天后自动删除，或在下载后立即删除。

## ✨ 功能特性

- **分片上传**：支持大文件分片上传（适配vue-simple-uploader）
- **文件合并**：自动合并分片文件为完整文件
- **取件码管理**：生成6位唯一取件码，支持Redis缓存和过期管理
- **文件下载**：通过取件码下载文件（单次使用）
- **会话管理**：上传会话过期清理（24小时）
- **进度跟踪**：实时上传进度查询
- **元数据管理**：文件信息存储和查询
- **文件夹上传功能**：支持文件夹结构上传、ZIP压缩包处理、目录结构保持
- **多存储后端支持**：抽象存储层，支持本地文件系统、S3/MinIO对象存储
- **Docker容器化部署**：完整的容器化部署方案，支持生产环境
- **存储管理API**：存储后端管理和监控功能

## 🛠️ 技术栈

- **后端框架**：Spring Boot 3.x
- **数据库**：Redis（会话和取件码管理）
- **文件存储**：本地文件系统 / S3/MinIO对象存储
- **构建工具**：Maven
- **开发语言**：Java 17+
- **容器化**：Docker + Docker Compose

## 📁 项目结构

```angular2html
src/main/java/com/jiaruiblog/quickboxserver/
├── controller/           # 控制器层
│   ├── FileUploadController.java       # 文件上传控制器
│   ├── FolderUploadController.java     # 文件夹上传控制器
│   └── StorageAdminController.java     # 存储管理控制器
├── service/              # 服务层
│   ├── FileUploadService.java          # 文件上传服务接口
│   ├── FolderUploadService.java        # 文件夹上传服务接口
│   └── impl/
│       ├── FileUploadServiceImpl.java  # 文件上传服务实现
│       └── FolderUploadServiceImpl.java # 文件夹上传服务实现
├── storage/              # 存储抽象层
│   ├── StorageService.java             # 存储服务统一接口
│   ├── StorageServiceFactory.java      # 存储服务工厂
│   ├── ConfigurableStorageStrategy.java # 可配置存储策略
│   ├── impl/
│   │   ├── LocalFileStorageService.java # 本地文件系统存储
│   │   └── S3StorageService.java       # S3/MinIO对象存储
│   └── model/
│       ├── StorageType.java            # 存储类型枚举
│       ├── StorageConfig.java          # 存储配置
│       └── StorageHealth.java          # 存储健康状态
├── model/                # 数据模型
│   ├── dto/              # 数据传输对象
│   ├── request/          # 请求对象
│   └── response/         # 响应对象
├── config/               # 配置类
│   ├── FileStorageConfig.java          # 文件存储配置
│   ├── RedisConfig.java                # Redis配置
│   └── CorsConfig.java                 # CORS配置
├── exception/            # 异常处理
│   ├── BusinessException.java          # 业务异常
│   ├── ErrorCode.java                  # 错误码枚举
│   └── GlobalExceptionHandler.java     # 全局异常处理器
└── common/               # 通用组件
    └── ApiResult.java                   # API响应封装
```

## 🚀 快速开始

### 环境要求
- JDK 17+
- Redis 6.0+
- Maven 3.6+
- Docker (可选，用于容器化部署)

### 配置说明

#### 1. Redis配置 (`application.properties`)
```properties
# Redis配置
redis.host=localhost
redis.port=6379

# 文件存储配置
file.storage.base-path=/path/to/storage
```

#### 2. 存储配置 (`application.properties`)
```properties
# 本地存储配置
storage.local.default.enabled=true
storage.local.default.primary=true
storage.local.default.base-path=/data/storage

# S3存储配置
storage.s3.default.enabled=true
storage.s3.default.endpoint=http://minio:9000
storage.s3.default.access-key=your_access_key
storage.s3.default.secret-key=your_secret_key
storage.s3.default.bucket-name=quickbox-files

# 存储策略配置
storage.strategy.type=PRIMARY_BACKUP
storage.primary.backend=local
storage.backup.backends=s3

# 文件夹上传配置
folder.upload.max-depth=10
folder.upload.max-total-size=10GB
folder.upload.max-files=1000
folder.upload.auto-zip=true
folder.upload.keep-structure=true
```

### 启动方式

#### 方式一：直接运行
```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/quick-box-server.jar

# 或使用Maven直接运行
mvn spring-boot:run
```

#### 方式二：Docker容器化部署
```bash
# 1. 克隆项目
git clone https://github.com/Jarrettluo/quick-box-server
cd quick-box-server/deploy

# 2. 构建镜像
./deploy.sh build

# 3. 启动服务
./deploy.sh start

# 4. 查看状态
./deploy.sh status
```

服务默认运行在 http://localhost:8080

## 📖 API文档

### 文件上传API

1. **初始化上传会话**
   ```
   POST /api/upload/init
   ```
   请求体：
   ```json
   {
     "filename": "example.zip",
     "totalSize": 10485760,
     "totalChunks": 5
   }
   ```
   响应：
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

2. **上传分片**
   ```
   POST /api/upload/upload
   ```
   参数：
   ```json
   {
     "uploadId": "ABCDEF",
     "chunkNumber": 3,
     "totalChunks": 5,
     "filename": "example.zip",
     "totalSize": 10485760,
     "file": "分片文件"
   }
   ```
   响应：
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

3. **合并分片**
   ```
   POST /api/upload/merge
   ```
   参数：
   ```json
   {
     "accessCode": "取件码"
   }
   ```
   响应：
   ```json
   {
     "code": 200,
     "message": "success",
     "data": "ABCDEF"
   }
   ```

4. **获取文件信息**
   ```
   GET /api/upload/info/{accessCode}
   ```
   响应：
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

5. **下载文件**
   ```
   GET /api/upload/download/{accessCode}
   ```
   响应：
   - 文件流下载
   - 自动设置Content-Disposition头
   - 下载后文件自动删除

### 文件夹上传API

1. **初始化文件夹上传**
   ```
   POST /api/upload/folder/init
   ```

2. **上传文件夹分片**
   ```
   POST /api/upload/folder/chunk
   ```

3. **合并文件夹分片**
   ```
   POST /api/upload/folder/merge
   ```

4. **获取文件夹信息**
   ```
   GET /api/upload/folder/info/{accessCode}
   ```

5. **下载文件夹**
   ```
   GET /api/upload/folder/download/{accessCode}
   ```

6. **下载文件夹中的文件**
   ```
   GET /api/upload/folder/file/{accessCode}
   ```

### 存储管理API

1. **获取存储后端列表**
   ```
   GET /api/storage/backends
   ```

2. **获取存储后端状态**
   ```
   GET /api/storage/status/{backendName}
   ```

3. **切换存储策略**
   ```
   POST /api/storage/strategy
   ```

4. **获取存储统计信息**
   ```
   GET /api/storage/stats
   ```

5. **获取存储使用情况**
   ```
   GET /api/storage/usage
   ```

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

### 存储抽象层设计
```
┌─────────────────────────────────────────┐
│            Business Layer               │
│  (FileUploadService, FolderUploadService)│
└─────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│         Storage Strategy Layer          │
│    (ConfigurableStorageStrategy)        │
└─────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│        Storage Service Factory          │
│       (StorageServiceFactory)           │
└─────────────────────────────────────────┘
                   │
                   ▼
┌────────────┬────────────┬──────────────┐
│   Local    │    S3      │   WebDAV     │
│  Storage   │  Storage   │   Storage    │
└────────────┴────────────┴──────────────┘
```

### 文件夹上传流程
```
1. 初始化上传
   ↓
2. 上传分片（支持ZIP和普通文件夹）
   ↓
3. 合并分片（自动解压ZIP）
   ↓
4. 保存目录结构
   ↓
5. 生成取件码
   ↓
6. 提供下载（自动打包为ZIP）
```

### 存储策略支持
- **主备模式** - 主存储不可用时自动切换到备份存储
- **负载均衡** - 在多个存储服务间均衡分配负载
- **分级存储** - 根据文件特征选择不同的存储层级
- **地理位置** - 根据用户位置选择最近的存储服务

## 🚀 部署指南

### 开发环境部署
```bash
# 1. 克隆项目
git clone https://github.com/Jarrettluo/quick-box-server
cd quick-box-server/deploy

# 2. 构建镜像
./deploy.sh build

# 3. 启动服务
./deploy.sh start

# 4. 查看状态
./deploy.sh status
```

### 生产环境部署
```bash
# 1. 进入部署目录
cd quick-box-server/deploy

# 2. 准备环境变量
cp .env.example .env
# 编辑.env文件配置生产环境参数

# 3. 使用生产环境配置启动
docker-compose -f docker-compose.prod.yml up -d

# 4. 配置Nginx和SSL证书
# 参考config/nginx.conf配置反向代理
```

### 管理命令
```bash
# 查看服务状态
./deploy.sh status

# 查看日志
./deploy.sh logs
./deploy.sh logs quickbox

# 重启服务
./deploy.sh restart

# 更新服务
./deploy.sh update

# 备份数据
./deploy.sh backup

# 清理服务
./deploy.sh cleanup
```

## 📊 监控和运维

### 健康检查
- 应用健康端点: `http://localhost:8080/actuator/health`
- 存储健康检查: 自动监控存储后端可用性
- 磁盘空间监控: 自动检测存储空间使用情况

### 日志管理
- 应用日志: `/data/logs/application.log`
- 访问日志: Nginx访问日志
- 错误日志: 集中错误日志收集

### 性能监控
- Prometheus指标: `http://localhost:8080/actuator/prometheus`
- Grafana仪表板: 预配置的监控仪表板
- 性能指标: 上传下载速度、并发数、错误率等

## ⚠️ 注意事项

### 安全性
- 取件码仅限单次使用，下载后立即失效
- 文件路径不暴露敏感信息
- 支持文件名URL编码，防止特殊字符问题
- 文件路径验证，防止目录遍历攻击
- 文件类型检查，防止恶意文件上传
- 文件大小限制，防止资源耗尽
- 存储凭据加密管理
- 数据传输加密（HTTPS）
- 数据备份和恢复机制

### 性能优化
- 分片上传支持大文件处理
- 文件合并使用流式操作，减少内存占用
- Redis缓存取件码状态，提高查询效率
- 连接池管理，提高存储访问效率
- 缓存策略，减少重复IO操作
- 批量操作，提高批量文件处理效率
- CDN集成，加速文件分发
- 压缩传输，减少网络带宽使用

## 🔧 故障排除

### 常见问题

#### 1. Redis连接失败
**症状**：应用启动失败，Redis连接错误
**解决**：
- 检查Redis服务是否运行：`redis-cli ping`
- 验证`application.properties`中的配置
- 检查防火墙设置

#### 2. 文件上传失败
**症状**：分片上传失败，存储错误
**解决**：
- 检查存储目录权限：`ls -la /path/to/storage`
- 确保磁盘空间充足：`df -h`
- 查看应用日志中的具体错误

#### 3. 分片合并失败
**症状**：合并时文件损坏或不完整
**解决**：
- 验证所有分片已上传完成
- 检查分片命名规则（数字序号）
- 确认元数据文件完整

#### 4. 取件码无效
**症状**：取件码查询返回不存在或已过期
**解决**：
- 检查Redis中键是否存在：`redis-cli keys "*"`
- 验证取件码是否已使用（下载后删除）
- 检查过期时间设置

## 📄 许可证

MIT License - 详见 LICENSE 文件

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进项目。

1. Fork本仓库
2. 创建功能分支 (git checkout -b feature/AmazingFeature)
3. 提交更改 (git commit -m 'Add some AmazingFeature')
4. 推送到分支 (git push origin feature/AmazingFeature)
5. 开启Pull Request

## 🔗 相关项目

- **前端项目**：https://github.com/Jarrettluo/quick-box-web
- **后端项目**：https://github.com/Jarrettluo/quick-box-server

## 📞 支持

如有问题或建议，请通过以下方式联系：

- 提交GitHub Issue
- 查看项目文档

## 🔮 后续开发建议

### 短期优化
1. 完善WebDAV和NAS存储实现
2. 增加更多的存储策略算法
3. 优化ZIP流式解压性能

### 中期扩展
1. 实现分布式部署支持
2. 增加CDN集成功能
3. 完善监控和告警系统

### 长期规划
1. 微服务架构改造
2. 人工智能文件处理
3. 边缘计算支持
