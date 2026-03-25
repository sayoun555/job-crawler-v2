# 이끼잡 프로젝트 핵심 데이터 요약

> AI 프로젝트 분석용 요약 문서. 각 문서의 문제 → 해결 → 정량적 결과만 추출.

---

## 1. Playwright 병렬 크롤링 (PROJECT_PLAYWRIGHT_PARALLEL.md)

**문제**: 상세 페이지 병렬 수집 시 3개 워커 스레드가 단일 Playwright WebSocket 파이프라인을 공유해 약 60% 실패 발생.

| 사이트 | 수집 | 성공 | 실패율 |
|--------|------|------|--------|
| JOBKOREA | 60 | ~22 | 63% |
| SARAMIN | 50 | ~34 | 32% |
| LINKAREER | 20 | ~8 | 60% |
| JOBPLANET | 16 | ~11 | 31% |

**해결**: 스레드별 독립 Playwright 인스턴스 + 독립 WebSocket + 독립 Chromium 프로세스 구조로 전환.

**결과**:
| 항목 | Before | After |
|------|--------|-------|
| 상세 페이지 성공률 | ~40% | **100%** |
| 브라우저 프로세스 | 1개 (~200MB) | 3개 (~500MB) |
| 50건 처리 시간 | ~90초 (에러 포함) | ~90초 (에러 없음) |

**트레이드오프**: 메모리 300MB 추가 사용. `crawler.detail.concurrency` 설정으로 워커 수 조절 가능.

---

## 2. 트래픽 대응 & 보안 (PROJECT_TRAFFIC_SECURITY.md)

**문제**: DB 커넥션 10개, 캐싱 없음, Rate Limiting 없음 상태에서 동시 접속 약 100명이 한계.

**해결**:
- DB 커넥션 풀: HikariCP 10개 → 30개 (PostgreSQL max_connections 100의 30%)
- Redis 캐싱: 공고 통계 API에 5분 캐시 적용 → DB 부하 80% 감소
- Rate Limiting: IP당 분당 200회 제한, JWT 필터 앞에 배치하여 파싱 비용 절감

**결과**:
| 항목 | Before | After |
|------|--------|-------|
| 동시 접속 | ~100명 | ~300~500명 |
| DB 부하 | 매 요청 직접 쿼리 | 캐싱으로 80% 감소 |
| 보안 등급 | C+ (5.5/10) | B (7.5/10) |

**보안 강화**:
- 외부 계정 비밀번호: Base64 → AES-256-GCM 암호화
- API 응답 민감정보 @JsonIgnore 마스킹
- 관리자 계정 하드코딩 → 환경변수 관리
- X-Content-Type-Options, X-Frame-Options, HSTS 보안 헤더 적용

---

## 3. innerHTML 전환 (BLOG_CRAWLING_HTML.md)

**문제**: Playwright innerText()로 수집 시 표(table)가 탭 문자로 깨지고, 자격요건 필드에 전체 텍스트가 오염.

**시도한 방법 5가지**:
1. innerHTML로 HTML 자체 저장 → **채택**
2. iframe 임베딩 → 보안 위험
3. 스크린샷 → 텍스트 검색 불가
4. 프론트에서 탭 파싱 → 행 병합 부정확으로 실패
5. 마크다운 변환 → 멀티라인 셀 깨짐

**해결**: innerHTML + 이중 소독 (서버: Jsoup Safelist.relaxed(), 클라이언트: DOMPurify)

**결과**:
- 표 구조 100% 보존
- 저장 용량: 5KB → ~10KB (TEXT 컬럼이라 문제없음)
- 다크모드 글씨 안 보임 2차 문제 → inline style 전면 제거로 해결

---

## 4. 스케줄러·Redis 휘발성·JWT (BLOG_SESSION4_DEVLOG.md)

**문제 1 - 스케줄러**: 관리자가 크롤링 off 했는데 서버 재시작하면 다시 on. AtomicBoolean 메모리 저장이 원인.
**해결**: SchedulerConfig 엔티티 → DB 영속화. 서버 재시작 후에도 설정 유지.

**문제 2 - 알림 중복**: Redis 재시작 시 알림 이력이 사라져 같은 공고 재발송.
**해결**: NotificationHistory 엔티티 → DB 영속화 + Redis 캐시 이중 저장.

**문제 3 - 사이트 연동 세션**: Redis에만 저장된 외부 사이트 세션이 장애 시 유실.
**해결**: Cache-Aside 패턴. DB 원본 + Redis 캐시. Redis 미스 시 DB에서 복구.

**문제 4 - jQuery 충돌**: 사람인 로그인 시 Playwright fill()이 jQuery 이벤트를 트리거하지 못함.
**해결**: fill() → click() + type(delay:50)으로 실제 키 입력처럼 동작.

**문제 5 - 트랜잭션 롤백**: crawlAll()에 @Transactional이 걸려있어 한 사이트 실패 시 전체 롤백.
**해결**: @Transactional 제거. 사이트별 독립 try-catch로 격리.

**문제 6 - JWT 만료**: 30분마다 로그아웃. 자동 갱신 없었음.
**해결**: 프론트에서 만료 2분 전 자동 refresh 호출.

---

## 5. 코드 품질 리팩토링 (BLOG_SESSION5_DEVLOG.md)

**문제 1 - @Setter 남발**: CrawledJobData 412줄, 40개 이상 setter 호출. Tell Don't Ask 위반.
**해결**: @Setter 제거 → enrichBasicInfo/enrichJobDetail/enrichConditions/enrichClassification 4개 메서드. 변환 로직 CrawledJobDataConverter 분리. AI 프롬프트 AiPromptDataBuilder 분리.

| 항목 | Before | After |
|------|--------|-------|
| AiAutomationServiceImpl | 412줄 | 250줄 |
| CrawledJobData | @Setter + 무분별 접근 | 의미 단위 4개 enrich 메서드 |
| 변환 로직 | 서비스에 혼재 | CrawledJobDataConverter 분리 |

**문제 2 - AI 적합률 비직관적**: 기술스택 50% 일치인데 적합률 85% 산출.
**해결**: 기술스택 매칭에 60% 가중치 부여. 같은 분야 기본 50점, 다른 분야 30점 이하 규칙.

| 항목 | Before | After |
|------|--------|-------|
| 기술스택 50% 일치 시 | 적합률 85% | 적합률 52% |

**문제 3 - 마감 공고 영구 생존**: deadline=NULL인 "채용시 마감" 공고가 영원히 열려있음.
**해결**: PostingUrlValidator가 7일 이상 된 공고의 원본 URL을 GET 요청으로 확인. 404/리다이렉트/"마감" 키워드 감지 시 closed 처리. 매일 새벽 3시 자동 실행.

**문제 4 - OCR**: 이미지 기반 공고에서 텍스트 추출 불가.
**해결**: Tesseract OCR 도입. 한글 인식 정확도 약 85~90%. AI 프롬프트에 "OCR 텍스트이므로 오타 가능" 명시.

---

## 6. 이력서 연동 14번의 시도 (RESUME_SYNC_DEVLOG.md)

**문제**: 사람인 이력서 자동 등록 시 jQuery 이벤트 위임, hidden 필드, 자동완성 드롭다운 때문에 14번 시도 중 13번 실패.

**실패 원인 분석**:
- fill()은 jQuery 이벤트를 발생시키지 않음
- force:true는 가시성 우회만 하고 jQuery 이벤트 위임은 트리거 안 됨
- JS element.click()은 confirm() 다이얼로그를 블로킹
- 학교명 자동완성은 hidden 필드(school_direct[])가 핵심인데 자동으로 세팅 안 됨

**해결**: Playwright MCP 방식으로 전환. AI가 실시간으로 페이지를 보면서 판단하는 방식이 하드코딩 셀렉터보다 안정적.

**핵심 교훈**:
- 외부 사이트 자동화는 "보면서 하는 것"이 맞다
- jQuery 기반 사이트는 type(delay:50)으로 실제 키 입력을 시뮬레이션해야 한다
- 폼이 여러 개일 때 "마지막 폼"이 아닌 특정 폼을 명시적으로 선택해야 한다

---

## 7. 아키텍처 요약 (ARCHITECTURE.md)

**레이어 구조**: api → application → domain → infrastructure (DIP 적용)
**디자인 패턴**:
- Strategy Pattern: 사이트별 크롤러 파서 (SiteParser 인터페이스)
- Builder Pattern: CrawledJobData, JobPosting
- Repository Pattern: Spring Data JPA

**크롤링 흐름**: 스케줄러 → JobScraper → SiteParser(Strategy) → CrawledJobData → CrawledJobDataConverter → JobPosting → DB

**세션 관리**: Cache-Aside 패턴 (DB 원본 + Redis 캐시)
**알림**: 직무 매칭 → NotificationHistory 중복 방지 → Discord 웹훅
**스케줄러**: DB 영속 설정 (enabled, cron, maxPages)

---

## 8. 프로젝트 규모 (PROJECT_REPORT.md)

- Backend: Java 21, Spring Boot 3.4, 14개 도메인
- Frontend: Next.js 16, React 19, TypeScript, 14페이지
- 크롤링 대상: 사람인, 잡플래닛, 잡코리아, 링커리어 (4개 사이트)
- AI 기능: 적합률 분석, 기업 분석, 자소서 생성, 포트폴리오 생성, GitHub 분석, 합격 자소서 패턴 분석
- 이력서: 학력·경력·스킬·자격·어학·활동·포트폴리오 7개 하위 엔티티
- 문제 해결 기록: 12개 (PROJECT_REPORT) + 9개 (SESSION4) + 11개 (SESSION5) + 5개 (TRAFFIC_SCALE) = 37건

---

## 9. 대규모 트래픽 대응 설계 (BLOG_TRAFFIC_SCALE.md)

- **AI Semaphore 동시 제한**: 다수 유저 동시 AI 요청 시 톰캣 스레드 고갈 위험 → java.util.concurrent.Semaphore(5)로 동시 AI 요청 5개 제한, 6번째부터 30초 대기, 초과 시 "잠시 후 다시 시도해주세요" 안내 메시지 → 일반 API와 AI 요청 격리, 외부 API 과부하 방지
- **Cache Stampede 방지**: 캐시 만료 순간 동시 요청이 전부 DB를 조회하는 Stampede 현상 → Redis setIfAbsent 분산 락으로 첫 스레드만 DB 조회, 나머지 500ms 대기 후 캐시 재확인 → 5개 동시 요청에서 "캐시 저장" 1회만 발생, DB 쿼리 20개→4개로 감소
- **AI 비동기 큐**: AI 자소서/포트폴리오 생성 30초~1분 동기 대기로 화면 멈춤 → Redis 기반 태스크 큐(AiTaskQueue)로 즉시 접수(1초) + 백그라운드 처리(30초) + 폴링(/async/status)으로 결과 수신 → 유저 체감 대기 1초, 탭 닫아도 taskId로 재조회 가능
- **WebSocket 실시간 알림**: 폴링 방식은 3초 간격 불필요한 요청 발생, 실시간성 부족 → STOMP over SockJS로 AI 태스크 완료 시 유저별 채널(/topic/ai/{userId})로 즉시 푸시, 폴링 fallback 유지 → 결과 수신 지연 3초→0ms, 불필요한 네트워크 요청 제거
- **DB 검색 인덱스**: LIKE '%keyword%' 검색이 Full Table Scan → PostgreSQL pg_trgm GIN 인덱스로 title, company 트라이그램 검색 가속 + JPA @Index 9개(title, jobCategory, createdAt, source, deadline, company, url, source+closed, closed+deadline) → 만 건 이상에서 검색 100ms+→20ms

---

## 10. 자동 지원 시스템 완성 & 사이트별 AI 연동 (세션 7)

### 10-1. 자동 지원 라우팅 버그 수정

**문제**: JobApplyServiceImpl에서 SARAMIN/JOBPLANET만 하드코딩 분기. 잡코리아/링커리어 공고 지원 시 잘못된 Provider로 라우팅.

**해결**: `submitApply(userId, siteName, app, attachments)` 전략패턴 통합 호출로 변경. AutoApplyRobot이 사이트명으로 올바른 Provider를 자동 선택.

**결과**: 4개 사이트 모두 올바른 Provider로 라우팅 확인.

### 10-2. Dialog 핸들러 순서 버그 수정

**문제**: SaraminApplyProvider/JobKoreaApplyProvider에서 `page.onDialog()` 핸들러를 제출 버튼 클릭 **이후**에 등록. 클릭 시 발생하는 confirm 다이얼로그를 놓칠 수 있음.

**해결**: dialog 핸들러를 지원 버튼 클릭 **이전**에 등록하고, 중복 핸들러 제거.

### 10-3. LinkareerApplyProvider 전면 재작성

**문제**: 링커리어 Provider가 미완성. 팝업 처리, 이력서 선택, 약관 동의, 외부 사이트 감지 누락.

**해결**: 사람인/잡코리아 수준으로 강화.

| 기능 | Before | After |
|------|--------|-------|
| 팝업/새탭 처리 | 없음 | getLatestPage() |
| 이력서 선택 | 없음 | 라디오/셀렉트 지원 |
| 자소서 입력 | 첫 textarea만 | name/placeholder 기반 스마트 매칭 + contenteditable |
| 약관 동의 | 없음 | 전체동의 우선 + 개별 checkbox |
| 외부 사이트 감지 | 없음 | linkareer.com 외 href 감지 → fail 반환 |
| 결과 검증 | 기본 3패턴 | 5개 성공패턴 + URL 변화 + 에러 감지 |

### 10-4. 사후 검증 스케줄러 4개 사이트 확장

**문제**: ApplicationVerificationScheduler가 사람인/잡플래닛만 지원. 직접 Redis 접근.

**해결**: AuthSessionManager 위임으로 리팩토링 + 잡코리아/링커리어 검증 추가. switch 표현식으로 사이트별 URL/셀렉터 분리.

### 10-5. 세션 만료 정확 감지

**문제**: ExternalAccount.hasValidSession()이 쿠키 문자열 존재만 체크. 사이트 세션이 만료돼도 "연동됨"으로 표시.

**해결**: sessionExpiresAt 필드 추가. 세션 저장 시 쿠키 expires 중 최소값을 파싱하여 기록. hasValidSession()에서 현재 시각과 비교.

| 항목 | Before | After |
|------|--------|-------|
| 만료 감지 | 쿠키 문자열 존재만 체크 | expires 시각 기반 정확 비교 |
| 만료 시 UI | "연동됨" (잘못 표시) | "세션 만료" + 재연동 버튼 |

### 10-6. 프론트엔드 WebSocket + 비동기 큐 UI

**문제**: 백엔드 AiTaskQueue + WebSocket은 완성됐지만 프론트에서 소비하지 않음. AI 자소서/포트폴리오 생성 시 30초~1분 동기 대기.

**해결**:
- `useAiTaskQueue` 훅: WebSocket `onAiTaskComplete` 리스너 + 3초 간격 폴링 fallback
- `AiTaskProgress` 컴포넌트: 인라인 진행상태 (PENDING/PROCESSING + 경과 시간)
- `AiTaskNotification` 컴포넌트: 성공/실패 토스트 (5초 자동 소멸)
- 미리보기 페이지: 동기 `aiApi.coverLetter()` → 비동기 `aiQueue.startCoverLetter()` 전환

| 항목 | Before | After |
|------|--------|-------|
| 재생성 UX | 버튼 클릭 → 30초 멈춤 → 결과 | 버튼 클릭 → 즉시 "생성 중..." → 실시간 완료 알림 |
| 결과 수신 | 동기 HTTP 응답 | WebSocket 푸시 (0ms 지연) + 폴링 fallback |
| 생성 중 다른 작업 | 불가 (UI 블로킹) | 가능 (비동기) |

### 10-7. 사이트별 AI 프로필 전략패턴

**문제**: AiPromptDataBuilder.buildProfileString()이 모든 사이트에 동일한 프로필을 전달. 각 사이트의 이력서 양식과 강조 포인트가 다른데 반영 안 됨.

**해결**: SiteProfileStrategy 인터페이스 + 4개 구현체 (SRP, OCP, DIP 준수)

| 사이트 | 강조 포인트 | 서술 스타일 |
|--------|-----------|-----------|
| 사람인 | 경력 상세 + 기술스택 | 자유 서술형, 프로젝트 경험 구체적 |
| 잡코리아 | 자격증 + 스킬 | 간결 구조화, 핵심 키워드 중심 |
| 잡플래닛 | 커리어 내러티브 | 기업문화 핏, 성장 스토리 |
| 링커리어 | 학력 + 대외활동 | 성장가능성, 학습 의지 |

**구조**: `ProfileBuildHelper`(공통 유틸) + `SiteProfileStrategy`(인터페이스) + 4개 구현체 → `AiPromptDataBuilder`가 위임

### 10-8. 이력서 동기화 상태 추적

**문제**: 어떤 사이트에 이력서가 동기화됐는지 추적 불가. 프론트에서 상태 표시 안 됨.

**해결**: ExternalAccount에 resumeSyncedAt/resumeSyncStatus/resumeSyncMessage 필드 추가. ResumeSyncRobot이 sync 완료 후 상태 저장. 기존 `/api/v1/accounts` 응답에 자동 포함 (새 API 불필요).

---

## 11. 사이트별 이력서 시스템 & 커스텀 자소서 & AI 프롬프트 강화 (세션 7 후반)

### 11-1. 사이트별 이력서 저장 시스템

**문제**: Resume 엔티티가 유저당 1개(1:1)로, 모든 사이트에 같은 이력서를 사용. 실제로는 사람인/잡코리아/잡플래닛/링커리어에 등록한 이력서가 각각 다름.

**해결**: Resume에 `sourceSite` 컬럼 추가하여 유저당 최대 5개(마스터 + 사이트별 4개) 이력서 저장. `@OneToOne` → `@ManyToOne` 변경. 4개 사이트 Importer 구현(Saramin/JobKorea: hidden form 추출, JobPlanet: SPA 카드 클릭 후 DOM 추출, Linkareer: React input + CareerChip 추출).

| 항목 | Before | After |
|------|--------|-------|
| 이력서 | 유저당 1개 | 유저당 최대 5개 (마스터 + 사이트별) |
| Import | 사람인만 | **4개 사이트 전부** |
| AI 자소서 | 통합 이력서 사용 | **해당 사이트 이력서 우선 사용 (fallback: 마스터)** |

### 11-2. AI 프롬프트 강화 (합격 자소서 패턴 기반)

**문제**: AI 자소서가 건조한 "~적용 → ~달성" 패턴으로 생성. 실제 합격 자소서와 톤/구조 차이가 큼.

**해결**: IT 대기업 합격 자소서 5건(LG CNS, LG유플러스, 세메스, 하나금융TI, 한전KDN) 분석 후 공통 패턴 추출하여 프롬프트에 반영.
- 소제목 [대괄호] 필수, 3단 구조(상황→행동→결과→교훈), 다층적 문제 해결, 정량적 성과, 솔직한 한계 인정
- 포트폴리오 문제 해결 섹션: 건조체 → 구어체 + 시행착오 + 트레이드오프
- 기업분석 결과를 자소서 프롬프트에 전달하여 지원동기 구체화
- 공고 상세내용 제한 2,000자 → 5,000자 확대

### 11-3. 커스텀 자소서 시스템

**문제**: AI가 고정된 4섹션(지원동기/직무역량/프로젝트/성장계획) 자유 양식으로만 생성. 실제 사이트 지원 폼은 문항별 textarea가 여러 개이고 문항마다 요구사항이 다름.

**해결**: 유저가 문항을 동적으로 추가하고 문항별 규칙을 설정하면 AI가 문항별로 분리 생성하는 커스텀 자소서 시스템 구현.
- 모드 1(기본): 기존 사이트별 맞춤 프롬프트로 자유 양식 생성
- 모드 2(커스텀): 유저 입력 문항 + 규칙 + 추가 요청 → AI가 JSON 형식으로 문항별 생성
- 프론트: 문항 동적 추가/삭제 UI, 규칙 입력 가이드, 추가 요청 필드
- 비동기: WebSocket + 폴링 fallback으로 생성 완료 알림

### 11-4. 지원서 준비 비동기 전환

**문제**: "지원서 준비" 클릭 시 AI 생성(30초~1분) 동안 화면이 멈춤.

**해결**: 빈 지원서를 즉시 생성 → 미리보기 페이지로 이동 → 백그라운드에서 AI 생성 → WebSocket으로 완료 알림 → 프론트 자동 갱신.

| 항목 | Before | After |
|------|--------|-------|
| 응답 시간 | 30초~1분 (블로킹) | **즉시** (비동기) |
| UX | 화면 멈춤 | 미리보기에서 "AI 생성 중..." → 자동 완료 |

---

## 12. 프로젝트 규모 업데이트

- Backend: Java 21, Spring Boot 3.4, 14개 도메인, **Strategy Pattern 4종** (크롤러파서 + 자동지원Provider + AI프로필전략 + 이력서Import)
- Frontend: Next.js 16, React 19, TypeScript, 14페이지, **WebSocket 실시간 통신**, **커스텀 자소서 UI**
- 크롤링 대상: 사람인, 잡플래닛, 잡코리아, 링커리어 (4개 사이트)
- AI 기능: 적합률 분석, 기업 분석, **기본+커스텀 자소서 생성**, 포트폴리오 생성, GitHub 분석, 합격 자소서 패턴 분석, **사이트별 맞춤 프로필 전략**
- 이력서: **사이트별 이력서 저장** (마스터 + 4사이트), **4개 사이트 Import** (Saramin/JobKorea/JobPlanet/Linkareer)
- 자동 지원: 4개 사이트 Provider, **사후 교차검증 4개 사이트**, 세션 만료 정확 감지
- 비동기 처리: Redis 태스크 큐 + WebSocket 실시간 알림 + 폴링 fallback (**지원서 준비 비동기 포함**)
- 문제 해결 기록: 37건 + **8건 (세션 7)** + **4건 (세션 7 후반)** = **총 49건**
