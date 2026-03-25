# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

이끼잡(Job Crawler) — 채용 공고 자동 수집, AI 자소서/포트폴리오 생성, 이력서 연동, 원클릭 자동 지원을 자동화하는 풀스택 플랫폼. 4개 채용 사이트(사람인, 잡코리아, 잡플래닛, 링커리어) 지원.

## Build & Run Commands

### Backend (Spring Boot)
```bash
# 빌드
cd job-crawler && ./gradlew build

# 실행 (dev 프로필 - H2)
cd job-crawler && ./gradlew bootRun

# 실행 (prod 프로필 - PostgreSQL)
cd job-crawler && ./gradlew bootRun --args='--spring.profiles.active=prod'

# 테스트 전체
cd job-crawler && ./gradlew test

# 단일 테스트
cd job-crawler && ./gradlew test --tests "com.portfolio.jobcrawler.infrastructure.SomeTest"

# 빌드 없이 빠른 실행 (devtools 포함)
cd job-crawler && ./gradlew bootRun
```

### Frontend (Next.js)
```bash
cd job-frontend && pnpm install   # npm 아님, pnpm 사용
cd job-frontend && pnpm dev       # 개발 서버 (localhost:3000)
cd job-frontend && pnpm build     # 프로덕션 빌드
cd job-frontend && pnpm lint      # ESLint
```

### Production (AWS EC2)
```bash
# Backend: jar 빌드 후 실행
cd job-crawler && ./gradlew bootJar
java -jar build/libs/job-crawler-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Frontend: PM2로 관리
cd job-frontend && pnpm build && pm2 start npm --name "job-frontend" -- start
```

## Architecture

모노레포 구조: `job-crawler/`(백엔드) + `job-frontend/`(프론트엔드) + `browser-extension/`(크롬 확장)

### Backend 레이어 (Layered + Strategy Pattern)
```
api/           → REST Controller (Swagger 문서화, 입력 검증)
application/   → Service (비즈니스 로직, 오케스트레이션)
domain/        → Entity, Repository, VO (JPA)
infrastructure/→ 외부 시스템 연동 (크롤러, AI, 자동지원, 이력서동기화)
global/        → 공통 (Security, JWT, Config, Error, Filter)
```

### 핵심 패턴
- **Strategy Pattern**: 사이트별 크롤러 파서(`infrastructure/crawler/parser/`), 자동 지원(`infrastructure/autoapply/provider/`), 이력서 동기화(`infrastructure/resumesync/`), AI 프로필 빌더(`application/ai/profile/`) 모두 사이트별 전략 패턴
- **비동기 AI 큐**: `AiTaskQueue` → Redis 큐 + WebSocket(STOMP) 완료 알림. 프론트에서 `use-ai-task-queue.ts` 훅으로 구독
- **세션 만료 관리**: `ExternalAccount.sessionExpiresAt` → 만료 시 자동 연동 해제 + 프론트 UI 알림

### 환경변수
- `spring-dotenv` 라이브러리로 `.env` 파일 로드
- `application.properties`에서 `${ENV_VAR:default}` 형태로 참조
- `.env`는 `.gitignore`에 포함, 절대 커밋 금지
- 프로필: `dev`(H2 + 로컬), `prod`(PostgreSQL + AWS)

## Coding Rules (AGENTS.md 참조)

1. **Clean Code**: 의도 명확한 네이밍, 메서드 단일 책임, 주석 없이 이해되는 코드
2. **OOP**: 캡슐화, Tell Don't Ask (Getter/Setter 지양), 원시 타입 포장, 일급 컬렉션
3. **SOLID**: SRP (클래스 길어지면 즉시 분리), OCP (인터페이스+다형성), DIP (추상화 의존)
4. **정석적 해결**: 임시방편/꼼수/우회 금지, 공식 권장 방식 우선
5. **크롤링 파싱 로직 수정 금지**: DOM 셀렉터, 딜레이, 스텔스 설정은 봇 탐지 회피용으로 세밀 조정된 상태. 인프라 레벨(병렬처리, 배치저장, 캐싱)만 수정 가능
6. **Playwright MCP 프로세스 kill 금지**: 로그인 세션 4개 사이트 전부 날아감

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.4, Spring Security, JPA, PostgreSQL, Redis
- **Frontend**: Next.js 16, React 19, TypeScript, Tailwind CSS 4, shadcn/ui, pnpm
- **Crawling**: Playwright 1.49, JSoup 1.17
- **AI**: OpenClaw API, Tesseract OCR
- **Deploy**: AWS EC2, Nginx, PM2
