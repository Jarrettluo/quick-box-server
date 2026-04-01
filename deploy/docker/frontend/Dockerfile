# 前端构建镜像
# 使用 Node 18 作为基础镜像
FROM node:18-alpine as builder

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache git

# 克隆前端项目
RUN git clone https://github.com/Jarrettluo/quick-box-web.git .

# 安装依赖
RUN npm ci --only=production

# 构建生产版本
RUN npm run build

# 生产环境使用 Nginx
FROM nginx:alpine

# 复制构建好的前端文件到 Nginx 目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置文件
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
