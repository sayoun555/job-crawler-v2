# 이끼잡 (Job Crawler)

채용 공고 자동 수집, AI 자소서/포트폴리오 생성, 이력서 연동, 원클릭 자동 지원까지 — 취업 준비의 모든 과정을 자동화하는 풀스택 플랫폼

> **Live**: https://job.eekky.com
> **API 문서**: https://job.eekky.com/swagger-ui (개발 환경)

---

## 주요 기능

### 채용 공고 크롤링
- 사람인 · 잡플래닛 · 잡코리아 · 링커리어 4개 사이트 동시 수집
- Playwright 기반 스텔스 크롤링 (innerHTML HTML 구조 보존)
- 관리자 스케줄링 (on/off 토글, cron 설정)
- 마감일 자동 파싱 (사이트별 다른 형식 대응)
- 마감 공고 자동 비활성화 + URL 유효성 검증 (매일 자동)

### AI 자동화 (8종)
- **AI 적합률 분석** — 기술스택 60% 가중치, 매칭/부족 기술 근거 팝업
- **AI 기업 분석** — 웹 검색 + 공고 데이터 교차 분석 (모든 유저 공유 캐시)
- **자기소개서 생성** — 기본 모드 (전체 텍스트) + 커스텀 모드 (문항별 JSON)
- **포트폴리오 생성** — 기본 + 커스텀 모드 (문항별 JSON)
- **GitHub 프로젝트 AI 분석** — 소스 코드, README, docs/ 기술 블로그 자동 분석
- **Notion 페이지 크롤링** — 공개 페이지 텍스트 + 이미지 추출, 멀티모달 AI 전달
- **합격 자소서 패턴 분석** — 구조, 패턴, 키워드, 강점 분석 + 템플릿 저장 (모든 유저 공유)
- **OCR 이미지 텍스트 추출** — Tesseract 기반 한글 인식 (85~90%)

### 커스텀 자소서/포트폴리오
- 문항별 AI 생성 (제목 + 규칙 입력 → AI가 문항별 맞춤 작성)
- 대기업 프리셋 9종 (삼성, 현대, SK하이닉스, LG, 카카오, 포스코, 한화, CJ, 공기업)
- 프로젝트 여러 개일 때 문항별 적합한 프로젝트 자동 분배
- 포트폴리오 항목 체크박스 선택/제외 UI
- PDF 출력 (섹션 번호, 구분선, 【소제목】 강조, ▶/✔ 불릿)

### 이력서 관리 & 사이트 연동
- 이력서 CRUD (학력 · 경력 · 스킬 · 자격증 · 어학 · 활동 · 포트폴리오 링크 · 희망조건)
- 사이트별 이력서 관리 (사람인/잡코리아/잡플래닛/링커리어 탭)
- 사이트별 이력서 Import (4개 사이트 → 내 이력서로 가져오기)
- 사이트별 이력서 편집 (Import된 이력서도 수정 가능)
- 이력서 → 외부 사이트 동기화 (내 이력서를 사이트에 반영)

### 자동 지원
- 지원 전 자기소개서 자동 매핑: 이력서 자기소개서 섹션을 커스텀 자소서로 업데이트 후 지원
  - 사람인/잡코리아/링커리어: 문항별 1:1 매핑 (제목 + 내용)
  - 잡플래닛: 전체 합쳐서 단일 textarea
- 일회용 로그인 (비밀번호 서버 미저장) / Chrome 확장 프로그램 (소셜 로그인 지원)
- 지원 결과 자동 검증 + 사후검증 스케줄러

### 알림 시스템
- 유저별 희망 직무 매칭 → 디스코드 웹훅 알림
- 알림 시간대 설정 + 중복 알림 방지

### 모니터링 & API 문서
- **Spring Boot Actuator** — 서버 상태, JVM 메트릭, DB 커넥션 모니터링
- **Prometheus 메트릭** — Grafana 연동 가능
- **Swagger/OpenAPI 3.0** — API 108개 전체 문서화 (한국어 설명, 파라미터 예시)
- 운영 환경에서는 Swagger 비활성화, Actuator 최소 노출 (ADMIN만)

### 관리자 대시보드
- 크롤링 스케줄 관리 (on/off, cron, 최대 페이지)
- 공고 관리 (사이트별/전체/선택 삭제)
- 만료 공고 URL 검증 (수동 실행)
- 합격 자소서 크롤링 / AI 프리셋 양식 갱신
- 알림 테스트

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA |
| **Frontend** | Next.js 16, React 19, TypeScript, Tailwind CSS 4, shadcn/ui |
| **Database** | PostgreSQL 14, Redis |
| **크롤링** | Playwright 1.49, JSoup 1.17 |
| **인증/보안** | JWT (자동 갱신), AES-256-GCM 암호화, BCrypt |
| **AI** | OpenClaw API (GPT 5.4 Codex), Tesseract OCR, 멀티모달 (텍스트+이미지) |
| **모니터링** | Spring Boot Actuator, Micrometer, Prometheus |
| **API 문서** | SpringDoc OpenAPI 3.0 (Swagger UI) |
| **배포** | AWS EC2, Nginx, PM2, Let's Encrypt |

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
├── api/                            # REST Controller (Swagger 문서화)
│   ├── auth/                       #   인증 (회원가입, 로그인, 토큰 갱신)
│   ├── account/                    #   외부 사이트 계정 연동
│   ├── crawler/                    #   크롤링 & 스케줄 & URL검증 (ADMIN)
│   ├── jobposting/                 #   채용 공고 조회
│   ├── jobapply/                   #   자동 지원 (기본 + 커스텀 자소서)
│   ├── resume/                     #   이력서 CRUD & 사이트별 관리 & 동기화
│   ├── coverletter/                #   합격 자소서 & AI 패턴 분석
│   ├── ai/                         #   AI 분석/생성 (8종, 비동기 큐)
│   ├── jobpreference/              #   희망 직무 알림 설정
│   ├── project/                    #   프로젝트 & GitHub/Notion 분석
│   ├── template/                   #   자소서/포트폴리오 템플릿
│   └── user/                       #   유저 프로필 & 설정
│
├── application/                    # Service (비즈니스 로직)
│   ├── ai/                         #   AI 오케스트레이션
│   │   ├── AiAutomationServiceImpl #     AI 8종 기능 통합
│   │   ├── AiPromptDataBuilder     #     프로필/공고 프롬프트 조립
│   │   ├── AiTaskQueue             #     비동기 AI 작업 큐 (Redis + WebSocket)
│   │   └── profile/                #     사이트별 프로필 전략 (4개 구현체)
│   ├── crawler/                    #   크롤링 오케스트레이션 & 스케줄러
│   ├── notification/               #   알림 발송 (직무 매칭 + 중복 방지)
│   └── ...
│
├── domain/                         # 도메인 모델 (Entity, Repository, VO)
│   ├── jobposting/                 #   채용 공고
│   ├── jobapply/                   #   지원서 (커스텀 자소서/포트폴리오 섹션)
│   ├── resume/                     #   이력서 + 7개 하위 엔티티 (사이트별 관리)
│   ├── aianalysis/                 #   AI 분석 결과 캐시 (적합률, 기업분석, 패턴)
│   ├── account/                    #   외부 계정 (세션 만료 관리)
│   └── ...
│
├── infrastructure/                 # 외부 시스템 연동
│   ├── crawler/                    #   Playwright 크롤러
│   │   └── parser/                 #     사이트별 DOM 파서 (Strategy Pattern)
│   ├── autoapply/                  #   자동 지원 로봇
│   │   ├── provider/               #     사이트별 지원 전략 (4개 구현체)
│   │   └── CoverLetterFiller       #     다중 textarea 자소서 매핑
│   ├── resumesync/                 #   이력서 동기화 & Import (4개 사이트)
│   ├── ai/                         #   OpenClaw AI 연동 (멀티모달)
│   ├── notion/                     #   Notion 페이지 크롤러 (텍스트+이미지)
│   ├── github/                     #   GitHub 레포 분석기
│   └── notification/               #   디스코드 웹훅 발송
│
└── global/                         # 공통 인프라
    ├── auth/                       #   JWT 필터, 토큰 프로바이더
    ├── config/                     #   Security, Swagger, JPA, CORS 설정
    ├── error/                      #   에러 코드 & 글로벌 예외 처리
    └── filter/                     #   Rate Limit 필터 (IP 기반)
```

### Frontend

```
job-frontend/src/
├── app/                            # Next.js App Router (페이지)
│   ├── (홈) /                      #   채용 공고 리스트 + 사이트별 필터
│   ├── jobs/[id]/                  #   공고 상세 + AI 분석 + 커스텀 자소서/포트폴리오
│   ├── resume/                     #   이력서 관리 (마스터 + 사이트별 탭 편집)
│   ├── applications/               #   지원 이력 + 미리보기 + PDF 출력
│   ├── cover-letters/              #   합격 자소서 목록 + AI 패턴 분석
│   ├── templates/                  #   자소서 템플릿 + 대기업 프리셋 + 수정 기능
│   ├── projects/                   #   프로젝트 + GitHub/Notion AI 분석
│   ├── settings/                   #   알림 설정 + 사이트 계정 연동
│   ├── admin/                      #   관리자 대시보드
│   └── login/ · signup/            #   인증
│
├── components/                     # 재사용 컴포넌트
│   ├── resume/                     #   이력서 섹션별 11개 컴포넌트 (사이트별 편집 지원)
│   ├── ai-task-indicator.tsx       #   AI 작업 진행률/알림 컴포넌트
│   └── job-card.tsx                #   공고 카드 (적합률 색상 분기)
│
└── lib/                            # 유틸리티
    ├── api.ts                      #   API 클라이언트 (자동 토큰 갱신)
    ├── auth-context.tsx            #   인증 컨텍스트
    ├── websocket.ts                #   STOMP WebSocket (AI 완료 알림)
    └── use-ai-task-queue.ts        #   AI 비동기 작업 큐 훅
```

---

## 인프라 구성

```
[사용자] → [Nginx :443] → [Next.js :3000] (프론트엔드)
                        → [Spring Boot :8080] (API)
                              ├── [PostgreSQL :5432]
                              ├── [Redis :6379]
                              ├── [Playwright] (크롤링/자동지원/이력서동기화)
                              └── [OpenClaw AI API] (자소서/포트폴리오/분석)
```

---

## 지원 채용 사이트

| 사이트 | 크롤링 | 이력서 동기화 | 이력서 Import | 자동 지원 | 자소서 매핑 |
|--------|--------|-------------|-------------|----------|------------|
| 사람인 | O | O | O | O | 문항별 1:1 |
| 잡코리아 | O | O | O | O | 문항별 1:1 |
| 잡플래닛 | O | O | O | O | 전체 합침 |
| 링커리어 | O | O | O | O | 문항별 1:1 |

