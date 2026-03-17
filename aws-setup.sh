#!/bin/bash
# ============================================
# AWS EC2 프리티어 (t2.micro) 초기 서버 세팅
# Ubuntu 22.04 LTS 기준
# ============================================
set -e

echo "============================="
echo " AWS EC2 서버 초기 설정 시작"
echo "============================="

# 1. Swap 메모리 추가 (t2.micro 1GB → +2GB swap)
echo "[1/8] Swap 메모리 설정 (2GB)..."
if [ ! -f /swapfile ]; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    # swap 사용 최적화
    sudo sysctl vm.swappiness=10
    echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
    echo "  Swap 설정 완료"
else
    echo "  Swap 이미 존재"
fi

# 2. 시스템 패키지 업데이트
echo "[2/8] 시스템 패키지 업데이트..."
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git nginx certbot python3-certbot-nginx unzip

# 3. Java 21 설치
echo "[3/8] Java 21 (Temurin) 설치..."
if ! java -version 2>&1 | grep -q "21"; then
    wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /usr/share/keyrings/adoptium.asc
    echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
    sudo apt update
    sudo apt install -y temurin-21-jdk
    echo "  Java 설치 완료: $(java -version 2>&1 | head -1)"
else
    echo "  Java 21 이미 설치됨"
fi

# 4. Node.js 20 + pnpm 설치
echo "[4/8] Node.js 20 + pnpm 설치..."
if ! node -v 2>&1 | grep -q "v20"; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt install -y nodejs
    sudo npm install -g pnpm pm2
    echo "  Node.js 설치 완료: $(node -v)"
else
    echo "  Node.js 이미 설치됨"
fi

# 5. PostgreSQL 14 설치
echo "[5/8] PostgreSQL 설치..."
if ! systemctl is-active --quiet postgresql; then
    sudo apt install -y postgresql postgresql-contrib
    sudo systemctl enable postgresql
    sudo systemctl start postgresql
    # DB & 유저 생성
    sudo -u postgres psql -c "CREATE USER jobcrawler WITH PASSWORD 'jobcrawler_pw_change_me';" 2>/dev/null || true
    sudo -u postgres psql -c "CREATE DATABASE jobcrawler OWNER jobcrawler;" 2>/dev/null || true
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE jobcrawler TO jobcrawler;" 2>/dev/null || true
    echo "  PostgreSQL 설치 완료"
else
    echo "  PostgreSQL 이미 실행 중"
fi

# 6. Redis 설치
echo "[6/8] Redis 설치..."
if ! systemctl is-active --quiet redis-server; then
    sudo apt install -y redis-server
    sudo systemctl enable redis-server
    sudo systemctl start redis-server
    echo "  Redis 설치 완료"
else
    echo "  Redis 이미 실행 중"
fi

# 7. Playwright 브라우저 의존성 설치
echo "[7/8] Playwright 브라우저 의존성 설치..."
sudo apt install -y libatk1.0-0 libatk-bridge2.0-0 libcups2 libxkbcommon0 \
    libxcomposite1 libxdamage1 libxrandr2 libgbm1 libpango-1.0-0 \
    libcairo2 libasound2 libnspr4 libnss3 libxshmfence1 fonts-noto-cjk
echo "  Playwright 의존성 설치 완료"

# 8. 프로젝트 디렉토리 & 환경변수 설정
echo "[8/8] 환경변수 파일 생성..."
APP_DIR="/home/ubuntu/job-crawler"
ENV_FILE="$APP_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    cat > "$ENV_FILE" << 'ENVEOF'
# ============================================
# 반드시 아래 값들을 변경하세요!
# ============================================
DB_USERNAME=jobcrawler
DB_PASSWORD=jobcrawler_pw_change_me
JWT_SECRET=your-jwt-secret-key-must-be-at-least-256-bits-long-change-this!!
ENCRYPTION_KEY=your-aes-256-key-must-be-32chars!
ADMIN_EMAIL=admin@job.com
ADMIN_PASSWORD=admin1234
OPENCLAW_API_URL=http://localhost:11434/api/generate
OPENCLAW_API_MODEL=llama3
ENVEOF
    echo "  .env 파일 생성 완료 — 반드시 값을 변경하세요!"
else
    echo "  .env 파일 이미 존재"
fi

echo ""
echo "============================="
echo " 초기 설정 완료!"
echo "============================="
echo ""
echo "다음 단계:"
echo "  1. .env 파일의 비밀번호/키 변경: nano $ENV_FILE"
echo "  2. 프로젝트 클론: git clone <repo-url> $APP_DIR"
echo "  3. 배포 실행: cd $APP_DIR && bash deploy-aws.sh"
echo "  4. Nginx SSL 설정: sudo certbot --nginx -d your-domain.com"
