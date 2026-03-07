#!/bin/bash

set -e

echo "=========================================="
echo "QuickBox Server 启动脚本"
echo "=========================================="

# 等待依赖服务
if [ -n "$WAIT_FOR_HOSTS" ]; then
    echo "等待依赖服务..."
    IFS=',' read -ra HOSTS <<< "$WAIT_FOR_HOSTS"
    for host in "${HOSTS[@]}"; do
        IFS=':' read -ra PARTS <<< "$host"
        hostname=${PARTS[0]}
        port=${PARTS[1]:-80}
        echo "等待 $hostname:$port..."
        while ! nc -z $hostname $port; do
            sleep 1
        done
        echo "$hostname:$port 就绪"
    done
fi

# 检查Redis连接
if [ -n "$REDIS_HOST" ] && [ -n "$REDIS_PORT" ]; then
    echo "检查Redis连接: $REDIS_HOST:$REDIS_PORT"
    timeout 10 bash -c "until redis-cli -h $REDIS_HOST -p $REDIS_PORT ping; do sleep 1; done" || {
        echo "Redis连接失败"
        exit 1
    }
    echo "Redis连接成功"
fi

# 创建必要的目录
echo "创建数据目录..."
mkdir -p /data/storage/chunks
mkdir -p /data/storage/files
mkdir -p /data/logs
mkdir -p /data/dumps

# 设置目录权限
chown -R appuser:appgroup /data
chmod -R 755 /data

# 检查存储目录
echo "检查存储目录..."
if [ ! -d "/data/storage" ]; then
    echo "错误: 存储目录不存在"
    exit 1
fi

# 显示配置信息
echo "=========================================="
echo "配置信息:"
echo "------------------------------------------"
echo "JAVA_OPTS: $JAVA_OPTS"
echo "SPRING_PROFILES_ACTIVE: $SPRING_PROFILES_ACTIVE"
echo "SERVER_PORT: $SERVER_PORT"
echo "REDIS_HOST: $REDIS_HOST"
echo "REDIS_PORT: $REDIS_PORT"
echo "FILE_STORAGE_BASE_PATH: $FILE_STORAGE_BASE_PATH"
echo "=========================================="

# 设置JVM参数
if [ -n "$JAVA_OPTS" ]; then
    echo "使用自定义JVM参数: $JAVA_OPTS"
    export JAVA_TOOL_OPTIONS="$JAVA_OPTS"
fi

# 启动应用
echo "启动QuickBox Server..."
exec java $JAVA_OPTS -jar app.jar "$@"