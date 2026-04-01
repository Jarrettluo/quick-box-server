# QuickBox 项目容器化部署指南

## 项目概述

QuickBox（快取柜）是一个基于Spring Boot的临时文件共享服务后端，提供文件上传、下载和管理功能。文件在7天后自动删除，或下载后立即删除。

本部署指南提供了完整的容器化部署方案，支持开发、测试和生产环境的快速部署。

## 架构设计

### 整体架构
```
┌─────────────────────────────────────────────────────────────┐
│                     客户端 (Browser)                        │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Nginx (反向代理 + 前端静态文件)                 │
│  - 处理HTTP/HTTPS请求                                       │
│  - 提供前端静态文件                                         │
│  - 反向代理后端API                                         │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              QuickBox 后端应用 (Spring Boot)                 │
│  - 文件上传/下载处理                                       │
│  - 分片管理与合并                                           │
│  - 取件码生成与验证                                       │
│  - Redis缓存操作                                           │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              Redis (缓存与会话存储)                         │
│  - 取件码存储 (7天过期)                                     │
│  - 上传会话管理 (24小时过期)                               │
│  - 文件元数据缓存                                         │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              存储系统 (本地存储 + 可选MinIO/S3)               │
│  - 分片存储                                                │
│  - 合并后文件存储                                           │
│  - 支持本地存储或对象存储 (MinIO/AWS S3/阿里云OSS)           │
└─────────────────────────────────────────────────────────────┘
```

## 文件结构

```
deploy/
├── docker/
│   ├── frontend/
│   │   ├── Dockerfile              # 前端构建镜像
│   │   └── .dockerignore          # 前端构建忽略文件
│   ├── backend/
│   │   ├── Dockerfile             # 后端构建镜像（已存在）
│   │   └── docker-entrypoint.sh   # 后端启动脚本（已存在）
│   └── nginx/
│       ├── nginx.conf             # 开发环境Nginx配置
│       ├── nginx.prod.conf        # 生产环境Nginx配置
│       └── ssl/                   # SSL证书目录（可选）
├── config/
│   ├── application-docker.yml     # Docker环境配置
│   ├── application-production.yml # 生产环境配置
│   └── redis.conf                # Redis配置
├── docker-compose.yml             # 开发环境Compose配置（已存在）
├── docker-compose.prod.yml        # 生产环境Compose配置（已存在）
├── .env.example                   # 环境变量示例文件
├── deploy.sh                      # 一键部署脚本（已存在）
├── README_部署指南.md             # 详细部署说明
└── README_前端项目说明.md          # 前端项目说明（已存在）
```

## 部署前准备

### 1. 系统要求

- **操作系统**：Linux (推荐 Ubuntu 20.04+ 或 CentOS 7+)
- **内存**：至少 4GB RAM（生产环境建议 8GB+）
- **磁盘空间**：至少 20GB 可用空间（根据实际存储需求调整）
- **网络**：可访问互联网（用于拉取 Docker 镜像和申请 SSL 证书）

### 2. 软件依赖

```bash
# Docker 20.10+
# Docker Compose 2.0+

# 检查 Docker 是否安装
docker --version

# 检查 Docker Compose 是否安装
docker-compose --version
```

### 3. 网络配置

- **开发环境**：需要开放端口 80、443、8080、9000、9001、6379
- **生产环境**：需要开放端口 80、443（其他端口可根据需要调整）

## 快速部署

### 1. 获取项目代码

```bash
# 克隆项目仓库
git clone <repository-url>
cd quick-box-server/deploy
```

### 2. 配置环境变量

```bash
# 复制环境变量示例文件
cp .env.example .env

# 编辑配置文件（根据实际需求修改）
vi .env

# 至少需要修改以下配置：
# - REDIS_PASSWORD：Redis 密码
# - MINIO_ROOT_USER/MINIO_ROOT_PASSWORD：MinIO 管理用户密码
# - STORAGE_PRIMARY_BACKEND：存储方式（local/minio/s3）
# - 如使用 MinIO/S3，需配置相应的访问密钥和存储桶信息
```

### 3. 执行一键部署

```bash
# 使脚本可执行
chmod +x deploy.sh

# 检查部署环境
./deploy.sh status

# 构建并启动服务
./deploy.sh build
./deploy.sh start

# 查看服务状态
./deploy.sh status
```

## 部署模式

### 开发环境部署

```bash
# 默认模式（开发环境）
./deploy.sh start

# 或明确指定环境
./deploy.sh --env dev start
```

**访问地址**：
- 前端应用：http://localhost
- 后端 API：http://localhost/api
- MinIO 控制台：http://localhost:9001

### 生产环境部署

```bash
# 生产环境部署
./deploy.sh --env prod start

# 使用外部 Redis（生产环境常见场景）
./deploy.sh --env prod --external-redis start

# 使用外部 MinIO（生产环境常见场景）
./deploy.sh --env prod --external-minio start

# 使用外部 Redis 和 MinIO
./deploy.sh --env prod --external-redis --external-minio start
```

**生产环境说明**：
- 使用 Nginx 作为反向代理
- 默认配置 HTTPS（需要申请 SSL 证书）
- 使用持久化存储卷
- 配置了监控和日志收集

### 配置 SSL 证书（生产环境）

```bash
# 为生产环境配置 SSL 证书
./deploy.sh --env prod ssl

# 更新 SSL 证书（Let's Encrypt 证书有效期为 90 天）
./deploy.sh --env prod renew-ssl
```

## 常用命令

### 服务管理

```bash
# 启动服务
./deploy.sh start

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看服务状态
./deploy.sh status

# 查看服务日志
./deploy.sh logs
./deploy.sh logs quickbox      # 只查看 QuickBox 应用日志
./deploy.sh logs --tail 100 -f # 实时查看最后 100 行日志

# 清理服务（删除容器、镜像和数据卷）
./deploy.sh cleanup
```

### 数据管理

```bash
# 备份数据（包括文件、Redis 数据等）
./deploy.sh backup

# 恢复数据
./deploy.sh restore quickbox_backup_20241220_103045.tar.gz
```

### 更新服务

```bash
# 拉取最新代码并更新服务
./deploy.sh update
```

### 高级功能

```bash
# 只构建镜像不启动服务
./deploy.sh build

# 查看容器资源使用情况
./deploy.sh status

# 进入容器内部
docker exec -it quickbox-app bash

# 查看网络配置
docker network inspect quickbox-network
```

## 存储配置

### 本地存储（默认）

```bash
# .env 文件配置
STORAGE_STRATEGY_TYPE=PRIMARY_BACKUP
STORAGE_PRIMARY_BACKEND=local
FILE_STORAGE_BASE_PATH=/data/storage
```

**存储路径**：
- 主机路径：`./data/storage/`
- 容器路径：`/data/storage/`

### MinIO 存储

```bash
# .env 文件配置
STORAGE_STRATEGY_TYPE=PRIMARY_BACKUP
STORAGE_PRIMARY_BACKEND=minio
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=quickbox
MINIO_SECRET_KEY=quickbox123
MINIO_BUCKET_NAME=quickbox-files
```

### AWS S3 存储

```bash
# .env 文件配置
STORAGE_STRATEGY_TYPE=PRIMARY_BACKUP
STORAGE_PRIMARY_BACKEND=s3
S3_ENDPOINT=https://s3.amazonaws.com
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
S3_BUCKET_NAME=quickbox-files
S3_REGION=us-east-1
```

## 监控与维护

### 健康检查

```bash
# 检查 QuickBox 应用健康
curl http://localhost:8080/actuator/health

# 检查 Nginx 健康
curl http://localhost/health

# 检查 MinIO 健康
curl http://localhost:9000/minio/health/live

# 检查 Redis 健康
redis-cli -h localhost -p 6379 -a <password> ping
```

### 监控指标

**Spring Boot Actuator**：
- `/actuator/health`：健康检查
- `/actuator/info`：应用信息
- `/actuator/metrics`：性能指标

**Prometheus 监控**（生产环境）：
- 访问地址：http://localhost:9090
- 配置文件：`config/prometheus.yml`

**Grafana 仪表板**（生产环境）：
- 访问地址：http://localhost:3000
- 默认用户名/密码：admin/admin
- 仪表板文件：`config/grafana/dashboards/*.json`

### 日志管理

**服务日志**：
```bash
# 查看所有服务日志
./deploy.sh logs

# 查看特定服务日志
./deploy.sh logs quickbox
./deploy.sh logs redis
./deploy.sh logs nginx

# 实时查看日志
./deploy.sh logs -f

# 查看最后 500 行日志
./deploy.sh logs --tail 500
```

**文件日志**：
- 应用日志：`data/logs/quick-box-server.log`
- Nginx 访问日志：`data/logs/nginx/access.log`
- Nginx 错误日志：`data/logs/nginx/error.log`

## 故障排查

### 常见问题

#### 1. 服务无法启动

```bash
# 检查 Docker 服务状态
systemctl status docker

# 检查 Docker Compose 状态
./deploy.sh status

# 查看服务日志
./deploy.sh logs

# 检查端口是否被占用
netstat -tuln | grep -E "(80|443|8080|9000|9001|6379)"

# 检查存储目录权限
ls -la data/storage
```

#### 2. 文件上传失败

```bash
# 检查存储目录权限
chmod 755 data/storage

# 检查磁盘空间
df -h

# 查看应用日志
./deploy.sh logs quickbox
```

#### 3. Redis 连接失败

```bash
# 检查 Redis 服务状态
./deploy.sh logs redis

# 测试 Redis 连接
redis-cli -h localhost -p 6379 -a <password> ping
```

#### 4. SSL 证书问题

```bash
# 检查证书文件
ls -la config/ssl

# 测试证书有效性
openssl x509 -in config/ssl/fullchain.pem -text -noout

# 重新申请证书
./deploy.sh --env prod ssl
```

### 性能优化

#### 1. JVM 内存配置

```bash
# 在 .env 文件中调整
JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

#### 2. Redis 内存配置

```bash
# 在 .env 文件中调整
REDIS_MAXMEMORY=2gb
REDIS_MAXMEMORY_POLICY=allkeys-lru
```

#### 3. 文件上传性能

```bash
# 在 .env 文件中调整
VITE_UPLOAD_CHUNK_SIZE=10485760  # 10MB（默认5MB）
VITE_UPLOAD_CONCURRENT=5        # 并发上传数（默认3）
```

## 数据迁移

### 本地存储到 MinIO 迁移

```bash
# 1. 确保 MinIO 服务已启动
./deploy.sh status

# 2. 创建存储桶（如不存在）
mc mb quickbox/quickbox-files

# 3. 复制文件到 MinIO
mc cp -r ./data/storage/files quickbox/quickbox-files

# 4. 更新配置
# 修改 .env 文件中的 STORAGE_PRIMARY_BACKEND 为 minio

# 5. 重启服务
./deploy.sh restart
```

### 备份与恢复

```bash
# 备份数据
./deploy.sh backup

# 查看备份文件
ls -la data/backups

# 恢复数据
./deploy.sh restore quickbox_backup_20241220_103045.tar.gz
```

## 升级与维护

### 版本升级

```bash
# 1. 备份当前版本
./deploy.sh backup

# 2. 拉取最新代码
git pull

# 3. 更新配置文件
# 检查是否有新的配置选项需要更新
diff -u .env.example .env

# 4. 重新构建和启动
./deploy.sh build
./deploy.sh restart
```

### 系统更新

```bash
# 更新系统包（Ubuntu/Debian）
apt update && apt upgrade -y

# 更新系统包（CentOS）
yum update -y

# 重启系统（如需要）
reboot
```

### 安全维护

```bash
# 更新 Docker 镜像
docker pull redis:7-alpine
docker pull nginx:alpine
docker pull minio/minio:latest

# 重新构建应用镜像
./deploy.sh build

# 重启服务
./deploy.sh restart
```

## 卸载

```bash
# 停止服务
./deploy.sh stop

# 清理服务（删除容器、镜像、数据卷）
./deploy.sh cleanup

# 删除项目文件
cd ..
rm -rf quick-box-server
```

## 联系方式

如有部署问题或建议，欢迎通过以下方式联系：

- 项目仓库：[GitHub](https://github.com/your-username/quick-box-server)
- 问题反馈：[Issues](https://github.com/your-username/quick-box-server/issues)
- 文档更新：[Wiki](https://github.com/your-username/quick-box-server/wiki)

---

**最后更新**：2024 年 12 月
**文档版本**：1.0
**部署方案**：基于 Docker Compose 的完整容器化部署方案
