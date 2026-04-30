#!/bin/bash
set -e

echo "========================================="
echo "  DocGen local Docker deployment script"
echo "========================================="

# 1. Check Docker
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed"
    echo "See https://docs.docker.com/get-docker/"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "Error: Docker Compose is not installed"
    echo "See https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✓ Docker and Docker Compose are installed"

# 2. Check .env file
if [ ! -f .env ]; then
    echo ".env not found; copying from .env.example..."
    cp .env.example .env
    echo "⚠ Edit .env for secrets (passwords, keys), then run this script again"
    exit 0
fi

echo "✓ .env file exists"

# 3. Build images
echo ""
echo "Building Docker images..."
docker compose build || {
    echo "Error: image build failed"
    exit 1
}
echo "✓ Image build complete"

# 4. Start services
echo ""
echo "Starting services..."
docker compose up -d || {
    echo "Error: failed to start services"
    docker compose logs
    exit 1
}

# 5. Wait for health checks
echo ""
echo "Waiting for services..."
SERVICES="postgres redis minio docxtemplater app"
MAX_WAIT=180
ELAPSED=0

for svc in $SERVICES; do
    echo -n "  Waiting for $svc..."
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
        echo " ✗ timed out"
        echo "Logs for $svc:"
        docker compose logs "$svc" --tail 30
        exit 1
    fi
done

# 6. Print URLs
echo ""
echo "========================================="
echo "  DocGen deployment succeeded"
echo "========================================="
echo "  Frontend:   http://localhost"
echo "  Backend API: http://localhost:8080"
echo "  Swagger:     http://localhost/swagger-ui.html"
echo "  MinIO:       http://localhost:9001"
echo "  OnlyOffice:  https://localhost:8443"
echo "========================================="
