#!/bin/bash

# 智能AI助手前端部署脚本
# 支持本地开发环境和生产环境部署

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
智能AI助手前端部署脚本

使用方法: $0 [环境] [选项]

环境:
    dev     开发环境
    test    测试环境
    prod    生产环境

选项:
    -h, --help      显示帮助信息
    -b, --build     构建前端项目
    -d, --deploy    部署前端项目
    -s, --serve     启动开发服务器
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

    local deps=("npm" "node")
    local missing=()

    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &> /dev/null; then
            missing+=("$dep")
        fi
    done

    if [ ${#missing[@]} -ne 0 ]; then
        log_error "缺少依赖: ${missing[*]}"
        log_info "请安装Node.js和npm: https://nodejs.org/"
        exit 1
    fi

    log_success "所有依赖检查通过"
}

# 构建前端
build_frontend() {
    log_info "构建前端项目..."

    cd frontend

    # 清理旧的构建文件
    if [ -d "dist" ]; then
        rm -rf dist
        log_info "已清理旧的构建文件"
    fi

    # 安装依赖
    log_info "安装npm依赖..."
    npm install

    # 安装terser用于压缩
    npm install terser --save-dev

    # 构建项目
    log_info "开始构建前端项目..."
    npm run build

    if [ $? -eq 0 ]; then
        log_success "前端构建成功"
        log_info "构建文件位于: $(pwd)/dist"
    else
        log_error "前端构建失败"
        exit 1
    fi

    cd ..
}

# 启动开发服务器
serve_frontend() {
    log_info "启动前端开发服务器..."

    cd frontend

    # 确保依赖已安装
    if [ ! -d "node_modules" ]; then
        log_info "安装npm依赖..."
        npm install
    fi

    # 启动开发服务器
    log_info "启动开发服务器..."
    npm run dev

    cd ..
}

# 部署到Nginx
deploy_to_nginx() {
    log_info "部署前端到Nginx..."

    # 检查Nginx是否运行
    if ! pgrep -x "nginx" > /dev/null; then
        log_warning "Nginx未运行，尝试启动..."
        sudo systemctl start nginx || {
            log_error "无法启动Nginx，请手动检查配置"
            exit 1
        }
    fi

    # 备份当前文件
    if [ -d "/usr/share/nginx/html" ]; then
        sudo mv /usr/share/nginx/html /usr/share/nginx/html.backup.$(date +%Y%m%d%H%M%S)
        log_info "已备份当前Nginx文件"
    fi

    # 复制构建文件
    sudo cp -r frontend/dist /usr/share/nginx/html
    sudo chown -R www-data:www-data /usr/share/nginx/html
    sudo chmod -R 755 /usr/share/nginx/html

    # 重新加载Nginx配置
    sudo nginx -t && sudo systemctl reload nginx

    log_success "前端部署成功"
    log_info "访问地址: http://localhost"
}

# 部署到Docker容器
deploy_to_docker() {
    local compose_file="docker-compose.yml"

    if [ "$1" = "test" ]; then
        compose_file="docker-compose.test.yml"
    elif [ "$1" = "prod" ]; then
        compose_file="docker-compose.prod.yml"
    fi

    log_info "部署前端到Docker容器..."

    # 确保前端已构建
    if [ ! -d "frontend/dist" ]; then
        log_warning "前端未构建，开始构建..."
        build_frontend
    fi

    # 停止现有Nginx容器
    docker-compose -f $compose_file stop nginx || true

    # 启动Nginx容器
    docker-compose -f $compose_file up -d nginx

    # 等待Nginx启动
    sleep 5

    # 健康检查
    local port=80
    if [ "$1" = "test" ]; then
        port=8081
    fi

    if curl -f http://localhost:$port &> /dev/null; then
        log_success "前端部署成功"
        log_info "访问地址: http://localhost:$port"
    else
        log_error "前端健康检查失败"
        exit 1
    fi
}

# 主函数
main() {
    local environment=""
    local build=false
    local deploy=false
    local serve=false

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
            -s|--serve)
                serve=true
                shift
                ;;
            -a|--all)
                build=true
                deploy=true
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
        build_frontend
    fi

    # 执行部署
    if [ "$deploy" = true ]; then
        case $environment in
            dev)
                log_warning "开发环境通常使用npm run serve，而不是部署"
                log_info "如需部署到本地Nginx，请使用: $0 dev -d"
                ;;
            test|prod)
                deploy_to_docker $environment
                ;;
        esac
    fi

    # 启动开发服务器
    if [ "$serve" = true ]; then
        if [ "$environment" = "dev" ]; then
            serve_frontend
        else
            log_warning "serve选项仅适用于开发环境"
        fi
    fi

    log_success "操作完成!"
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
