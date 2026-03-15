# Step 1: 요구사항 정의 및 기술 스택 선정

## 1.1 타겟 취업 사이트

| 사이트 | URL | 특징 | 크롤링 난이도 |
|--------|-----|------|--------------|
| **사람인 (Saramin)** | saramin.co.kr | 국내 대형 취업 포털, 즉시지원 기능 | ⚠️ Incapsula 봇 차단 |
| **잡플래닛 (JobPlanet)** | jobplanet.co.kr | 기업 리뷰 + 채용 공고 | ⚠️ Cloudflare 차단 가능 |

---

## 1.2 확정 기술 스택

### Backend
| 항목 | 선택 | 버전 | 선정 사유 |
|------|------|------|----------|
| **언어** | Java | 21 (LTS) | 국내 취업 시장 최적, 기존 프로젝트 경험 활용 |
| **프레임워크** | Spring Boot | 3.4.3 | DDD 아키텍처 적용, 엔터프라이즈급 안정성 |
| **아키텍처 패턴** | Domain-Driven Design (DDD) | - | Service는 흐름만 제어, 핵심 비즈니스 로직은 Domain 객체에 위임 |
| **인증** | JWT (jjwt 0.12.6) | 토큰 만료 24h | 다중 사용자 무상태 인증 |
| **Validation** | spring-boot-starter-validation | - | 입력값 검증 |

### Crawler
| 항목 | 선택 | 선정 사유 |
|------|------|----------|
| **엔진** | Playwright Java (1.49.0) | 동적 DOM 파싱, 헤드리스 크로미움 제어, 로그인 자동화 지원 |
| **전략** | Stealth + Random Delay + UA 변경 | Anti-Bot 우회 |

### Database & Cache
| 항목 | 선택 | 용도 |
|------|------|------|
| **RDB** | PostgreSQL (JPA) | 공고/사용자/프로젝트 등 영속 데이터 |
| **Cache** | Redis | 세션 캐싱, 중복 공고 체크, API 응답 최적화 |
| **개발용 DB** | H2 (In-Memory) | 로컬 개발 시 빠른 테스트용 |

### Frontend
| 항목 | 선택 | 선정 사유 |
|------|------|----------|
| **프레임워크** | React | SPA 구현, 컴포넌트 기반 아키텍처 |
| **UI 라이브러리** | shadcn/ui | Tailwind CSS 기반, 모던 디자인 시스템 |
| **형태** | 반응형 웹앱 (PWA 고려) | 모바일/데스크탑 모두 지원 |

### AI & 자동화
| 항목 | 선택 | 용도 |
|------|------|------|
| **AI API** | OpenClaw HTTP API | 맞춤형 자소서/포트폴리오 텍스트 생성 |
| **템플릿 시스템** | Plain Text/HTML 기반 (마크다운 금지) | 다중 템플릿 저장 및 선택 |

### DevOps
| 항목 | 선택 | 비용 |
|------|------|------|
| **테스트 환경** | 로컬 서버 + Cloudflare Tunnel | 무료 (job.eekky.com) |
| **CI/CD** | GitHub Actions | 자동 빌드/배포 |
| **프로세스 관리** | PM2 | 무중단 운영 |
| **프로덕션** | 추후 결정 (Oracle Cloud Free Tier / AWS Lightsail) | - |

---

## 1.3 프로젝트 구조 (DDD 기반 패키지 구조)

```
com.portfolio.jobcrawler/
├── JobCrawlerApplication.java
│
├── global/                          # 전역 설정 및 공통 모듈
│   ├── config/                      # Spring 설정 (Security, Redis, CORS 등)
│   ├── error/                       # 글로벌 예외 처리 (ErrorCode, CustomException)
│   ├── auth/                        # JWT 필터, 인증/인가 처리
│   └── util/                        # 공통 유틸리티
│
├── domain/                          # 도메인 계층 (핵심 비즈니스 로직)
│   ├── user/                        # 사용자 도메인
│   │   ├── entity/                  # User, UserProfile, ExternalAccount
│   │   ├── repository/              # UserRepository
│   │   └── service/                 # UserDomainService (도메인 로직)
│   │
│   ├── jobposting/                  # 채용공고 도메인
│   │   ├── entity/                  # JobPosting, enum (ApplicationMethod, SourceSite 등)
│   │   ├── repository/              # JobPostingRepository
│   │   └── service/                 # JobPostingDomainService
│   │
│   ├── project/                     # 프로젝트 도메인 (내 포트폴리오 프로젝트)
│   │   ├── entity/                  # Project
│   │   ├── repository/              # ProjectRepository
│   │   └── service/                 # ProjectDomainService
│   │
│   ├── template/                    # 템플릿 도메인
│   │   ├── entity/                  # Template
│   │   ├── repository/              # TemplateRepository
│   │   └── service/                 # TemplateDomainService
│   │
│   └── application/                 # 입사 지원 도메인
│       ├── entity/                  # Application (지원 이력)
│       ├── repository/              # ApplicationRepository
│       └── service/                 # ApplicationDomainService
│
├── application/                     # 어플리케이션 서비스 계층 (유즈케이스 조합)
│   ├── auth/                        # AuthService (회원가입, 로그인)
│   ├── crawler/                     # CrawlerApplicationService (크롤링 오케스트레이션)
│   ├── ai/                          # AiApplicationService (OpenClaw 연동)
│   ├── autoapply/                   # AutoApplyService (자동 지원)
│   └── notification/                # NotificationService (디스코드 알림)
│
├── infrastructure/                  # 인프라 계층 (외부 시스템 연동)
│   ├── crawler/                     # Playwright 기반 크롤러 구현체
│   │   ├── PlaywrightManager.java
│   │   ├── SaraminScraper.java
│   │   └── JobPlanetScraper.java
│   ├── ai/                          # OpenClaw HTTP API 클라이언트
│   ├── discord/                     # Discord Webhook 클라이언트
│   └── redis/                       # Redis 설정 및 레포지토리
│
└── api/                             # API 계층 (Controller, DTO)
    ├── auth/                        # 인증 관련 API
    ├── user/                        # 사용자 관련 API
    ├── jobposting/                  # 채용공고 관련 API
    ├── project/                     # 프로젝트 관련 API
    ├── template/                    # 템플릿 관련 API
    ├── crawler/                     # 크롤링 관련 API
    └── application/                 # 지원 관련 API
```

---

## 1.4 핵심 도메인 엔티티 설계

### User (사용자)
```
User: id, email, password, nickname, discordWebhookUrl, notificationEnabled
UserProfile: id, userId, education, career, certifications, skills, strengths
ExternalAccount: id, userId, siteName(SARAMIN/JOBPLANET), loginId, encryptedPassword
```

### JobPosting (채용공고)
```
JobPosting: id, title, company, location, url, description, source(SARAMIN/JOBPLANET),
            applicationMethod(DIRECT_APPLY/HOMEPAGE/EMAIL/UNKNOWN),
            education, career, salary, deadline, techStack(JSON), requirements(JSON),
            companyImages(JSON), aiMatchScore, crawledAt
```

### Project (내 프로젝트)
```
Project: id, userId, name, description, githubUrl, notionUrl, 
         techStack(JSON), images(JSON), aiSummary
```

### Template (템플릿)
```
Template: id, userId, name, type(COVER_LETTER/PORTFOLIO), content(Plain Text/HTML)
```

### Application (지원 이력)
```
Application: id, userId, jobPostingId, status(PENDING/APPLIED/FAILED/MANUAL_APPLIED),
             coverLetter, portfolioContent, templateId, appliedAt, failReason
```
