# 아키텍처 & 설계 문서

## 목차
1. [아키텍처 개요](#아키텍처-개요)
2. [레이어 구조](#레이어-구조)
3. [설계 원칙](#설계-원칙)
4. [디자인 패턴](#디자인-패턴)
5. [도메인 모델](#도메인-모델)
6. [인증 & 보안](#인증--보안)
7. [크롤링 아키텍처](#크롤링-아키텍처)
8. [자동 지원 아키텍처](#자동-지원-아키텍처)
9. [이력서 연동 아키텍처](#이력서-연동-아키텍처)
10. [알림 시스템](#알림-시스템)
11. [세션 관리](#세션-관리)
12. [스케줄러](#스케줄러)

---

## 아키텍처 개요

### Clean Architecture (레이어드 아키텍처)

```
┌─────────────────────────────────────────────────┐
│  API (Controller)                               │  ← HTTP 요청/응답
├─────────────────────────────────────────────────┤
│  Application (Service)                          │  ← 비즈니스 로직, 트랜잭션
├─────────────────────────────────────────────────┤
│  Domain (Entity, Repository, VO)                │  ← 핵심 도메인 모델
├─────────────────────────────────────────────────┤
│  Infrastructure (외부 연동)                       │  ← Playwright, AI, 알림
├─────────────────────────────────────────────────┤
│  Global (공통)                                   │  ← 인증, 설정, 에러 처리
└─────────────────────────────────────────────────┘
```

**의존성 방향**: API → Application → Domain ← Infrastructure

Domain은 가장 안쪽 레이어로, 외부 의존성이 없다. Infrastructure가 Domain의 인터페이스를 구현하는 형태 (DIP).

### 인프라 구성

```
[클라이언트]
    ↓ HTTPS
[Nginx :443]
    ├── / → [Next.js :3000] (SSR, 정적 자산)
    └── /api/ → [Spring Boot :8080] (REST API)
                    ├── [PostgreSQL :5432] (영속 데이터)
                    ├── [Redis :6379] (세션 캐시)
                    └── [Playwright] (크롤링, 자동지원)
```

---

## 레이어 구조

### API Layer (`api/`)

- REST Controller만 위치
- 요청 유효성 검사, DTO 변환
- 인증/인가 처리 (Spring Security)
- Application Layer에 위임

### Application Layer (`application/`)

- Service 인터페이스 + 구현체
- 트랜잭션 경계 (`@Transactional`)
- 도메인 객체 조합 및 비즈니스 로직
- Infrastructure 호출 조율

### Domain Layer (`domain/`)

- Entity: JPA 엔티티, 비즈니스 로직 캡슐화 ("Tell, Don't Ask")
- Repository: Spring Data JPA 인터페이스
- VO (Value Object): enum, 불변 객체

### Infrastructure Layer (`infrastructure/`)

- 외부 시스템과의 연동 구현
- Playwright 크롤러, AI API 클라이언트, 디스코드 웹훅
- Domain의 인터페이스를 구현

### Global Layer (`global/`)

- JWT 인증 필터, 토큰 프로바이더
- Spring Security 설정
- 에러 코드, 전역 예외 처리
- Rate Limit 필터

---

## 설계 원칙

### Clean Code
- 의도가 명확한 네이밍 (변수명, 메서드명)
- 메서드는 단일 책임, 최소 크기
- 주석 없이도 코드 자체가 설명이 되도록

### OOP
- 캡슐화: 엔티티 내부 상태는 메서드를 통해서만 변경
- "Tell, Don't Ask": Getter로 꺼내서 판단하지 않고, 객체에 메시지를 보냄
- 원시 타입 포장: `SourceSite`, `SchoolType`, `ActivityType` 등 enum 활용

### SOLID

| 원칙 | 적용 |
|------|------|
| **SRP** | `AuthSessionManager`(세션), `CrawlerEngine`(크롤링), `AutoApplyRobot`(지원) 책임 분리 |
| **OCP** | 새 채용 사이트 추가 시 `Provider` 구현체만 추가, 기존 코드 수정 없음 |
| **LSP** | 모든 `AutoApplyProvider` 구현체는 동일한 인터페이스 계약 준수 |
| **ISP** | `ResumeProvider`(동기화)와 `AutoApplyProvider`(지원) 인터페이스 분리 |
| **DIP** | Service는 구체 클래스가 아닌 인터페이스에 의존, Spring DI로 주입 |

---

## 디자인 패턴

### Strategy Pattern — 사이트별 전략 교체

```
AutoApplyProvider (인터페이스)
├── SaraminApplyProvider
├── JobPlanetApplyProvider
├── JobKoreaApplyProvider
└── LinkareerApplyProvider

ResumeProvider (인터페이스)
├── SaraminResumeProvider
├── JobPlanetResumeProvider
├── JobKoreaResumeProvider
└── LinkareerResumeProvider
```

`AutoApplyRobot`과 `ResumeSyncRobot`이 오케스트레이터로 동작하며, 사이트 이름으로 적절한 Provider를 선택한다.

```java
// AutoApplyRobot - Provider를 Map으로 관리
this.providers = providerList.stream()
    .collect(Collectors.toMap(AutoApplyProvider::getSiteName, Function.identity()));
```

새 사이트 추가 시:
1. `XxxApplyProvider implements AutoApplyProvider` 구현체 추가
2. `@Component` 붙이면 Spring이 자동 주입
3. 기존 코드 변경 없음 (OCP)

### Builder Pattern — 결과 객체

```java
ResumeSyncResult.builder()
    .addSectionResult("학력", SectionResult.success())
    .addSectionResult("경력", SectionResult.fail("입력 실패"))
    .build();
```

### Repository Pattern

Spring Data JPA를 활용한 데이터 접근 추상화. 도메인 레이어에 인터페이스만 선언하고, 구현은 Spring이 자동 생성.

---

## 도메인 모델

### 핵심 엔티티 관계

```
User (1) ─── (1) Resume
  │                ├── (N) ResumeEducation
  │                ├── (N) ResumeCareer
  │                ├── (N) ResumeSkill
  │                ├── (N) ResumeCertification
  │                ├── (N) ResumeLanguage
  │                ├── (N) ResumeActivity
  │                └── (N) ResumePortfolioLink
  │
  ├── (N) ExternalAccount (사이트별 세션 쿠키)
  ├── (N) JobPreference (희망 직무)
  ├── (N) JobApplication (지원 이력)
  └── (1) UserProfile

JobPosting (채용 공고)
  ├── source: SourceSite (SARAMIN, JOBPLANET, JOBKOREA, LINKAREER)
  └── (N) AiAnalysisResult

SchedulerConfig (스케줄러 설정 — 싱글 row)
NotificationHistory (알림 중복 방지)
```

### Value Objects (Enum)

| VO | 값 |
|----|-----|
| `SourceSite` | SARAMIN, JOBPLANET, JOBKOREA, LINKAREER |
| `AuthType` | CREDENTIAL, COOKIE_SESSION |
| `SchoolType` | HIGH_SCHOOL, COLLEGE_2Y, COLLEGE_4Y, GRADUATE_MASTER, GRADUATE_DOCTOR |
| `GraduationStatus` | ENROLLED, LEAVE_OF_ABSENCE, GRADUATED, COMPLETED, DROPPED, EXPECTED |
| `ActivityType` | SCHOOL_ACTIVITY, INTERN, VOLUNTEER, CLUB, PART_TIME, EXTERNAL_ACTIVITY, EDUCATION, AWARD, OVERSEAS |

---

## 인증 & 보안

### JWT 인증 흐름

```
[회원가입/로그인] → Access Token (30분) + Refresh Token (7일) 발급
                         ↓
[API 호출] → Authorization: Bearer <access_token>
                         ↓
[JwtAuthenticationFilter] → 토큰 검증 → SecurityContext 설정
                         ↓
[토큰 만료 시] → Refresh Token으로 자동 갱신 (프론트 자동 처리)
```

### 보안 설정

- **Stateless**: 세션 없음, JWT만 사용
- **CSRF**: 비활성화 (Stateless이므로 불필요)
- **CORS**: 화이트리스트 도메인만 허용 (localhost, job.eekky.com, 채용 사이트, chrome-extension)
- **비밀번호**: BCrypt 해싱
- **외부 계정 비밀번호**: AES-256 암호화 (또는 일회용 로그인 시 미저장)
- **Rate Limiting**: IP 기반 요청 제한
- **보안 헤더**: X-Frame-Options DENY, HSTS, XSS Protection

### 엔드포인트 권한

| 경로 | 권한 |
|------|------|
| `/api/v1/auth/**` | 공개 |
| `GET /api/v1/jobs/**` | 공개 |
| `/api/v1/crawler/**` | ADMIN |
| 그 외 | 인증 필요 |

---

## 크롤링 아키텍처

### 크롤링 흐름

```
CrawlerScheduler (cron 트리거)
  → CrawlerService.crawlAll()
    → ParallelCrawler (사이트별 병렬 실행)
      → CrawlerEngine (사이트별 크롤러)
        → Playwright (브라우저 자동화)
          → Parser (HTML 파싱)
            → JobPostingRepository.saveAll() (배치 저장)
```

### 사이트별 크롤러 구조

```
infrastructure/crawler/
├── PlaywrightManager          # Playwright 브라우저 인스턴스 관리
├── core/
│   ├── CrawlerEngine          # 공통 크롤링 흐름
│   └── ParallelCrawler        # 멀티스레드 병렬 수집
├── parser/
│   └── category/              # 사이트별 HTML 파서
│       ├── SaraminParser
│       ├── JobPlanetParser
│       ├── JobKoreaParser
│       └── LinkareerParser
└── config/                    # 크롤러 설정
```

### 스텔스 설정

Playwright에 스텔스 옵션을 적용하여 봇 탐지를 우회한다:
- User-Agent 랜덤화
- WebDriver 플래그 제거
- 뷰포트/언어/타임존 설정
- 적절한 딜레이 삽입

---

## 자동 지원 아키텍처

### 자동 지원 흐름

```
[유저: 지원 버튼 클릭]
  → JobApplicationService.apply()
    → AutoApplyRobot.submitApply(userId, site, application, attachments)
      → AuthSessionManager.getSession() (Redis → DB 폴백)
        → injectSessionCookies() (Playwright Context에 쿠키 주입)
          → AutoApplyProvider.submit() (사이트별 지원 로직)
            → ApplyResult (성공/실패/알 수 없음)
```

### 세션 확보 방법 (3가지)

1. **일회용 로그인** (`POST /api/v1/accounts/onetime-login`)
   - 유저가 ID/PW 입력 → 서버 Playwright로 대리 로그인 → 쿠키 저장 → 비밀번호 즉시 폐기

2. **Chrome 확장** (`POST /api/v1/accounts/cookie-session`)
   - 유저가 본인 브라우저에서 로그인 → 확장이 쿠키 캡처 → 서버로 전송
   - 소셜 로그인 지원

3. **Playwright 팝업** (`POST /api/v1/accounts/login-popup`)
   - 서버에서 headed 브라우저 실행 → 유저가 직접 로그인 → 쿠키 자동 추출
   - 서버 앞에 앉아있는 경우만 사용 가능

---

## 이력서 연동 아키텍처

### 가져오기 (Import: 사람인 → 우리 DB)

```
[유저: 사람인에서 가져오기]
  → ResumeService.importFromSite("SARAMIN")
    → ResumeSyncRobot.importResume()
      → Playwright로 사람인 이력서 편집 페이지 접속
        → SaraminResumeImporter.importResume()
          → JS evaluate로 hidden form 필드 파싱
            → Resume 엔티티에 매핑 & 저장
```

**파싱 방식**: 사람인 이력서 편집 페이지의 `<input type="hidden">` 필드에서 구조화된 데이터를 추출한다. 화면에 보이는 텍스트가 아닌 hidden form 값을 사용하여 정확한 데이터를 얻는다.

파싱 대상:
- `input[name="user_nm"]` → 이름
- `input[name="school_nm[]"]` → 학교명
- `input[name="career_company_nm[]"]` → 회사명
- `input[name="s_ability_gb[]"]` → 스킬명
- `textarea[name="activity_contents[]"]` → 활동 설명

### 내보내기 (Sync: 우리 DB → 사람인)

```
[유저: 사람인 연동]
  → ResumeService.syncToSite("SARAMIN")
    → ResumeSyncRobot.syncResume()
      → Playwright로 사람인 이력서 작성 페이지 접속
        → SaraminResumeProvider.syncResume()
          → 각 섹션별 폼 자동 입력 (type(), evaluate(), click())
            → 저장 버튼 클릭
```

---

## 알림 시스템

### 알림 흐름

```
CrawlerScheduler (매시간 트리거)
  → NotificationService.notifyScheduledUsers(currentHour)
    → UserRepository.findByNotificationEnabledTrue()
      → User.shouldNotifyAt(hour) 필터
        → JobPreference 매칭
          → NotificationHistory 중복 체크 (DB)
            → Discord Webhook 발송
              → NotificationHistory 저장
```

### 중복 알림 방지

`NotificationHistory` 테이블에 `(userId, jobPostingId)` 유니크 제약으로 영속 저장. 서버/Redis가 재시작되어도 이미 보낸 알림은 다시 보내지 않는다.

```
notification_history
├── id (PK)
├── user_id (FK)
├── job_posting_id (FK)
├── notified_at
└── UNIQUE(user_id, job_posting_id)
```

---

## 세션 관리

### DB 원본 + Redis 캐시 이중 저장

```
[세션 저장]
  → ExternalAccount.sessionCookies (DB, 원본)
  → Redis "session:{userId}:{SITE}" (캐시, TTL 24시간)

[세션 조회]
  → Redis에서 먼저 조회
    → 없으면 DB에서 복구 → Redis에 다시 캐시

[세션 삭제]
  → DB의 sessionCookies = null
  → Redis 키 삭제
```

Redis가 날아가도 DB에서 자동 복구된다.

---

## 스케줄러

### DB 영속화된 스케줄 설정

```
scheduler_config (싱글 row)
├── crawl_cron1 (기본: 0 0 9 * * MON-FRI)
├── crawl_cron2 (기본: 0 0 14 * * MON-FRI)
├── max_pages (기본: 50)
├── enabled (on/off)
├── created_at
└── updated_at
```

관리자가 토글 off → DB에 `enabled=false` 저장 → 서버 재시작해도 off 상태 유지.

### 스케줄 실행 흐름

```
@PostConstruct
  → SchedulerConfig DB 로드 (없으면 기본값으로 생성)
  → TaskScheduler에 cron 등록
    → 실행 시점마다 DB에서 enabled 확인
      → enabled=false면 건너뜀
```
