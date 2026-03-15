# 이끼잡 프로젝트 개발 보고서

## 1. 프로젝트 개요

취업 포털 사이트의 채용 공고를 자동 수집하고, AI 기반 맞춤형 자기소개서/포트폴리오를 생성하며, 자동 지원까지 지원하는 다중 사용자 서비스.

### 기술 스택
- **Backend**: Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security
- **Crawler**: Playwright Java (헤드리스 브라우저 기반 동적 렌더링)
- **Database**: PostgreSQL, Redis (캐싱/중복 체크)
- **Frontend**: React, Next.js 16, shadcn/ui, Tailwind CSS
- **AI**: OpenClaw HTTP API (OpenAI 호환, GPT-5.4)
- **빌드/배포**: Gradle, GitHub Actions, Cloudflare Tunnel

### 규모
- Backend: Java 124개 파일
- Frontend: TSX/TS 35개 파일, 14개 페이지
- 크롤링 대상: 사람인, 잡플래닛, 링커리어, 잡코리아 + 링커리어 합격 자소서

---

## 2. 아키텍처

### DDD 기반 레이어 구조

```
job-crawler/
├── api/              # Presentation Layer (REST Controller)
│   ├── ai/           # AI 분석 API
│   ├── crawler/      # 크롤링/삭제 API
│   ├── jobposting/   # 공고 조회 API
│   ├── coverletter/  # 자소서 API
│   └── ...
├── application/      # Application Layer (Service - 흐름만 제어)
│   ├── ai/           # AI 자동화 서비스
│   ├── crawler/      # 크롤링 서비스, 스케줄러
│   ├── jobposting/   # 공고 검색/통계
│   ├── notification/ # 디스코드 알림
│   └── ...
├── domain/           # Domain Layer (Entity, VO, Repository)
│   ├── jobposting/   # 공고 엔티티 + 비즈니스 로직
│   ├── coverletter/  # 자소서 엔티티
│   ├── aianalysis/   # AI 분석 결과 (유저별 저장)
│   └── ...
├── infrastructure/   # Infrastructure Layer (외부 시스템 연동)
│   ├── crawler/      # 크롤러 엔진 + 사이트별 파서
│   ├── ai/           # OpenClaw API 연동
│   ├── github/       # GitHub 레포 분석
│   └── notification/ # 디스코드 웹훅
└── global/           # 공통 설정, 인증, 에러 처리
```

### 크롤러 아키텍처 (Strategy + Composition 패턴)

```
JobScraper (인터페이스)
    ├── SaraminScraper
    ├── JobPlanetScraper
    ├── LinkareerScraper
    └── JobKoreaScraper

각 Scraper = PlaywrightScrapingEngine + SiteParser (조합)

SiteParser (인터페이스)
    ├── SaraminParser    - 사람인 DOM 파싱
    ├── JobPlanetParser  - 잡플래닛 DOM 파싱
    ├── LinkareerParser  - 링커리어 DOM 파싱 (JS evaluate)
    └── JobKoreaParser   - 잡코리아 DOM 파싱 (iframe + JS evaluate)

PlaywrightScrapingEngine - 브라우저 조작 (페이지 순회, 타임아웃)
PlaywrightManager       - 브라우저 인스턴스 관리 (Anti-Bot, Stealth)
```

### AGENTS.md 원칙 적용 근거

**1. Clean Code**
- 메서드는 단일 책임: `extractKeyInfo()`, `extractApplicationMethod()`, `extractBenefits()` 각각 독립
- 의미 명확한 네이밍: `isDeadlineExpired()`, `isAdImage()`, `normalizeCategory()`
- 주석 없이 코드 자체가 설명: `builder.title(title).company(company).location(location)`

**2. OOP 캡슐화**
- `JobPosting.markAsClosed()`, `JobPosting.isExpired()` - 도메인 객체에 비즈니스 로직 위임
- `TechStack` 일급 컬렉션으로 원시 String 포장
- Getter/Setter 지양: Builder 패턴으로 생성, 변경은 도메인 메서드로만

**3. SOLID**
- **SRP**: `PlaywrightScrapingEngine`(브라우저 조작) vs `SiteParser`(DOM 파싱) 분리
- **OCP**: 새 사이트 추가 시 `SiteParser` 구현체만 추가 (기존 코드 수정 없음)
- **DIP**: `JobScraper`, `SiteParser`, `AiTextGenerator`, `NotificationSender` 모두 인터페이스 의존
- Spring DI로 `List<JobScraper> scrapers` 자동 주입 → 전체 크롤링 시 모든 크롤러 순회

---

## 3. 구현 기능 목록

### 크롤링
| 사이트 | 리스트 셀렉터 | 상세 추출 | 페이지네이션 |
|--------|-------------|----------|------------|
| 사람인 | `.list_recruiting .list_body > .list_item` | iframe + `.jv_summary` | `.PageBox button` 클릭 |
| 잡플래닛 | `a[href*='posting_ids']` | JSON 구조 파싱 | 무한 스크롤 |
| 링커리어 | `a.recruit-link` (React SPA) | JS `page.evaluate()` | URL `page=N` |
| 잡코리아 | `tr.devloopArea` | iframe 직접 접근 + JS evaluate | `.tplPagination` 클릭 |
| 자소서 | `a.link[href*='/cover-letter/']` | `h1.basic-info` + `article` | URL `page=N` |

### AI 기능
- AI 적합률 분석 (유저별 캐시)
- AI 기업 분석 (웹 검색 + AI 자체 지식)
- AI 자소서 자동 생성 (프로필 + 공고 + 프로젝트 매칭)
- AI 포트폴리오 자동 생성
- GitHub 프로젝트 AI 분석 (README + 빌드파일 + 트리)
- 분석 결과 유저별 DB 저장 (새로고침해도 유지)

### 프론트엔드 (14페이지)
- 메인: 공고 목록 (카드/리스트 뷰, 사이트별 탭, 필터, 페이지네이션)
- 상세: 공고 상세 (섹션별 분리, 이미지, AI 버튼)
- 자소서: 합격 자소서 목록/상세 (정렬, 학교 필터, 문항 분리)
- 관리: 크롤링 실행, 스케줄, 공고 삭제 (사이트별/전체/ID)
- 프로필/프로젝트/템플릿/설정/지원이력

---

## 4. 문제 해결 기록

### 문제 1: 사람인 URL 파라미터 불일치
- **문제**: `recruitPage=1&recruitPageCount=50` 사용 → 실제는 `page=1&page_count=50`
- **고민**: MCP로 실제 페이지 분석 vs 문서 기반 추측
- **시도**: Playwright MCP로 실제 DOM 스냅샷 + 페이지네이션 버튼 onclick 분석
- **해결**: 실제 사이트의 URL 파라미터 (`page`, `page_count`, `sort=RD`)로 전면 교체, 페이지네이션도 `.PageBox button[page='N']` 클릭 방식으로 변경

### 문제 2: `recruitPage` regex가 `recruitPageCount`도 매칭
- **문제**: `replaceAll("recruitPage=\\d+", ...)` → `recruitPageCount=50`까지 치환 → URL 깨짐
- **고민**: 단순 문자열 치환의 한계
- **해결**: `replaceAll("([?&])recruitPage=\\d+", "$1recruitPage=N")` 캡처 그룹으로 정확한 파라미터만 매칭

### 문제 3: 사람인 상세 데이터 안 들어옴
- **문제**: `.jv_header .col dl` 셀렉터 → DOM에 존재하지 않음
- **시도**: MCP로 실제 상세 페이지 DOM 스냅샷 분석
- **해결**: 실제 셀렉터 `.jv_summary .cont dl`로 수정, iframe 로딩 대기(`LOAD` + `waitForSelector`) 추가

### 문제 4: 우대사항 "4건 상세보기"만 표시
- **문제**: `dd` 텍스트가 "4건\n우대사항\n상세보기" (실제 내용은 숨겨진 툴팁)
- **고민**: 툴팁 클릭 vs 숨겨진 DOM 직접 접근
- **해결**: `dd.preferred .toolTipTxt li`에서 숨겨진 실제 내용 추출 (AJAX 불필요, DOM에 이미 존재)

### 문제 5: 링커리어 공고 20개 찾고 0개 수집
- **문제**: React SPA - `tbody tr` 존재하지만 내부 React 컴포넌트 미렌더링
- **고민**: Locator API vs JavaScript evaluate vs 렌더링 대기
- **시도**: `waitForSelector(".recruit-name")` 추가 → 여전히 실패
- **해결**: `SiteParser.parseJobData()` 내에서 각 공고마다 상세 페이지를 직접 방문, `page.evaluate()`로 JS에서 데이터 추출. React DOM을 Java Locator로 접근하는 것 자체가 불안정하므로 우회.

### 문제 6: 잡코리아 상세에 AI추천/합격자소서 포함
- **문제**: `styles_mt_space60` 섹션 전부 수집 → 스마트픽, AI추천공고, 합격자소서까지 description에 포함
- **고민**: CSS 클래스 필터 vs data 속성 필터
- **해결**: `data-sentry-component` 속성으로 정확한 컴포넌트 필터링. `RecruitmentGuidelines`, `Qualification`, `ApplyBox`, `CorpInformation`, `BenefitCard`만 허용, 나머지 제외.

### 문제 7: 잡코리아 iframe이 Next.js RSC 코드 포함
- **문제**: `innerText()`가 `self.__next_f.push([1,"$Sreact..."])` 코드까지 포함
- **고민**: FrameLocator vs iframe URL 직접 접근
- **해결**: iframe src URL을 추출하여 별도 페이지로 직접 네비게이션. `<script>`, `<style>` 태그 제거 + RSC payload 줄 단위 필터링.

### 문제 8: DB 삭제 후 0개 크롤링
- **문제**: "전체 삭제" → DB 비었지만 Redis 캐시(`crawled:job:*`)가 남아 있음 → `saveIfNew`에서 전부 중복 판정
- **해결**: "전체 삭제" 시 `redisTemplate.keys("crawled:job:*")` 삭제도 함께 수행

### 문제 9: LINKAREER DB 저장 실패
- **문제**: `job_postings_source_check` 제약조건에 `LINKAREER` 없음
- **해결**: `ALTER TABLE job_postings DROP CONSTRAINT ... ADD CONSTRAINT ... CHECK (source IN ('SARAMIN','JOBPLANET','LINKAREER','JOBKOREA'))`

### 문제 10: AI 분석 결과 새로고침 시 사라짐
- **문제**: `useState`에만 저장 → 페이지 리로드 시 증발
- **해결**: `AiAnalysisResult` 엔티티 생성 (userId + jobPostingId + type 유니크), 페이지 로드 시 `GET /ai/results/{jobId}`로 저장된 결과 조회

### 문제 11: 자소서 학점 "4.05/4.5" → "4.05"와 "4.5"로 분리
- **문제**: `split("\\s*/\\s*")` → "4.05/4.5"의 `/`도 구분자로 인식
- **해결**: `split("\\s+/\\s+")` (공백 필수) → 공백 없는 "4.05/4.5"는 분리 안 됨

### 문제 12: 사이트별 필터 옵션 불일치
- **문제**: 모든 탭에서 같은 경력/학력 필터 → 사이트마다 데이터 형식 다름
- **해결**: `getCareerOptions()`, `getEducationOptions()`, `getMethodOptions()` 함수로 사이트별 동적 옵션 반환. 링커리어는 학력 필터 숨김 (데이터 없음).

---

## 5. 프론트엔드 구조

```
job-frontend/src/
├── app/
│   ├── page.tsx              # 메인 공고 목록 (5개 탭, 필터, 페이지네이션)
│   ├── jobs/[id]/page.tsx    # 공고 상세 (섹션 분리, AI 버튼, 적합률)
│   ├── cover-letters/        # 합격 자소서 목록/상세
│   ├── admin/page.tsx        # 관리자 (크롤링, 스케줄, 삭제)
│   ├── applications/         # 지원 이력 + 지원서 미리보기
│   ├── profile/              # 프로필 (학력, 경력, 기술스택)
│   ├── projects/             # 프로젝트 CRUD
│   ├── templates/            # 자소서/포트폴리오 템플릿
│   ├── settings/             # 디스코드 알림, 외부 계정, 직무 설정
│   └── login/, signup/       # 인증
├── components/
│   ├── navbar.tsx            # 상단 네비게이션
│   ├── job-card.tsx          # 공고 카드/리스트 아이템
│   └── filter-panel.tsx      # 사이트별 필터 사이드바
└── lib/
    ├── api.ts                # REST API 호출 함수
    └── auth-context.tsx      # JWT 인증 컨텍스트
```

---

## 6. 데이터 흐름

### 크롤링 → 저장 → 표시
```
[스케줄러 / 관리자 버튼]
    ↓
PlaywrightScrapingEngine.scrape()
    ↓ SiteParser.buildSearchUrl() → 리스트 페이지 접근
    ↓ SiteParser.getListItems() → 공고 목록 추출
    ↓ SiteParser.parseJobData() → 각 공고 상세 추출
    ↓ CrawledJobData (인프라 DTO)
    ↓
CrawlerServiceImpl.saveIfNew()
    ↓ Redis 중복 체크 → DB 중복 체크 → 신규만 저장
    ↓
JobPosting (도메인 엔티티) → PostgreSQL
    ↓
NotificationService.notifyNewJobPostings()
    ↓ 유저 희망 직무 매칭 → Discord 웹훅 발송
```

### AI 지원서 준비
```
[프론트] "지원서 준비" 클릭
    ↓
JobApplyService.prepareApplication()
    ↓ matchProjects() → 기술 스택 겹침으로 프로젝트 매칭 (없으면 전체)
    ↓ generateCoverLetter() → OpenClaw API (프로필 + 공고 + 프로젝트)
    ↓ generatePortfolio() → OpenClaw API
    ↓
JobApplication (PENDING) → DB 저장
    ↓
[프론트] /applications/[id]/preview 에서 수정/재생성/제출
```

---

## 7. 보안 및 인증

- JWT 기반 무상태 인증 (24시간 만료)
- 역할 기반 권한: `USER` (일반), `ADMIN` (크롤링/삭제)
- CSRF 비활성화 (REST API)
- CORS: localhost:3000, job.eekky.com 허용
- 크롤러 Anti-Bot: User-Agent 랜덤, Stealth 스크립트, 뷰포트 랜덤화

---

## 8. 향후 과제

| 우선순위 | 항목 | 상태 |
|---------|------|------|
| 높음 | 배포 (CI/CD + Cloudflare Tunnel) | 미착수 |
| 높음 | 자동 지원 (Playwright 로봇) | 코드 있음, 미테스트 |
| 중간 | 잡코리아 신입/인턴 전용 크롤러 | 미착수 |
| 중간 | 디스코드 알림 실제 발송 테스트 | 코드 연결됨, 미테스트 |
| 낮음 | 필터 데이터 정규화 | 기본 LIKE 검색 구현 |
| 낮음 | PWA 아이콘/매니페스트 | 미완성 |
