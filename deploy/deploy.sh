#!/bin/bash

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 全局变量
ENVIRONMENT="development"
EXTERNAL_REDIS="false"
EXTERNAL_MINIO="false"
DOCKER_COMPOSE_FILE="docker-compose.yml"

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

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --env)
                if [ -n "$2" ]; then
                    ENVIRONMENT="$2"
                    shift 2
                else
                    log_error "参数 --env 需要指定环境名称"
                    show_help
                    exit 1
                fi
                ;;
            --external-redis)
                EXTERNAL_REDIS="true"
                shift 1
                ;;
            --external-minio)
                EXTERNAL_MINIO="true"
                shift 1
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                # 其他参数视为命令
                COMMAND="$1"
                ARGS="${@:2}"
                break
                ;;
        esac
    done

    # 根据环境设置相应的配置文件
    case $ENVIRONMENT in
        development|dev)
            DOCKER_COMPOSE_FILE="docker-compose.yml"
            ;;
        production|prod)
            DOCKER_COMPOSE_FILE="docker-compose.prod.yml"
            ;;
        staging|test)
            DOCKER_COMPOSE_FILE="docker-compose.staging.yml"
            ;;
        *)
            log_error "未知环境: $ENVIRONMENT"
            show_help
            exit 1
            ;;
    esac

    log_info "部署环境: $ENVIRONMENT"
    log_info "使用配置文件: $DOCKER_COMPOSE_FILE"
    if [ "$EXTERNAL_REDIS" = "true" ]; then
        log_info "使用外部Redis服务"
    fi
    if [ "$EXTERNAL_MINIO" = "true" ]; then
        log_info "使用外部MinIO服务"
    fi
}

# 显示帮助
show_help() {
    echo "QuickBox 部署脚本"
    echo ""
    echo "用法: $0 [选项] [命令]"
    echo ""
    echo "选项:"
    echo "  --env <环境>       指定部署环境 (dev/prod/staging，默认: dev)"
    echo "  --external-redis   使用外部Redis服务，不启动内置Redis"
    echo "  --external-minio   使用外部MinIO服务，不启动内置MinIO"
    echo "  --help, -h         显示帮助信息"
    echo ""
    echo "命令:"
    echo "  build              构建Docker镜像"
    echo "  start              启动服务"
    echo "  stop               停止服务"
    echo "  restart            重启服务"
    echo "  status             查看服务状态"
    echo "  logs               查看服务日志"
    echo "  cleanup            清理服务"
    echo "  update             更新服务"
    echo "  backup             备份数据"
    echo "  restore <文件>     恢复数据"
    echo "  ssl                配置SSL证书"
    echo "  help               显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 start                      # 启动开发环境服务"
    echo "  $0 --env prod start           # 启动生产环境服务"
    echo "  $0 --env prod --external-redis start  # 使用外部Redis启动生产环境"
    echo "  $0 logs quickbox              # 查看QuickBox应用日志"
    echo "  $0 --env prod ssl             # 为生产环境配置SSL证书"
    echo ""
}

# 构建镜像
build_image() {
    log_info "开始构建Docker镜像..."

    check_command docker
    check_command docker-compose

    # 根据环境使用相应的Compose文件
    local compose_args="-f $DOCKER_COMPOSE_FILE build"

    if [ "$EXTERNAL_REDIS" = "true" ] && [ "$EXTERNAL_MINIO" = "true" ]; then
        log_info "使用外部Redis和MinIO服务，只构建QuickBox应用"
        compose_args="$compose_args quickbox"
    elif [ "$EXTERNAL_REDIS" = "true" ]; then
        log_info "使用外部Redis服务，构建QuickBox、MinIO和Nginx"
        compose_args="$compose_args quickbox minio nginx"
    elif [ "$EXTERNAL_MINIO" = "true" ]; then
        log_info "使用外部MinIO服务，构建QuickBox、Redis和Nginx"
        compose_args="$compose_args quickbox redis nginx"
    else
        log_info "构建所有服务镜像"
    fi

    docker-compose $compose_args

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
    local compose_args="-f $DOCKER_COMPOSE_FILE up -d"

    if [ "$EXTERNAL_REDIS" = "true" ] && [ "$EXTERNAL_MINIO" = "true" ]; then
        log_info "使用外部Redis和MinIO服务，只启动QuickBox应用"
        compose_args="$compose_args quickbox"
    elif [ "$EXTERNAL_REDIS" = "true" ]; then
        log_info "使用外部Redis服务，启动QuickBox和MinIO"
        compose_args="$compose_args quickbox minio nginx"
    elif [ "$EXTERNAL_MINIO" = "true" ]; then
        log_info "使用外部MinIO服务，启动QuickBox和Redis"
        compose_args="$compose_args quickbox redis nginx"
    else
        log_info "启动所有服务"
    fi

    docker-compose $compose_args

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

    docker-compose -f $DOCKER_COMPOSE_FILE down

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

    docker-compose -f $DOCKER_COMPOSE_FILE restart

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

    docker-compose -f $DOCKER_COMPOSE_FILE ps

    echo ""
    log_info "容器资源使用:"
    docker stats --no-stream $(docker-compose -f $DOCKER_COMPOSE_FILE ps -q) 2>/dev/null || echo "无法获取资源使用情况"

    echo ""
    log_info "服务健康状态:"

    # 检查QuickBox应用健康
    local quickbox_port="8080"
    if [ "$ENVIRONMENT" = "production" ] || [ "$ENVIRONMENT" = "prod" ]; then
        quickbox_port="80"  # 生产环境可能通过Nginx代理
    fi

    if curl -s http://localhost:$quickbox_port/actuator/health > /dev/null 2>&1; then
        log_success "QuickBox应用: 健康"
    else
        log_error "QuickBox应用: 不健康"
    fi

    # 检查Redis健康（如果是内置Redis）
    if [ "$EXTERNAL_REDIS" = "false" ]; then
        if docker-compose -f $DOCKER_COMPOSE_FILE exec -T redis redis-cli ping > /dev/null 2>&1; then
            log_success "Redis服务: 健康"
        else
            log_error "Redis服务: 不健康"
        fi
    else
        log_info "使用外部Redis服务"
    fi

    # 检查MinIO健康（如果是内置MinIO）
    if [ "$EXTERNAL_MINIO" = "false" ]; then
        if curl -s http://localhost:9000/minio/health/live > /dev/null 2>&1; then
            log_success "MinIO服务: 健康"
        else
            log_error "MinIO服务: 不健康"
        fi
    else
        log_info "使用外部MinIO服务"
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
        docker-compose -f $DOCKER_COMPOSE_FILE logs -f
    else
        log_info "查看 $service 服务日志 (Ctrl+C退出)..."
        docker-compose -f $DOCKER_COMPOSE_FILE logs -f $service
    fi
}

# 清理服务
cleanup_services() {
    log_warning "清理QuickBox服务..."
    read -p "确定要清理服务吗？这将删除所有容器和镜像 (y/N): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose -f $DOCKER_COMPOSE_FILE down -v --rmi all

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

# SSL 证书配置
configure_ssl() {
    if [ "$ENVIRONMENT" != "production" ] && [ "$ENVIRONMENT" != "prod" ]; then
        log_error "SSL 配置仅适用于生产环境"
        exit 1
    fi

    log_info "配置 SSL 证书..."

    check_command docker

    # 检查是否已安装 certbot
    if ! docker images | grep -q certbot; then
        log_info "拉取 Certbot 镜像..."
        docker pull certbot/certbot
    fi

    # 检查是否已配置域名
    if [ ! -f ".env" ]; then
        log_error "未找到环境变量配置文件，请先创建 .env 文件"
        exit 1
    fi

    DOMAIN=$(grep -E "^NGINX_SERVER_NAME=" .env | cut -d'=' -f2)
    if [ -z "$DOMAIN" ] || [ "$DOMAIN" = "localhost" ]; then
        log_error "请在 .env 文件中配置有效的域名（NGINX_SERVER_NAME）"
        log_error "示例: NGINX_SERVER_NAME=your-domain.com"
        exit 1
    fi

    log_info "为域名 $DOMAIN 申请 SSL 证书..."

    # 检查 Nginx 是否正在运行
    if ! docker-compose -f $DOCKER_COMPOSE_FILE ps nginx | grep -q "Up"; then
        log_warning "Nginx 未运行，需要先启动服务"
        log_info "启动 Nginx 服务..."
        docker-compose -f $DOCKER_COMPOSE_FILE up -d nginx
        sleep 5  # 等待 Nginx 启动
    fi

    # 使用 Certbot 申请证书
    local ssl_dir="config/ssl"
    mkdir -p $ssl_dir

    docker run -it --rm \
        --name certbot \
        -v "$(pwd)/$ssl_dir:/etc/letsencrypt" \
        -v "$(pwd)/data/webroot:/var/www/html" \
        --network quickbox-network-prod \
        certbot/certbot certonly --webroot \
        --webroot-path /var/www/html \
        --email noreply@$DOMAIN \
        --agree-tos \
        --no-eff-email \
        -d $DOMAIN

    if [ $? -eq 0 ]; then
        log_success "SSL 证书申请成功"

        # 创建符号链接
        ln -sf /etc/letsencrypt/live/$DOMAIN/fullchain.pem $ssl_dir/fullchain.pem
        ln -sf /etc/letsencrypt/live/$DOMAIN/privkey.pem $ssl_dir/privkey.pem

        # 重启 Nginx 以应用新证书
        log_info "重启 Nginx 以应用 SSL 证书..."
        docker-compose -f $DOCKER_COMPOSE_FILE restart nginx

        log_success "SSL 证书配置完成"
        log_info "访问地址: https://$DOMAIN"
    else
        log_error "SSL 证书申请失败"
        exit 1
    fi
}

# 自动更新 SSL 证书
renew_ssl() {
    if [ "$ENVIRONMENT" != "production" ] && [ "$ENVIRONMENT" != "prod" ]; then
        log_error "SSL 更新仅适用于生产环境"
        exit 1
    fi

    log_info "更新 SSL 证书..."

    check_command docker

    DOMAIN=$(grep -E "^NGINX_SERVER_NAME=" .env | cut -d'=' -f2)

    docker run -t --rm \
        --name certbot-renew \
        -v "$(pwd)/config/ssl:/etc/letsencrypt" \
        -v "$(pwd)/data/webroot:/var/www/html" \
        --network quickbox-network-prod \
        certbot/certbot renew \
        --webroot --webroot-path /var/www/html

    if [ $? -eq 0 ]; then
        log_success "SSL 证书更新成功"
        log_info "重启 Nginx 以应用新证书..."
        docker-compose -f $DOCKER_COMPOSE_FILE restart nginx
        log_success "SSL 证书更新完成"
    else
        log_error "SSL 证书更新失败"
        exit 1
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

# Nginx 配置
NGINX_SERVER_NAME=localhost
EOF

    log_info "已创建.env.example文件，请复制为.env并修改配置"
}

# 主函数
main() {
    case $COMMAND in
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
            show_logs $ARGS
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
            restore_data $ARGS
            ;;
        ssl)
            configure_ssl
            ;;
        renew-ssl)
            renew_ssl
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            if [ -z "$COMMAND" ]; then
                show_help
            else
                log_error "未知命令: $COMMAND"
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
parse_args "$@"
check_docker
main