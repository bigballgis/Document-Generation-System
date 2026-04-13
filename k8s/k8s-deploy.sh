#!/bin/bash
set -e

echo "========================================="
echo "  DocGen Kubernetes 部署脚本"
echo "========================================="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 1. 检查 kubectl
if ! command -v kubectl &> /dev/null; then
    echo "错误: kubectl 未安装"
    echo "请访问 https://kubernetes.io/docs/tasks/tools/ 安装 kubectl"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    echo "错误: 无法连接到 Kubernetes 集群"
    echo "请检查 kubeconfig 配置"
    exit 1
fi

echo "✓ kubectl 已安装且可连接集群"

# 2. 按顺序应用资源
echo ""
echo "正在部署资源..."

echo "  创建命名空间..."
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"

echo "  创建配置..."
kubectl apply -f "$SCRIPT_DIR/configmap.yaml"
kubectl apply -f "$SCRIPT_DIR/secret.yaml"

echo "  创建持久卷..."
kubectl apply -f "$SCRIPT_DIR/redis-pvc.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-pvc.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-pvc.yaml"

echo "  部署有状态服务..."
kubectl apply -f "$SCRIPT_DIR/redis-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/redis-service.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-service.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-service.yaml"

echo "  部署应用服务..."
kubectl apply -f "$SCRIPT_DIR/docxtemplater-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/docxtemplater-service.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-service.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-service.yaml"

echo "  创建 Ingress..."
kubectl apply -f "$SCRIPT_DIR/ingress.yaml"

# 3. 等待 Deployment 就绪
echo ""
echo "等待 Deployment 就绪..."
DEPLOYMENTS="docgen-redis docgen-minio docgen-onlyoffice docgen-docxtemplater docgen-backend docgen-frontend"

for dep in $DEPLOYMENTS; do
    echo -n "  等待 $dep..."
    if kubectl rollout status deployment/"$dep" -n docgen --timeout=180s 2>/dev/null; then
        echo " ✓"
    else
        echo " ✗ 失败"
        echo "Pod 状态:"
        kubectl get pods -n docgen -l app="$dep" -o wide
        echo "Pod 日志:"
        kubectl logs -n docgen -l app="$dep" --tail=30 2>/dev/null || true
        echo "Pod 事件:"
        kubectl describe pods -n docgen -l app="$dep" 2>/dev/null | tail -20 || true
        exit 1
    fi
done

echo ""
echo "========================================="
echo "  DocGen K8s 部署成功!"
echo "========================================="
kubectl get pods -n docgen
echo ""
kubectl get svc -n docgen
echo ""
kubectl get ingress -n docgen
echo "========================================="
