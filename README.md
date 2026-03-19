# 이끼잡 (Job Crawler)

채용 공고 자동 수집, AI 자소서 생성, 이력서 연동, 원클릭 자동 지원까지 — 취업 준비의 모든 과정을 자동화하는 풀스택 플랫폼

> **Live**: https://job.eekky.com

---

## 주요 기능

### 채용 공고 크롤링
- 사람인 · 잡플래닛 · 잡코리아 · 링커리어 4개 사이트 동시 수집
- Playwright 기반 스텔스 크롤링 (innerHTML HTML 구조 보존)
- 관리자 스케줄링 (on/off 토글, cron 설정)
- 마감일 자동 파싱 (사이트별 다른 형식 대응)
- 마감 공고 자동 비활성화 (매시간)
- 채용시 마감 공고 URL 유효성 검증 (매일 자동 실행)

### AI 자동화
- AI 적합률 분석 (기술스택 60% 가중치) + 근거 팝업 (매칭/부족 기술 표시)
- AI 기업 분석 (웹 검색 + 공고 데이터 교차 분석, 모든 유저 공유)
- 맞춤형 자기소개서 자동 생성 (템플릿 선택 가능)
- 포트폴리오 자동 생성
- GitHub 프로젝트 AI 분석
- 합격 자소서 패턴 분석 + 템플릿 저장
- 대기업 자소서 프리셋 양식 (AI 갱신 지원)
- OCR 이미지 텍스트 추출 (Tesseract)

### 이력서 관리 & 사이트 연동
- 이력서 CRUD (학력 · 경력 · 스킬 · 자격 · 어학 · 활동 · 포트폴리오)
- 사람인 이력서 가져오기
- 이력서 → 외부 사이트 자동 동기화

### 자동 지원
- 우리 사이트에서 "지원" → 채용 사이트에 실제 지원까지 자동 처리
- 일회용 로그인 (비밀번호 서버 미저장)
- Chrome 확장 프로그램 (소셜 로그인 지원)

### 알림 시스템
- 유저별 희망 직무 매칭 → 디스코드 웹훅 알림
- 알림 시간대 설정
- 중복 알림 방지 (NotificationHistory)

### 관리자 대시보드
- 크롤링 스케줄 관리 (on/off, cron, 최대 페이지)
- 공고 관리 (사이트별/전체/선택 삭제)
- 만료 공고 URL 검증 (수동 실행)
- 닫힌 공고 히스토리 조회
- 합격 자소서 크롤링
- AI 프리셋 양식 갱신
- 알림 테스트

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA |
| **Frontend** | Next.js 16, React 19, TypeScript, Tailwind CSS 4, shadcn/ui |
| **Database** | PostgreSQL 14, Redis |
| **크롤링** | Playwright 1.49, JSoup 1.17 |
| **인증** | JWT (자동 갱신), AES-256 암호화 |
| **AI** | OpenClaw API (GPT 5.4 Codex), Tesseract OCR |
| **배포** | AWS EC2, Nginx, PM2, Let's Encrypt |

---

## 설계 원칙

- **Clean Code**: 의도가 명확한 메서드명, 단일 책임 메서드
- **OOP**: Tell Don't Ask, 원시 타입 포장, 일급 컬렉션
- **SOLID**: SRP (Converter/PromptBuilder 분리), OCP (Strategy Pattern), DIP (인터페이스 의존)

---

## 프로젝트 구조

```
job-crawler/
├── job-crawler/                    # Backend (Spring Boot)
├── job-frontend/                   # Frontend (Next.js)
├── browser-extension/              # Chrome 확장 프로그램
└── docs/                           # 기술 블로그 & 아키텍처 문서
```

### Backend

```
com.portfolio.jobcrawler/
│
├── api/                            # REST Controller
│   ├── auth/                       #   인증 (회원가입, 로그인, 토큰 갱신)
│   ├── account/                    #   외부 사이트 계정 연동
│   ├── crawler/                    #   크롤링 & 스케줄 & URL검증 (ADMIN)
│   ├── jobposting/                 #   채용 공고 조회
│   ├── jobapply/                   #   자동 지원
│   ├── resume/                     #   이력서 CRUD & 사이트 연동
│   ├── coverletter/                #   자기소개서
│   ├── ai/                         #   AI 분석/생성 (적합률 근거 포함)
│   ├── jobpreference/              #   희망 직무 설정
│   ├── project/                    #   포트폴리오 프로젝트
│   ├── template/                   #   템플릿 관리
│   └── user/                       #   유저 프로필 & 설정
│
├── application/                    # Service (비즈니스 로직)
│   ├── auth/                       #   JWT 발급, 회원 인증
│   ├── account/                    #   외부 계정 + 일회용 로그인
│   ├── crawler/                    #   크롤링 오케스트레이션 & 스케줄러
│   │   ├── CrawlerServiceImpl      #     크롤링 + 배치 저장
│   │   ├── CrawlerScheduler        #     스케줄 관리 (마감정리/알림/URL검증)
│   │   └── PostingUrlValidator     #     만료 공고 URL 유효성 검증
│   ├── notification/               #   알림 발송 (직무 매칭 + 중복 방지)
│   ├── ai/                         #   AI 자동화 서비스
│   │   ├── AiAutomationServiceImpl #     AI 오케스트레이션
│   │   └── AiPromptDataBuilder     #     프로필/공고 프롬프트 조립
│   └── ...
│
├── domain/                         # 도메인 모델 (Entity, Repository, VO)
│   ├── jobposting/                 #   채용 공고
│   ├── resume/                     #   이력서 + 7개 하위 엔티티
│   ├── aianalysis/                 #   AI 분석 결과 (적합률 근거 + 기업 분석)
│   ├── account/                    #   외부 계정 (세션 쿠키 포함)
│   ├── notification/               #   알림 이력
│   ├── scheduler/                  #   스케줄러 설정 (DB 영속)
│   ├── user/                       #   유저 & 프로필
│   └── ...
│
├── infrastructure/                 # 외부 시스템 연동
│   ├── crawler/                    #   Playwright 크롤러
│   │   ├── parser/                 #     사이트별 DOM 파서 (Strategy)
│   │   └── CrawledJobDataConverter #     DTO → Entity 변환
│   ├── autoapply/                  #   자동 지원 로봇 (사이트별 전략)
│   ├── resumesync/                 #   이력서 동기화 & 가져오기
│   ├── ai/                         #   OpenClaw AI 연동
│   └── notification/               #   디스코드 웹훅 발송
│
└── global/                         # 공통 인프라
    ├── auth/                       #   JWT 필터, 토큰 프로바이더
    ├── config/                     #   Security, JPA, CORS 설정
    ├── error/                      #   에러 코드 & 예외 처리
    ├── util/                       #   HtmlSanitizer, ImageOcrUtil
    └── filter/                     #   Rate Limit 필터
```

### Frontend

```
job-frontend/src/
├── app/                            # Next.js App Router (페이지)
│   ├── (홈) /                      #   채용 공고 리스트 + 사이트별 필터
│   ├── jobs/[id]/                  #   공고 상세 + AI 분석 + 적합률 근거 팝업
│   ├── resume/                     #   이력서 관리 + 사이트 연동
│   ├── applications/               #   지원 이력 관리
│   ├── cover-letters/              #   합격 자소서 목록 + AI 패턴 분석
│   ├── templates/                  #   자소서 템플릿 + 프리셋
│   ├── projects/                   #   포트폴리오 프로젝트 + GitHub AI 분석
│   ├── settings/                   #   알림 설정 + 사이트 계정 연동
│   ├── dashboard/                  #   내 관리 대시보드
│   ├── admin/                      #   관리자 (스케줄/삭제/URL검증/히스토리)
│   └── login/ · signup/            #   인증
│
├── components/                     # 재사용 컴포넌트
│   ├── resume/                     #   이력서 섹션별 11개 컴포넌트
│   ├── filter-panel.tsx            #   사이트별 동적 필터 패널
│   ├── ui/                         #   shadcn/ui
│   └── ...
│
└── lib/                            # 유틸리티
    ├── api.ts                      #   API 클라이언트 (자동 토큰 갱신)
    └── auth-context.tsx            #   인증 컨텍스트
```

---

## 인프라 구성

```
[사용자] → [Nginx :443] → [Next.js :3000] (프론트엔드)
                        → [Spring Boot :8080] (API)
                              ↓
                        [PostgreSQL :5432]
                        [Redis :6379]
                        [Playwright] (크롤링/자동지원)
```

---

## 지원 채용 사이트

| 사이트 | 크롤링 | 이력서 동기화 | 자동 지원 | 이력서 가져오기 |
|--------|--------|-------------|----------|---------------|
| 사람인 | O | O | O | O |
| 잡플래닛 | O | - | O | - |
| 잡코리아 | O | - | O | - |
| 링커리어 | O | - | - | - |
