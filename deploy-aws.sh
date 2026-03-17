#!/bin/bash
# ============================================
# AWS EC2 배포 스크립트
# ============================================
set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$APP_DIR/.env"

echo "============================="
echo " 배포 시작"
echo "============================="

# 환경변수 로드
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
    echo "[ENV] 환경변수 로드 완료"
else
    echo "[ERROR] .env 파일이 없습니다. aws-setup.sh를 먼저 실행하세요."
    exit 1
fi

# 1. 최신 코드 Pull
echo "[1/5] 코드 업데이트..."
cd "$APP_DIR"
git pull origin main

# 2. Backend 빌드
echo "[2/5] Spring Boot 빌드..."
cd "$APP_DIR/job-crawler"
./gradlew build -x test --no-daemon -Dorg.gradle.jvmargs="-Xmx512m"

# 3. Frontend 빌드
echo "[3/5] Next.js 빌드..."
cd "$APP_DIR/job-frontend"
pnpm install --frozen-lockfile
NODE_OPTIONS="--max-old-space-size=512" pnpm build

# 4. PM2 재시작
echo "[4/5] PM2 재시작..."
cd "$APP_DIR"
pm2 describe job-crawler > /dev/null 2>&1
if [ $? -eq 0 ]; then
    pm2 restart ecosystem.config.js --update-env
else
    pm2 start ecosystem.config.js
fi
pm2 save

# 5. 상태 확인
echo "[5/5] 상태 확인..."
sleep 10
pm2 status

echo ""
echo "============================="
echo " 배포 완료!"
echo "============================="
