#!/bin/bash
set -e

echo "========================================="
echo "  DocGen 本地 Docker 部署脚本"
echo "========================================="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装"
    echo "请访问 https://docs.docker.com/get-docker/ 安装 Docker"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "错误: Docker Compose 未安装"
    echo "请访问 https://docs.docker.com/compose/install/ 安装 Docker Compose"
    exit 1
fi

echo "✓ Docker 和 Docker Compose 已安装"

# 2. 检查 .env 文件
if [ ! -f .env ]; then
    echo "未找到 .env 文件，从 .env.example 复制..."
    cp .env.example .env
    echo "⚠ 请编辑 .env 文件，修改敏感配置（密码、密钥等），然后重新运行此脚本"
    exit 0
fi

echo "✓ .env 文件已存在"

# 3. 构建镜像
echo ""
echo "正在构建 Docker 镜像..."
docker compose build || {
    echo "错误: 镜像构建失败"
    exit 1
}
echo "✓ 镜像构建完成"

# 4. 启动服务
echo ""
echo "正在启动服务..."
docker compose up -d || {
    echo "错误: 服务启动失败"
    docker compose logs
    exit 1
}

# 5. 等待健康检查
echo ""
echo "等待服务启动..."
SERVICES="postgres redis minio docxtemplater app"
MAX_WAIT=180
ELAPSED=0

for svc in $SERVICES; do
    echo -n "  等待 $svc..."
    while [ $ELAPSED -lt $MAX_WAIT ]; do
        STATUS=$(docker compose ps --format json "$svc" 2>/dev/null | grep -o '"Health":"[^"]*"' | head -1 || echo "")
        if echo "$STATUS" | grep -q "healthy"; then
            echo " ✓"
            break
        fi
        sleep 5
        ELAPSED=$((ELAPSED + 5))
        echo -n "."
    done
    if [ $ELAPSED -ge $MAX_WAIT ]; then
        echo " ✗ 超时"
        echo "服务 $svc 日志:"
        docker compose logs "$svc" --tail 30
        exit 1
    fi
done

# 6. 输出访问地址
echo ""
echo "========================================="
echo "  DocGen 部署成功!"
echo "========================================="
echo "  前端:     http://localhost"
echo "  后端 API: http://localhost:8080"
echo "  Swagger:  http://localhost/swagger-ui.html"
echo "  MinIO:    http://localhost:9001"
echo "  OnlyOffice: https://localhost:8443"
echo "========================================="
