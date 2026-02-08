# 创建安装脚本
cat > /tmp/install-quick-box.sh << 'EOF'
#!/bin/bash

set -e

echo "正在安装 quick-box 到 /home/ubuntu/quick_box..."

# 创建目录
mkdir -p /home/ubuntu/quick_box/{bin,logs,config,lib,backup}

# 复制脚本（这里假设脚本内容已经在文件中）
# 实际使用时，您需要将上面的脚本内容保存为文件

echo "安装完成！"
echo ""
echo "使用命令:"
echo "  quick-box start    # 启动应用"
echo "  quick-box status   # 查看状态"
echo "  quick-box logs     # 查看日志"
echo ""
echo "请确保将 quick-box-server-0.0.1-SNAPSHOT.jar 文件"
echo "复制到 /home/ubuntu/quick_box/ 目录下"
EOF

chmod +x /tmp/install-quick-box.sh