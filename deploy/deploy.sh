#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "命令 $1 未安装"
        exit 1
    fi
}

# 显示帮助
show_help() {
    echo "QuickBox 部署脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  build        构建Docker镜像"
    echo "  start        启动服务"
    echo "  stop         停止服务"
    echo "  restart      重启服务"
    echo "  status       查看服务状态"
    echo "  logs         查看服务日志"
    echo "  cleanup      清理服务"
    echo "  update       更新服务"
    echo "  backup       备份数据"
    echo "  restore      恢复数据"
    echo "  help         显示帮助信息"
    echo ""
}

# 构建镜像
build_image() {
    log_info "开始构建Docker镜像..."

    check_command docker
    check_command docker-compose

    docker-compose build

    if [ $? -eq 0 ]; then
        log_success "Docker镜像构建完成"
    else
        log_error "Docker镜像构建失败"
        exit 1
    fi
}

# 启动服务
start_services() {
    log_info "启动QuickBox服务..."

    # 检查环境变量文件
    if [ ! -f ".env" ]; then
        log_warning "未找到.env文件，创建示例配置文件"
        cp .env.example .env 2>/dev/null || create_env_file
        log_warning "请编辑.env文件配置环境变量"
    fi

    # 创建数据目录
    mkdir -p data/storage
    mkdir -p data/logs
    mkdir -p data/backups

    # 启动服务
    docker-compose up -d

    if [ $? -eq 0 ]; then
        log_success "服务启动成功"
        show_status
    else
        log_error "服务启动失败"
        exit 1
    fi
}

# 停止服务
stop_services() {
    log_info "停止QuickBox服务..."

    docker-compose down

    if [ $? -eq 0 ]; then
        log_success "服务停止成功"
    else
        log_error "服务停止失败"
        exit 1
    fi
}

# 重启服务
restart_services() {
    log_info "重启QuickBox服务..."

    docker-compose restart

    if [ $? -eq 0 ]; then
        log_success "服务重启成功"
        show_status
    else
        log_error "服务重启失败"
        exit 1
    fi
}

# 查看状态
show_status() {
    log_info "服务状态:"
    echo ""

    docker-compose ps

    echo ""
    log_info "容器资源使用:"
    docker stats --no-stream $(docker-compose ps -q) 2>/dev/null || echo "无法获取资源使用情况"

    echo ""
    log_info "服务健康状态:"

    # 检查QuickBox应用健康
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "QuickBox应用: 健康"
    else
        log_error "QuickBox应用: 不健康"
    fi

    # 检查Redis健康
    if docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; then
        log_success "Redis服务: 健康"
    else
        log_error "Redis服务: 不健康"
    fi

    # 检查存储目录
    if [ -d "data/storage" ]; then
        storage_size=$(du -sh data/storage 2>/dev/null | cut -f1)
        log_info "存储目录大小: $storage_size"
    fi
}

# 查看日志
show_logs() {
    local service=$1

    if [ -z "$service" ]; then
        log_info "查看所有服务日志 (Ctrl+C退出)..."
        docker-compose logs -f
    else
        log_info "查看 $service 服务日志 (Ctrl+C退出)..."
        docker-compose logs -f $service
    fi
}

# 清理服务
cleanup_services() {
    log_warning "清理QuickBox服务..."
    read -p "确定要清理服务吗？这将删除所有容器和镜像 (y/N): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v --rmi all

        # 清理未使用的资源
        docker system prune -f

        log_success "服务清理完成"
    else
        log_info "取消清理操作"
    fi
}

# 更新服务
update_services() {
    log_info "更新QuickBox服务..."

    # 拉取最新代码
    git pull

    # 重新构建镜像
    build_image

    # 重启服务
    restart_services

    log_success "服务更新完成"
}

# 备份数据
backup_data() {
    local backup_dir="data/backups"
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="quickbox_backup_$timestamp.tar.gz"

    log_info "开始备份数据..."

    # 创建备份目录
    mkdir -p $backup_dir

    # 停止服务
    docker-compose stop

    # 备份数据
    tar -czf "$backup_dir/$backup_file" data/storage data/redis 2>/dev/null

    # 启动服务
    docker-compose start

    # 计算备份大小
    backup_size=$(du -h "$backup_dir/$backup_file" | cut -f1)

    log_success "数据备份完成: $backup_file ($backup_size)"
    log_info "备份文件位置: $backup_dir/$backup_file"
}

# 恢复数据
restore_data() {
    local backup_file=$1

    if [ -z "$backup_file" ]; then
        log_error "请指定备份文件"
        echo "用法: $0 restore <备份文件>"
        exit 1
    fi

    if [ ! -f "$backup_file" ]; then
        log_error "备份文件不存在: $backup_file"
        exit 1
    fi

    log_warning "恢复数据从: $backup_file"
    read -p "确定要恢复数据吗？这将覆盖当前数据 (y/N): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        # 停止服务
        docker-compose stop

        # 清理旧数据
        rm -rf data/storage/*
        rm -rf data/redis/*

        # 恢复数据
        tar -xzf "$backup_file" -C .

        # 启动服务
        docker-compose start

        log_success "数据恢复完成"
    else
        log_info "取消恢复操作"
    fi
}

# 创建环境变量文件
create_env_file() {
    cat > .env << EOF
# QuickBox 环境变量配置

# Redis 配置
REDIS_PASSWORD=your_redis_password_here

# MinIO 配置 (可选)
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=password123

# S3 配置 (可选)
S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY=your_access_key
S3_SECRET_KEY=your_secret_key
S3_BUCKET_NAME=quickbox-files
S3_REGION=us-east-1

# 应用配置
FILE_STORAGE_BASE_PATH=/data/storage
STORAGE_STRATEGY_TYPE=PRIMARY_BACKUP
STORAGE_PRIMARY_BACKEND=local

# 监控配置 (可选)
GRAFANA_PASSWORD=admin

# 网络配置
QUICKBOX_NETWORK_SUBNET=172.20.0.0/16
EOF

    log_info "已创建.env.example文件，请复制为.env并修改配置"
}

# 主函数
main() {
    local command=$1
    local arg=$2

    case $command in
        build)
            build_image
            ;;
        start)
            start_services
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs $arg
            ;;
        cleanup)
            cleanup_services
            ;;
        update)
            update_services
            ;;
        backup)
            backup_data
            ;;
        restore)
            restore_data $arg
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            if [ -z "$command" ]; then
                show_help
            else
                log_error "未知命令: $command"
                show_help
                exit 1
            fi
            ;;
    esac
}

# 检查Docker和Docker Compose
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装"
        echo "请参考: https://docs.docker.com/get-docker/"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装"
        echo "请参考: https://docs.docker.com/compose/install/"
        exit 1
    fi

    # 检查Docker服务是否运行
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker服务未运行"
        exit 1
    fi
}

# 脚本入口
check_docker
main "$@"