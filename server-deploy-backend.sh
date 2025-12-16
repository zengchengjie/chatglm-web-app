#!/bin/bash

# ChatGLM Web App 后端部署脚本（服务器端执行）
# 用于在服务器上编译并更新后端服务

set -e  # 遇到错误立即退出

# 配置变量
PROJECT_DIR="/root/chatglm-web-app"   # 项目目录
BACKEND_DIR="$PROJECT_DIR/backend"   # 后端代码目录
SERVICE_NAME="chatglm-backend-1.0.0"       # 服务名称
JAR_FILE="$SERVICE_NAME.jar"          # 打包后的jar文件名
JAR_PATH="$PROJECT_DIR/$JAR_FILE"     # jar文件路径

echo "======================================"
echo "开始部署 ChatGLM Web App 后端服务"
echo "======================================"

# 1. 进入后端目录并编译项目
echo "步骤 1: 编译后端项目..."
cd "$BACKEND_DIR"

# 检查是否有Maven
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven，请先安装Maven"
    exit 1
fi

# 执行编译
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "错误: Maven 编译失败!"
    exit 1
fi

echo "编译成功完成!"

# 2. 复制jar文件到项目根目录
echo "步骤 2: 复制jar文件..."
cp "$BACKEND_DIR/target/$JAR_FILE" "$JAR_PATH"

# 3. 创建systemd服务文件（如果不存在）
echo "步骤 3: 检查并配置systemd服务..."
if [ ! -f /etc/systemd/system/$SERVICE_NAME.service ]; then
    echo "创建systemd服务文件..."
    cat > /etc/systemd/system/$SERVICE_NAME.service << EOF
[Unit]
Description=ChatGLM Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$PROJECT_DIR
ExecStart=/usr/bin/java -jar $JAR_PATH
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

    # 重新加载systemd并启用服务
    systemctl daemon-reload
    systemctl enable $SERVICE_NAME
    echo "systemd服务文件已创建并启用"
else
    echo "systemd服务文件已存在，跳过创建"
fi

# 4. 重启服务
echo "步骤 4: 重启服务..."
systemctl restart $SERVICE_NAME

# 5. 检查服务状态
echo "步骤 5: 检查服务状态..."
systemctl status $SERVICE_NAME --no-pager

echo ""
echo "======================================"
echo "部署完成! 后端服务已更新并重启"
echo "服务日志查看命令: journalctl -u $SERVICE_NAME -f"
echo "======================================"
