#!/bin/bash
set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=============================="
echo " Deploying job-crawler"
echo "=============================="

# 환경변수 로드
if [ -f "$APP_DIR/.env" ]; then
    set -a && source "$APP_DIR/.env" && set +a
fi

cd "$APP_DIR"
git pull origin main

echo "[1/4] Building Spring Boot..."
cd "$APP_DIR/job-crawler"
./gradlew build -x test --no-daemon

echo "[2/4] Building Frontend..."
cd "$APP_DIR/job-frontend"
pnpm install --frozen-lockfile
pnpm build

echo "[3/4] Restarting with PM2..."
cd "$APP_DIR"
pm2 describe job-crawler > /dev/null 2>&1 && pm2 restart ecosystem.config.js --update-env || pm2 start ecosystem.config.js
pm2 save

echo "[4/4] Checking status..."
sleep 5
pm2 status

echo "=============================="
echo " Deploy Complete!"
echo "=============================="
