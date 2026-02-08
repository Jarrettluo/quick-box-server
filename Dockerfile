# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build

# 设置工作目录
WORKDIR /app

# 复制项目文件
COPY pom.xml .
COPY src ./src

# 下载依赖并构建应用
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 安装必要的工具
RUN apk add --no-cache \
    bash \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建应用用户和目录
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 创建应用目录
RUN mkdir -p /app && chown -R appuser:appgroup /app

# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar文件
COPY --from=build /app/target/*.jar app.jar

# 复制启动脚本
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

# 创建数据目录
RUN mkdir -p /data/storage && chown -R appuser:appgroup /data

# 暴露端口
EXPOSE 8080

# 切换到非root用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 设置JVM参数
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/dumps"

# 设置应用参数
ENV SPRING_PROFILES_ACTIVE=docker
ENV SERVER_PORT=8080
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379
ENV FILE_STORAGE_BASE_PATH=/data/storage

# 入口点
ENTRYPOINT ["./docker-entrypoint.sh"]

# 启动命令
CMD ["java", "-jar", "app.jar"]