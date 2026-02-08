# QuickBox 项目长期记忆

## 项目概述

QuickBox（快取柜）是一个基于Spring Boot的临时文件共享服务后端，提供文件上传、下载和管理功能。文件在7天后自动删除，或下载后立即删除。

**核心特性**：
- 大文件分片上传（适配vue-simple-uploader）
- 取件码单次使用机制
- 自动文件清理
- Redis缓存管理

## 项目结构

### 主要目录
```
src/main/java/com/jiaruiblog/quickboxserver/
├── controller/FileUploadController.java        # 文件上传控制器
├── service/FileUploadService.java              # 服务接口
├── service/impl/FileUploadServiceImpl.java     # 服务实现
├── model/                                      # 数据模型
│   ├── dto/FileInfo.java                       # 文件信息DTO
│   ├── request/                                # 请求对象
│   └── response/                               # 响应对象
├── config/                                     # 配置类
│   ├── FileStorageConfig.java                  # 文件存储配置 ★重要
│   ├── RedisConfig.java                        # Redis配置
│   └── CorsConfig.java                         # CORS配置
├── exception/                                  # 异常处理
│   ├── BusinessException.java                  # 业务异常
│   ├── ErrorCode.java                          # 错误码枚举
│   └── GlobalExceptionHandler.java             # 全局异常处理器
└── common/                                     # 通用组件
    ├── ApiResult.java                          # API响应封装
    ├── MessageConstant.java                    # 消息常量
    └── RegexConstant.java                      # 正则常量
```

### 关键文件说明
1. **FileStorageConfig.java** - 文件存储配置，包含Windows/Linux路径适配
2. **FileUploadController.java** - 所有API端点定义
3. **FileUploadServiceImpl.java** - 核心业务逻辑实现
4. **application.properties** - 应用配置文件

## 配置说明

### Redis配置
```properties
redis.host=localhost
redis.port=16379
# 注意：生产环境需要设置密码和适当超时
```

### 文件存储配置
```java
// FileStorageConfig.java 中定义
@Value("${file.storage.base-path}")
private String basePath;  // 基础存储路径

// 默认值：
// Windows: C:/app/chunks/ 和 C:/app/files/
// Linux: /home/ubuntu/quick_box/box_data/chunks/ 和 /home/ubuntu/quick_box/box_data/files/
```

### 过期时间配置
- 上传会话过期：24小时（86400秒）
- 取件码过期：7天（604800秒）
- 文件保留时间：7天或下载后立即删除

## API接口

### 上传流程
1. `POST /api/upload/init` - 初始化上传，生成取件码
2. `POST /api/upload/upload` - 上传分片
3. `GET /api/upload/progress/{uploadId}` - 查询进度
4. `POST /api/upload/merge` - 合并分片

### 下载流程
1. `GET /api/upload/info/{accessCode}` - 查询文件信息
2. `GET /api/upload/download/{accessCode}` - 下载文件

### 响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 核心业务逻辑

### 取件码生成
- 6位大写字母随机码（A-Z）
- 26^6 ≈ 3亿种组合
- 存储在Redis中，7天过期
- 单次使用，下载后删除

### 分片上传
1. 前端将大文件分割为多个分片
2. 每个分片单独上传
3. 服务器按序号存储分片
4. 全部分片上传完成后合并

### 文件合并
1. 按分片序号排序
2. 使用BufferedInputStream/BufferedOutputStream流式合并
3. 验证文件完整性（大小校验）
4. 删除临时分片文件

### 清理机制
1. **定时清理**：每天凌晨3点清理过期会话
2. **下载清理**：文件下载后立即删除物理文件
3. **Redis过期**：自动清理7天前的取件码

## 部署信息

### 部署结构
```
/home/ubuntu/quick_box/
├── bin/                    # 启动脚本
├── logs/                   # 日志文件
├── config/                 # 配置文件
├── lib/                    # 依赖库
├── backup/                 # 备份文件
└── box_data/              # 文件存储
    ├── chunks/            # 分片文件
    └── files/             # 最终文件
```

### 部署命令
```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/quick-box-server.jar

# 或使用部署脚本
quick-box start     # 启动
quick-box stop      # 停止
quick-box restart   # 重启
quick-box status    # 状态
quick-box logs      # 日志
```

## 开发注意事项

### 1. 文件路径处理
- 使用`Paths.get()`处理路径分隔符兼容性
- 注意Windows和Linux路径差异
- 文件操作使用try-with-resources确保资源释放

### 2. Redis操作
- 使用RedisTemplate进行序列化操作
- 注意键的命名规范：`access_code:{code}`
- 设置合理的过期时间

### 3. 异常处理
- 业务异常使用`BusinessException`
- 统一异常响应格式
- 记录详细的异常日志

### 4. 性能优化
- 大文件使用流式操作
- Redis连接池配置
- 分片上传减少内存占用

### 5. 安全性
- 取件码单次使用
- 文件路径验证，防止目录遍历
- 输入参数校验

## 常见问题和解决方案

### 1. Redis连接失败
**症状**：应用启动失败，Redis连接错误
**解决**：
- 检查Redis服务是否运行：`redis-cli ping`
- 验证`application.properties`中的配置
- 检查防火墙设置

### 2. 文件上传失败
**症状**：分片上传失败，存储错误
**解决**：
- 检查存储目录权限：`ls -la /path/to/storage`
- 确保磁盘空间充足：`df -h`
- 查看应用日志中的具体错误

### 3. 分片合并失败
**症状**：合并时文件损坏或不完整
**解决**：
- 验证所有分片已上传完成
- 检查分片命名规则（数字序号）
- 确认元数据文件完整

### 4. 取件码无效
**症状**：取件码查询返回不存在或已过期
**解决**：
- 检查Redis中键是否存在：`redis-cli keys "*"`
- 验证取件码是否已使用（下载后删除）
- 检查过期时间设置

## 监控和日志

### 日志文件位置
- 控制台日志：Spring Boot默认输出
- 文件日志：配置日志文件路径
- 访问日志：记录API调用

### 关键监控指标
1. **文件上传成功率**
2. **平均上传时间**
3. **存储空间使用率**
4. **Redis连接状态**
5. **系统错误率**

### 日志查询命令
```bash
# 查看实时日志
tail -f logs/application.log

# 查找错误日志
grep "ERROR" logs/application.log

# 查看特定API调用
grep "POST /api/upload" logs/access.log
```

## 扩展和定制

### 存储后端扩展
当前使用本地文件系统，可扩展支持：
1. **MinIO**：兼容S3协议的对象存储
2. **AWS S3**：云存储服务
3. **阿里云OSS**：国内云存储
4. **七牛云**：CDN加速存储

### 功能扩展建议
1. **用户认证**：增加JWT token认证
2. **文件预览**：支持图片、文档预览
3. **分享设置**：设置下载次数、有效期
4. **统计报表**：上传下载统计
5. **Web界面**：管理后台

### 性能优化建议
1. **CDN集成**：静态文件CDN加速
2. **负载均衡**：多实例部署
3. **数据库优化**：Redis集群
4. **异步处理**：文件处理异步化

## 环境配置示例

### 开发环境
```properties
server.port=8089
redis.host=localhost
redis.port=6379
file.storage.base-path=./uploads
```

### 测试环境
```properties
server.port=8080
redis.host=test-redis
redis.port=6379
file.storage.base-path=/data/uploads
```

### 生产环境
```properties
server.port=80
redis.host=production-redis
redis.port=6379
redis.password=${REDIS_PASSWORD}
file.storage.base-path=/mnt/storage/uploads
```

## 版本信息

### 当前版本
- Spring Boot: 3.5.4
- Java: 17+
- Redis: 6.0+
- Maven: 3.6+

### 依赖库
```xml
<!-- 核心依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

## 维护记录

### 重要更新
1. **2024-12-20**：增加分片上传功能
2. **2024-12-21**：优化文件合并性能
3. **2024-12-22**：增加异常处理和日志
4. **2024-12-23**：完善部署脚本和配置

### 待办事项
- [ ] 增加单元测试覆盖率
- [ ] 实现MinIO存储支持
- [ ] 增加API限流功能
- [ ] 完善监控告警

## 联系信息

### 项目维护者
- **项目负责人**：开发团队
- **问题反馈**：GitHub Issues
- **文档维护**：开发团队

### 支持资源
- [GitHub仓库](https://github.com/username/quick-box-server)
- [API文档](README.md)
- [部署指南](deploy/README.md)

---

**最后更新**：2024年12月
**文档状态**：持续维护中
**更新频率**：随项目版本更新