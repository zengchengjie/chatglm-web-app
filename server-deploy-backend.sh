#!/bin/bash

# ChatGLM Web App 后端部署脚本（服务器端执行）
# 用于在服务器上编译并更新Docker环境下的后端服务

set -e  # 遇到错误立即退出

# 配置变量
PROJECT_DIR="/root/chatglm-web-app"   # 项目目录
BACKEND_DIR="$PROJECT_DIR/backend"   # 后端代码目录
SERVICE_NAME="chatglm-web-app-chatglm-backend"  # Docker服务名称
JAR_FILE="chatglm-backend.jar"       # 打包后的jar文件名
DOCKER_COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"  # Docker Compose文件

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

# 2. 检查jar文件是否存在
echo "步骤 2: 检查jar文件..."
if [ ! -f "$BACKEND_DIR/target/$JAR_FILE" ]; then
    echo "错误: 找不到编译后的JAR文件: $BACKEND_DIR/target/$JAR_FILE"
    exit 1
fi
echo "JAR文件检查通过!"

# 3. 检查Docker和Docker Compose
echo "步骤 3: 检查Docker环境..."
if ! command -v docker &> /dev/null; then
    echo "错误: 未找到Docker，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未找到Docker Compose，请先安装Docker Compose"
    exit 1
fi

# 4. 停止并重启Docker服务
echo "步骤 4: 重启Docker服务..."
cd "$PROJECT_DIR"

# 停止服务
docker-compose stop $SERVICE_NAME

# 重新构建并启动服务
docker-compose up -d --build $SERVICE_NAME

# 5. 检查服务状态
echo "步骤 5: 检查服务状态..."
docker-compose ps $SERVICE_NAME

# 6. 查看服务日志（最近10行）
echo ""
echo "最近的服务日志:"
docker-compose logs --tail=10 $SERVICE_NAME

echo ""
echo "======================================"
echo "部署完成! 后端服务已更新并重启"
echo "查看完整日志命令: docker-compose logs -f $SERVICE_NAME"
echo "======================================"
