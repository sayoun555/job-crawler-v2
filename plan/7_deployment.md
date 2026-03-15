# Step 7: 배포 파이프라인 (CI/CD) 구축

## 7.1 환경 구성 개요

```
┌─────────────────────────────────────────────────────┐
│                    개발자 PC                          │
│  git push → GitHub Repository                       │
└──────────────────┬──────────────────────────────────┘
                   │ GitHub Actions 트리거
                   ▼
┌─────────────────────────────────────────────────────┐
│              GitHub Actions (CI)                     │
│  1. 코드 체크아웃                                      │
│  2. Java 21 + Node.js 설정                           │
│  3. ./gradlew build (Spring Boot)                   │
│  4. npm run build (React)                           │
│  5. 테스트 실행                                       │
│  6. SSH로 서버에 배포 스크립트 실행                      │
└──────────────────┬──────────────────────────────────┘
                   │ SSH / SCP
                   ▼
┌─────────────────────────────────────────────────────┐
│             로컬 서버 (테스트 환경)                     │
│  PM2로 Spring Boot 프로세스 관리                       │
│  Cloudflare Tunnel → job.eekky.com                  │
└─────────────────────────────────────────────────────┘
```

---

## 7.2 테스트 환경

### 구성
| 항목 | 설명 |
|------|------|
| **서버** | 로컬 개발 PC (macOS 또는 Linux) |
| **터널링** | Cloudflare Tunnel (무료) |
| **도메인** | job.eekky.com |
| **비용** | **완전 무료** |

### Cloudflare Tunnel 설정
```bash
# cloudflared 설치 (macOS)
brew install cloudflared

# 인증
cloudflared tunnel login

# 터널 생성
cloudflared tunnel create job-crawler

# 설정 파일 (~/.cloudflared/config.yml)
tunnel: <TUNNEL_ID>
credentials-file: ~/.cloudflared/<TUNNEL_ID>.json

ingress:
  - hostname: job.eekky.com
    service: http://localhost:8080
  - service: http_status:404

# DNS 설정
cloudflared tunnel route dns job-crawler job.eekky.com

# 터널 실행 (PM2로 데몬화)
pm2 start cloudflared -- tunnel run job-crawler
```

---

## 7.3 프로덕션 환경 (추후 결정)

### 후보 옵션
| 옵션 | 비용 | 스펙 | 장점 | 단점 |
|------|------|------|------|------|
| **Oracle Cloud Free Tier** | 무료 (Always Free) | ARM 4코어, 24GB RAM | 무료, 넉넉한 스펙 | 서울 리전 미제공 (도쿄 사용) |
| **AWS Lightsail** | $3.50/월~ | 512MB RAM~ | AWS 생태계, 서울 리전 | 유료 |
| **Vultr/DigitalOcean** | $4~6/월 | 1GB RAM | 가성비 | 서울 리전 없음 (도쿄) |

---

## 7.4 GitHub Actions CI/CD 파이프라인

### `.github/workflows/deploy.yml`
```yaml
name: Build and Deploy

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
      # 1. 코드 체크아웃
      - name: Checkout code
        uses: actions/checkout@v4
      
      # 2. Java 21 설정
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'
      
      # 3. Spring Boot 빌드
      - name: Build with Gradle
        run: ./gradlew build -x test
      
      # 4. Node.js 설정 (프론트엔드)
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      
      # 5. 프론트엔드 빌드
      - name: Build Frontend
        working-directory: frontend
        run: |
          npm ci
          npm run build
      
      # 6. 테스트 실행
      - name: Run Tests
        run: ./gradlew test
      
      # 7. 서버 배포
      - name: Deploy to Server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /home/deploy/job-crawler
            git pull origin main
            ./deploy.sh
```

---

## 7.5 서버 배포 스크립트 (`deploy.sh`)

```bash
#!/bin/bash
set -e

APP_NAME="job-crawler"
APP_DIR="/home/deploy/job-crawler"
JAR_FILE="$APP_DIR/build/libs/job-crawler-0.0.1-SNAPSHOT.jar"

echo "=============================="
echo " Deploying $APP_NAME"
echo "=============================="

# 1. 최신 코드 Pull
cd $APP_DIR
git pull origin main

# 2. Gradle 빌드
echo "[1/4] Building Spring Boot..."
./gradlew build -x test --no-daemon

# 3. 프론트엔드 빌드
echo "[2/4] Building Frontend..."
cd frontend
npm ci
npm run build
cd ..

# 4. PM2로 재시작 (무중단)
echo "[3/4] Restarting with PM2..."
pm2 describe $APP_NAME > /dev/null 2>&1
if [ $? -eq 0 ]; then
    pm2 restart $APP_NAME
else
    pm2 start $JAR_FILE \
        --name $APP_NAME \
        --interpreter java \
        --interpreter-args "-jar" \
        -- --spring.profiles.active=prod
fi

# 5. 상태 확인
echo "[4/4] Checking status..."
sleep 5
pm2 status $APP_NAME

echo "=============================="
echo " Deploy Complete!"
echo "=============================="
```

---

## 7.6 PM2 설정

### PM2 ecosystem 파일 (`ecosystem.config.js`)
```javascript
module.exports = {
  apps: [
    {
      name: 'job-crawler',
      script: 'java',
      args: '-jar build/libs/job-crawler-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod',
      cwd: '/home/deploy/job-crawler',
      env: {
        SPRING_PROFILES_ACTIVE: 'prod'
      },
      max_restarts: 10,
      restart_delay: 5000,
      log_date_format: 'YYYY-MM-DD HH:mm:ss',
      error_file: '/home/deploy/logs/job-crawler-error.log',
      out_file: '/home/deploy/logs/job-crawler-out.log',
      merge_logs: true
    },
    {
      name: 'cloudflare-tunnel',
      script: 'cloudflared',
      args: 'tunnel run job-crawler',
      max_restarts: 10,
      restart_delay: 3000
    }
  ]
};
```

### PM2 주요 명령어
```bash
pm2 start ecosystem.config.js      # 전체 시작
pm2 restart job-crawler             # 재시작
pm2 logs job-crawler                # 로그 확인
pm2 monit                           # 실시간 모니터링
pm2 save                            # 현재 상태 저장
pm2 startup                         # 부팅 시 자동 시작 설정
```

---

## 7.7 Spring Profiles 구성

### `application.properties` (공통)
```properties
spring.application.name=job-crawler
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

### `application-dev.properties`
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### `application-prod.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/jobcrawler
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.jpa.show-sql=false
```
