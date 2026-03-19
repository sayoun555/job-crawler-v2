# AWS 배포 가이드

AWS 프리티어 EC2 t2.micro (Ubuntu 22.04) 기준 배포 가이드.

---

## 프리티어 무료 범위

| 서비스 | 무료 범위 | 기간 |
|--------|----------|------|
| EC2 t2.micro | 월 750시간 (24시간 가동 가능) | 12개월 |
| EBS | 30GB gp2 SSD | 12개월 |
| 데이터 전송 | 월 15GB 아웃바운드 | 12개월 |

---

## 1. EC2 인스턴스 생성

AWS 콘솔 → EC2 → 인스턴스 시작

| 항목 | 설정 |
|------|------|
| AMI | Ubuntu 22.04 LTS (64-bit x86) |
| 인스턴스 유형 | t2.micro (프리티어) |
| 키 페어 | 새로 생성 → `.pem` 파일 저장 |
| 스토리지 | 30GB gp2 (프리티어 최대) |
| 보안 그룹 | 22(SSH), 80(HTTP), 443(HTTPS) 인바운드 허용 |

인스턴스 생성 후 탄력적 IP 할당 (고정 IP).

---

## 2. SSH 접속 & 초기 설정

```bash
# 키 파일 권한 설정
chmod 400 your-key.pem

# SSH 접속
ssh -i your-key.pem ubuntu@<EC2-퍼블릭-IP>

# 프로젝트 클론
git clone <your-repo-url> ~/job-crawler
cd ~/job-crawler

# 초기 설정 스크립트 실행
sudo bash aws-setup.sh
```

`aws-setup.sh`가 자동으로 설치하는 것:
- Swap 2GB (t2.micro 메모리 부족 대비)
- Java 21 (Temurin)
- Node.js 20 + pnpm + PM2
- PostgreSQL 14
- Redis
- Playwright 브라우저 의존성
- Nginx

---

## 3. 환경변수 설정

```bash
nano ~/job-crawler/.env
```

아래 값들을 **반드시 변경**:

```
DB_USERNAME=jobcrawler
DB_PASSWORD=<강력한 비밀번호>
JWT_SECRET=<32자 이상 랜덤 문자열>
ENCRYPTION_KEY=<정확히 32자 AES 키>
ADMIN_EMAIL=admin@job.com
ADMIN_PASSWORD=<강력한 비밀번호>
OPENCLAW_API_URL=http://localhost:11434/api/generate
OPENCLAW_API_MODEL=llama3
```

---

## 4. 빌드 & 배포

```bash
cd ~/job-crawler
bash deploy-aws.sh
```

이 스크립트가 하는 일:
1. `.env` 환경변수 로드
2. `git pull` 최신 코드
3. Gradle 빌드 (Spring Boot)
4. pnpm 빌드 (Next.js)
5. PM2로 프로세스 시작/재시작

---

## 5. Nginx 설정

```bash
# 설정 파일 복사
sudo cp ~/job-crawler/nginx.conf /etc/nginx/sites-available/job-crawler
sudo ln -sf /etc/nginx/sites-available/job-crawler /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 문법 검사 & 재시작
sudo nginx -t
sudo systemctl restart nginx
```

---

## 6. 도메인 & SSL

### DNS 설정
도메인 DNS 관리에서 A 레코드를 EC2 탄력적 IP로 설정.

### SSL 인증서 (Let's Encrypt)

```bash
sudo certbot --nginx -d your-domain.com
```

자동 갱신 확인:
```bash
sudo certbot renew --dry-run
```

---

## 7. PM2 자동 시작 설정

서버 재부팅 시 자동 실행:

```bash
pm2 startup
pm2 save
```

---

## 메모리 관리 (t2.micro 1GB)

### 평시 메모리 사용량

| 프로세스 | 메모리 |
|---------|--------|
| Spring Boot (JVM -Xmx384m) | ~350MB |
| Next.js (Node) | ~150MB |
| PostgreSQL | ~100MB |
| Redis | ~30MB |
| OS | ~150MB |
| **합계** | **~780MB** |

### 크롤링 시 추가

| 프로세스 | 메모리 |
|---------|--------|
| Playwright (Chromium) | ~300MB |

크롤링 시 Swap 사용. 하루 1회 크롤링이면 몇 분만 Swap 쓰고 끝남.

### Swap 설정 (aws-setup.sh에 포함)

```bash
# 2GB Swap
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# swappiness 최소화 (가능하면 RAM 우선 사용)
sudo sysctl vm.swappiness=10
```

---

## 운영 명령어

```bash
# 프로세스 상태 확인
pm2 status

# 로그 확인
pm2 logs job-crawler
pm2 logs job-frontend

# 재시작
pm2 restart all

# 배포 (코드 업데이트)
cd ~/job-crawler && bash deploy-aws.sh

# DB 접속
sudo -u postgres psql jobcrawler

# 디스크 사용량 확인
df -h

# 메모리 사용량 확인
free -h
```

---

## 문제 해결

### 서버가 느려질 때
```bash
# 메모리 확인
free -h

# Swap 사용량이 높으면 크롤링 중일 수 있음
# 크롤링 완료 후 자동 복구됨
```

### 디스크 부족 시
```bash
# 로그 정리
pm2 flush

# Gradle 캐시 정리
cd ~/job-crawler/job-crawler && ./gradlew clean
```

### PostgreSQL 접속 안 될 때
```bash
sudo systemctl status postgresql
sudo systemctl restart postgresql
```
