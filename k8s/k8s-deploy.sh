#!/bin/bash
set -e

echo "========================================="
echo "  DocGen Kubernetes deployment script"
echo "========================================="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 1. Check kubectl
if ! command -v kubectl &> /dev/null; then
    echo "Error: kubectl is not installed"
    echo "See https://kubernetes.io/docs/tasks/tools/"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    echo "Error: cannot connect to Kubernetes cluster"
    echo "Check your kubeconfig"
    exit 1
fi

echo "✓ kubectl is installed and can reach the cluster"

# 2. Apply manifests in order
echo ""
echo "Applying resources..."

echo "  Creating namespace..."
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"

echo "  Creating config..."
kubectl apply -f "$SCRIPT_DIR/configmap.yaml"
kubectl apply -f "$SCRIPT_DIR/secret.yaml"

echo "  Creating persistent volumes..."
kubectl apply -f "$SCRIPT_DIR/redis-pvc.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-pvc.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-pvc.yaml"

echo "  Deploying stateful backing services..."
kubectl apply -f "$SCRIPT_DIR/redis-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/redis-service.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/minio-service.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/onlyoffice-service.yaml"

echo "  Deploying application services..."
kubectl apply -f "$SCRIPT_DIR/docxtemplater-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/docxtemplater-service.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/backend-service.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-deployment.yaml"
kubectl apply -f "$SCRIPT_DIR/frontend-service.yaml"

echo "  Creating Ingress..."
kubectl apply -f "$SCRIPT_DIR/ingress.yaml"

# 3. Wait for Deployments
echo ""
echo "Waiting for Deployments..."
DEPLOYMENTS="docgen-redis docgen-minio docgen-onlyoffice docgen-docxtemplater docgen-backend docgen-frontend"

for dep in $DEPLOYMENTS; do
    echo -n "  Waiting for $dep..."
    if kubectl rollout status deployment/"$dep" -n docgen --timeout=180s 2>/dev/null; then
        echo " ✓"
    else
        echo " ✗ failed"
        echo "Pod status:"
        kubectl get pods -n docgen -l app="$dep" -o wide
        echo "Pod logs:"
        kubectl logs -n docgen -l app="$dep" --tail=30 2>/dev/null || true
        echo "Pod events:"
        kubectl describe pods -n docgen -l app="$dep" 2>/dev/null | tail -20 || true
        exit 1
    fi
done

echo ""
echo "========================================="
echo "  DocGen Kubernetes deployment succeeded"
echo "========================================="
kubectl get pods -n docgen
echo ""
kubectl get svc -n docgen
echo ""
kubectl get ingress -n docgen
echo "========================================="
