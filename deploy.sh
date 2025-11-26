#!/bin/bash

# ChatGLM企业级项目部署脚本
# 支持本地开发环境、测试环境和生产环境部署

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

# 显示帮助信息
show_help() {
    cat << EOF
ChatGLM企业级项目部署脚本

使用方法: $0 [环境] [选项]

环境:
    dev     开发环境
    test    测试环境  
    prod    生产环境

选项:
    -h, --help      显示帮助信息
    -b, --build     构建项目
    -d, --deploy    部署项目
    -m, --monitor   启动监控
    -a, --all       执行所有操作

示例:
    $0 dev -a          # 开发环境完整部署
    $0 test --build    # 测试环境仅构建
    $0 prod --deploy   # 生产环境仅部署
EOF
}

# 检查依赖
check_dependencies() {
    log_info "检查系统依赖..."
    
    local deps=("docker" "docker-compose" "java" "mvn" "npm")
    local missing=()
    
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            missing+=("$dep")
        fi
    done
    
    if [ ${#missing[@]} -ne 0 ]; then
        log_error "缺少依赖: ${missing[*]}"
        exit 1
    fi
    
    log_success "所有依赖检查通过"
}

# 构建后端
build_backend() {
    log_info "构建后端项目..."
    
    cd backend
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        log_success "后端构建成功"
    else
        log_error "后端构建失败"
        exit 1
    fi
    
    cd ..
}

# 构建前端
build_frontend() {
    log_info "构建前端项目..."
    
    cd frontend
    npm install
    npm install terser --save-dev
    npm run build
    
    if [ $? -eq 0 ]; then
        log_success "前端构建成功"
    else
        log_error "前端构建失败"
        exit 1
    fi
    
    cd ..
}

# 构建Docker镜像
build_docker() {
    log_info "构建Docker镜像..."
    
    cd backend
    docker build --no-cache --disable-content-trust=true -t chatglm-backend:latest .
    cd ..
    
    if [ $? -eq 0 ]; then
        log_success "Docker镜像构建成功"
    else
        log_error "Docker镜像构建失败"
        exit 1
    fi
}

# 启动开发环境
deploy_dev() {
    log_info "启动开发环境..."
    
    # 启动Redis和RabbitMQ
    docker-compose up -d redis rabbitmq
    
    # 等待服务启动
    sleep 10
    
    # 启动后端服务
    cd backend
    mvn spring-boot:run &
    BACKEND_PID=$!
    cd ..
    
    # 启动前端服务
    cd frontend
    npm run dev &
    FRONTEND_PID=$!
    cd ..
    
    log_success "开发环境启动成功"
    echo "后端服务PID: $BACKEND_PID"
    echo "前端服务PID: $FRONTEND_PID"
    echo "访问地址: http://localhost:3000"
}

# 部署测试环境
deploy_test() {
    log_info "部署测试环境..."
    
    # 停止现有服务
    docker-compose -f docker-compose.test.yml down
    
    # 构建并启动服务
    docker-compose -f docker-compose.test.yml up -d --build
    
    # 等待服务启动
    sleep 30
    
    # 健康检查
    if curl -f http://localhost:8080/api/chat/health &> /dev/null; then
        log_success "测试环境部署成功"
        echo "访问地址: http://localhost:8081"
        echo "API文档: http://localhost:8081/api/swagger-ui.html"
        echo "监控面板: http://localhost:3000"
    else
        log_error "测试环境健康检查失败"
        exit 1
    fi
}

# 部署生产环境
deploy_prod() {
    log_info "部署生产环境..."
    
    # 检查环境变量
    if [ -z "$CHATGLM_API_KEY" ]; then
        log_error "请设置CHATGLM_API_KEY环境变量"
        exit 1
    fi
    
    # 停止现有服务
    docker-compose -f docker-compose.prod.yml down
    
    # 构建并启动服务
    docker-compose -f docker-compose.prod.yml up -d --build
    
    # 等待服务启动
    sleep 60
    
    # 健康检查
    if curl -f http://localhost/api/chat/health &> /dev/null; then
        log_success "生产环境部署成功"
        echo "访问地址: http://your-domain.com"
        echo "监控面板: http://your-domain.com:3000"
    else
        log_error "生产环境健康检查失败"
        exit 1
    fi
}

# 启动监控
start_monitor() {
    log_info "启动监控服务..."
    
    local compose_file="docker-compose.yml"
    
    if [ "$1" = "test" ]; then
        compose_file="docker-compose.test.yml"
    elif [ "$1" = "prod" ]; then
        compose_file="docker-compose.prod.yml"
    fi
    
    docker-compose -f $compose_file up -d prometheus grafana
    
    log_success "监控服务启动成功"
    echo "Prometheus: http://localhost:9090"
    echo "Grafana: http://localhost:3000 (admin/admin123)"
}

# 主函数
main() {
    local environment=""
    local build=false
    local deploy=false
    local monitor=false
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            dev|test|prod)
                environment="$1"
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            -b|--build)
                build=true
                shift
                ;;
            -d|--deploy)
                deploy=true
                shift
                ;;
            -m|--monitor)
                monitor=true
                shift
                ;;
            -a|--all)
                build=true
                deploy=true
                monitor=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 检查环境参数
    if [ -z "$environment" ]; then
        log_error "请指定部署环境 (dev|test|prod)"
        show_help
        exit 1
    fi
    
    # 检查依赖
    check_dependencies
    
    # 执行构建
    if [ "$build" = true ]; then
        case $environment in
            dev)
                build_backend
                ;;
            test|prod)
                build_backend
                build_frontend
                build_docker
                ;;
        esac
    fi
    
    # 执行部署
    if [ "$deploy" = true ]; then
        case $environment in
            dev)
                deploy_dev
                ;;
            test)
                deploy_test
                ;;
            prod)
                deploy_prod
                ;;
        esac
    fi
    
    # 启动监控
    if [ "$monitor" = true ] && [ "$environment" != "dev" ]; then
        start_monitor $environment
    fi
    
    log_success "部署完成!"
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi