#!/bin/bash

# ChatGLM Web App 后端部署脚本
# 用于编译并更新云服务器上的后端服务

set -e  # 遇到错误立即退出

# 配置变量
PROJECT_NAME="chatglm-web-app"
BACKEND_DIR="$PROJECT_NAME/backend"
REMOTE_SERVER="111.229.177.132"  # 云服务器地址
REMOTE_USER="root"                # 云服务器用户名
REMOTE_DIR="/opt/$PROJECT_NAME"   # 云服务器上的部署目录
SERVICE_NAME="chatglm-backend"     # 服务名称
JAR_FILE="$SERVICE_NAME.jar"      # 打包后的jar文件名
LOCAL_JAR_PATH="$BACKEND_DIR/target/$JAR_FILE"  # 本地jar文件路径

echo "======================================"
echo "开始部署 $PROJECT_NAME 后端服务"
echo "======================================"

# 1. 编译后端项目
echo "步骤 1: 编译后端项目..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "错误: Maven 编译失败!"
    exit 1
fi

echo "编译成功完成!"

# 2. 创建远程目录（如果不存在）
echo "步骤 2: 检查并创建远程目录..."
ssh "$REMOTE_USER@$REMOTE_SERVER" "mkdir -p $REMOTE_DIR"

# 3. 上传jar文件到服务器
echo "步骤 3: 上传jar文件到服务器..."
scp "$LOCAL_JAR_PATH" "$REMOTE_USER@$REMOTE_SERVER:$REMOTE_DIR/"

if [ $? -ne 0 ]; then
    echo "错误: 文件上传失败!"
    exit 1
fi

# 4. 上传systemd服务文件（如果不存在）
echo "步骤 4: 检查并上传systemd服务文件..."
SERVICE_FILE_CONTENT="[Unit]
Description=ChatGLM Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$REMOTE_DIR
ExecStart=/usr/bin/java -jar $REMOTE_DIR/$JAR_FILE
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target"

# 检查服务文件是否已存在
ssh "$REMOTE_USER@$REMOTE_SERVER" "if [ ! -f /etc/systemd/system/$SERVICE_NAME.service ]; then echo '$SERVICE_FILE_CONTENT' > /etc/systemd/system/$SERVICE_NAME.service; systemctl daemon-reload; systemctl enable $SERVICE_NAME; fi"

# 5. 重启服务
echo "步骤 5: 重启服务..."
ssh "$REMOTE_USER@$REMOTE_SERVER" "systemctl restart $SERVICE_NAME"

# 6. 检查服务状态
echo "步骤 6: 检查服务状态..."
ssh "$REMOTE_USER@$REMOTE_SERVER" "systemctl status $SERVICE_NAME --no-pager"

echo ""
echo "======================================"
echo "部署完成! 后端服务已更新并重启"
echo "======================================"
