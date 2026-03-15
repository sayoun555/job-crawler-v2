#!/bin/bash
set -e

APP_NAME="job-crawler"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$APP_DIR/job-crawler/build/libs/job-crawler-0.0.1-SNAPSHOT.jar"

echo "=============================="
echo " Deploying $APP_NAME"
echo "=============================="

# 1. 최신 코드 Pull
cd "$APP_DIR"
git pull origin main

# 2. Gradle 빌드 (Spring Boot)
echo "[1/4] Building Spring Boot..."
cd "$APP_DIR/job-crawler"
./gradlew build -x test --no-daemon

# 3. 프론트엔드 빌드 (Next.js)
echo "[2/4] Building Frontend..."
cd "$APP_DIR/job-frontend"
npm ci
npm run build

# 4. PM2로 재시작 (무중단)
echo "[3/4] Restarting with PM2..."
cd "$APP_DIR"
pm2 describe $APP_NAME > /dev/null 2>&1
if [ $? -eq 0 ]; then
    pm2 restart ecosystem.config.js
else
    pm2 start ecosystem.config.js
fi

# 5. 상태 확인
echo "[4/4] Checking status..."
sleep 5
pm2 status

echo "=============================="
echo " Deploy Complete!"
echo "=============================="
