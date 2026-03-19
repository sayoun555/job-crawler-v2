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
- 문제 해결 기록: 12개 (PROJECT_REPORT) + 9개 (SESSION4) + 11개 (SESSION5) = 32건
