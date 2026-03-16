# 채용 공고 크롤러 — Playwright 병렬 처리 & 데이터 품질 개선 기록

> Playwright Java 기반 크롤러에서 상세 페이지 병렬 수집 시 발생하는 thread-safety 문제를 해결하고,
> 수집 데이터의 품질을 높인 과정을 기록합니다.

---

## 목차

1. [Playwright 병렬 처리](#1-playwright-병렬-처리)
   - [1.1 문제 발견: 상세 페이지 60% 실패](#11-문제-발견-상세-페이지-60-실패)
   - [1.2 1차 시도: synchronized 동기화](#12-1차-시도-synchronized-동기화)
   - [1.3 2차 시도: 순차 처리 fallback](#13-2차-시도-순차-처리-fallback)
   - [1.4 근본 원인 분석: WebSocket 단일 파이프라인](#14-근본-원인-분석-websocket-단일-파이프라인)
   - [1.5 최종 해결: 스레드별 독립 Playwright 인스턴스](#15-최종-해결-스레드별-독립-playwright-인스턴스)
   - [1.6 headless 차단 이슈](#16-headless-차단-이슈)
2. [크롤링 데이터 품질 개선](#2-크롤링-데이터-품질-개선)
   - [2.1 이미지 사이즈 필터링](#21-이미지-사이즈-필터링)
   - [2.2 iframe fallback 쓰레기 데이터 방지](#22-iframe-fallback-쓰레기-데이터-방지)
   - [2.3 HTML 공백 아티팩트 정리](#23-html-공백-아티팩트-정리)
3. [운영 개선](#3-운영-개선)
   - [3.1 Hibernate SQL 로그 폭주 해결](#31-hibernate-sql-로그-폭주-해결)
   - [3.2 파일 로깅 & 롤링 정책](#32-파일-로깅--롤링-정책)
4. [개선 전후 비교](#4-개선-전후-비교)

---

## 1. Playwright 병렬 처리

### 1.1 문제 발견: 상세 페이지 60% 실패

#### 문제

2단계 크롤링 구조에서 Phase 2(상세 페이지 보강)를 3개 워커 스레드로 병렬 처리했더니, 전체 공고의 약 60%가 실패했다.

```
[JOBKOREA] Phase 1 완료 - 리스트 60 건 중 신규 60 건, Phase 2 시작 (동시 3개)
[JOBKOREA] 워커-0 시작 (20 건)
[JOBKOREA] 워커-1 시작 (20 건)
[JOBKOREA] 워커-2 시작 (20 건)
[JOBKOREA] 상세 워커 실패: Cannot find object to call __adopt__: page@bc9be664...
[JOBKOREA] 상세 페이지 실패 (시도 1/3): Object doesn't exist: request@603dbcd8...
[JOBKOREA] 상세 페이지 실패 (시도 1/3): timeoutSettings is null
```

세 가지 에러 패턴이 반복됐다:

| 에러 | 발생 시점 | 의미 |
|------|----------|------|
| `Cannot find object to call __adopt__` | `ctx.newPage()` | Page 객체 생성 실패 |
| `Object doesn't exist: request/response@...` | `page.navigate()` | 내부 응답 라우팅 꼬임 |
| `timeoutSettings is null` | `page.setDefaultNavigationTimeout()` | Page가 null 상태로 반환됨 |

4개 사이트 모두 동일한 패턴:

| 사이트 | 발견 | 상세 성공 | 실패율 |
|--------|------|----------|--------|
| JOBKOREA | 60 | ~22 | 63% |
| SARAMIN | 50 | ~34 | 32% |
| LINKAREER | 20 | ~8 | 60% |
| JOBPLANET | 16 | ~11 | 31% |

#### 고민

Java는 멀티스레딩의 대명사다. `ExecutorService`, `CompletableFuture`, `ForkJoinPool` 등 풍부한 동시성 도구가 있고, 대부분의 Java 라이브러리는 thread-safe하게 설계된다.

그런데 Playwright는 Java 네이티브 라이브러리가 아니다. Playwright의 아키텍처를 이해해야 했다.

```
┌─────────────────────────────────────────────────────┐
│  Java 프로세스                                        │
│                                                       │
│  Thread-1 ──┐                                        │
│  Thread-2 ──┼── WebSocket 1개 ──▶ Chromium 프로세스   │
│  Thread-3 ──┘                                        │
│                                                       │
└─────────────────────────────────────────────────────┘
```

Playwright Java는 내부적으로 **단일 WebSocket 연결**을 통해 Chromium 브라우저 프로세스와 통신한다. `newPage()`, `navigate()`, `evaluate()` 같은 모든 API 호출이 이 하나의 파이프를 통과한다.

여러 스레드가 동시에 메시지를 보내면, Chromium에서 돌아오는 응답이 어느 스레드의 요청에 대한 것인지 **라우팅이 꼬인다**. 이것이 `Object doesn't exist` 에러의 근본 원인이다.

---

### 1.2 1차 시도: synchronized 동기화

#### 시도

Playwright의 WebSocket이 thread-safe하지 않다면, Page 생성/소멸만 `synchronized`로 직렬화하면 되지 않을까?

```java
private final Object pageLock = new Object();

private void fetchSingleDetailWithRetry(BrowserContext ctx, ...) {
    Page detailPage;
    synchronized (pageLock) {
        detailPage = ctx.newPage();
        detailPage.setDefaultNavigationTimeout(timeout);
    }
    // navigate()와 enrichFromDetailPage()는 lock 밖에서 병렬 실행
    detailPage.navigate(data.getUrl(), ...);
    parser.enrichFromDetailPage(detailPage, data);

    synchronized (pageLock) {
        detailPage.close();
    }
}
```

의도: `newPage()`/`close()`만 직렬화하고, 실제 네트워크 I/O(navigate)와 DOM 파싱(enrich)은 병렬로 실행.

#### 결과: 실패

```
[JOBKOREA] 상세 워커 실패: timeoutSettings is null
[JOBKOREA] 상세 워커 실패: Cannot find object to call __adopt__: page@c9ce41fd...
[JOBPLANET] 상세 워커 실패: Object doesn't exist: request@8033ebb7...
```

`navigate()` 자체도 WebSocket을 통해 Chromium과 통신하기 때문에, Page 생성만 동기화해서는 부족했다. `navigate()`가 보낸 메시지의 응답이 다른 스레드의 `navigate()` 응답과 뒤섞였다.

모든 Playwright API 호출을 `synchronized`로 감싸면? 그건 사실상 순차 처리와 같으므로 병렬의 의미가 없다.

---

### 1.3 2차 시도: 순차 처리 fallback

#### 시도

병렬을 포기하고, 단일 BrowserContext에서 순차 처리로 변경했다.

```java
private void fetchDetailsSequentially(SiteParser parser, String siteName, List<CrawledJobData> items) {
    try (BrowserContext ctx = playwrightManager.createStealthContext()) {
        for (CrawledJobData data : items) {
            fetchSingleDetail(ctx, parser, siteName, data, waitUntil, timeout);
            playwrightManager.randomDelay(800, 2000);
        }
    }
}
```

#### 결과: 성공하지만 느림

에러는 완전히 사라졌다. 하지만 50건 상세 페이지를 순차 처리하면 약 4~5분이 소요된다. 사이트 4곳이면 16~20분.

병렬로는 약 2분이면 끝나는 작업이었기에, 성능 손실이 컸다.

---

### 1.4 근본 원인 분석: WebSocket 단일 파이프라인

#### 깊이 들어가기

Playwright 공식 GitHub 이슈와 문서를 직접 확인했다.

**Playwright Java - Issue #758** (메인테이너 Yury Semikhatsky 답변):

> "Playwright API is **not thread-safe**. You'll need to ensure that each Playwright instance is accessed from a single thread."

**Playwright Java 공식 문서** (`playwright.dev/java/docs/multithreading`):

> "All its methods as well as methods on all objects created by it (Browser, BrowserContext, Page etc.) are **expected to be called on the same thread** where the Playwright object was created."

**Playwright Python도 동일** - 메인테이너 Pavel Feldman 답변:

> "The sync API is **NOT thread-safe**. Use process-based parallelization."

핵심은 **Playwright 인스턴스 = WebSocket 연결 = 1스레드 전용**이라는 것이다.

```
❌ 현재 (깨지는 구조)
┌──────────────────────────────────────────┐
│  Thread-1 ──┐                            │
│  Thread-2 ──┼── 공유 Browser ── WS ──▶ Chromium │
│  Thread-3 ──┘                            │
└──────────────────────────────────────────┘

✅ 공식 권장 (스레드별 독립 인스턴스)
┌──────────────────────────────────────────┐
│  Thread-1 ── Playwright1 ── WS-A ──▶ Chromium-1 │
│  Thread-2 ── Playwright2 ── WS-B ──▶ Chromium-2 │
└──────────────────────────────────────────┘
```

Browser, BrowserContext, Page는 모두 생성한 Playwright 인스턴스의 WebSocket을 통해 통신한다. 인스턴스를 공유하는 한, 어떤 동기화 기법을 써도 내부 메시지 라우팅 충돌을 완전히 막을 수 없다.

---

### 1.5 최종 해결: 스레드별 독립 Playwright 인스턴스

#### 설계

공식 권장 패턴을 그대로 적용했다. 각 워커 스레드가 자신만의 `Playwright.create()` → `browser.launch()` → `context` → `page`를 소유한다.

```java
// PlaywrightManager 내부 - 독립 워커 팩토리
public class PlaywrightWorker implements AutoCloseable {
    private final Playwright workerPlaywright;
    private final Browser workerBrowser;

    PlaywrightWorker() {
        this.workerPlaywright = Playwright.create();
        this.workerBrowser = workerPlaywright.chromium().launch(launchOptions);
    }

    public BrowserContext createStealthContext() { /* 스텔스 설정 동일 적용 */ }

    @Override
    public void close() {
        workerBrowser.close();
        workerPlaywright.close();
    }
}
```

```java
// PlaywrightScrapingEngine - Phase 2 병렬 처리
private void fetchDetailsWithIsolatedWorkers(SiteParser parser, String siteName, List<CrawledJobData> items) {
    ExecutorService pool = Executors.newFixedThreadPool(concurrency);

    for (int w = 0; w < concurrency; w++) {
        final List<CrawledJobData> batch = workerBatches[w];
        pool.submit(() -> {
            // 각 스레드가 독립 Playwright+Browser 생성
            try (PlaywrightManager.PlaywrightWorker worker = playwrightManager.createIsolatedWorker()) {
                try (BrowserContext ctx = worker.createStealthContext()) {
                    for (CrawledJobData data : batch) {
                        fetchSingleDetail(ctx, parser, siteName, data, waitUntil, timeout);
                    }
                }
            }
        });
    }
}
```

아키텍처:

```
메인 스레드 ── Playwright-Main ── WS-A ──▶ Chromium-1 (리스트 페이지 순회)
워커-0      ── Playwright-W0   ── WS-B ──▶ Chromium-2 (상세 배치 25건)
워커-1      ── Playwright-W1   ── WS-C ──▶ Chromium-3 (상세 배치 25건)
```

#### 결과: 100% 성공

```
[JOBKOREA] 워커-0 시작 (30 건, 독립 브라우저)
[JOBKOREA] 워커-1 시작 (30 건, 독립 브라우저)
[JOBKOREA] 워커-1 완료 (성공 30, 실패 0)
[JOBKOREA] 워커-0 완료 (성공 30, 실패 0)
[JOBKOREA] 상세 페이지 보강 완료 - 성공 60, 실패 0 / 총 60 건
```

| 사이트 | 수집 | 상세 성공 | 실패 |
|--------|------|----------|------|
| JOBKOREA | 60 | **60 (100%)** | 0 |
| SARAMIN | 50 | **50 (100%)** | 0 |
| LINKAREER | 20 | **20 (100%)** | 0 |
| JOBPLANET | 8 | **8 (100%)** | 0 |

`Object doesn't exist`, `timeoutSettings is null` 에러가 완전히 사라졌다.

#### 트레이드오프

| 항목 | 공유 Browser (기존) | 독립 인스턴스 (현재) |
|------|-------------------|-------------------|
| 브라우저 프로세스 수 | 1개 | 3개 (메인+워커2) |
| 메모리 사용량 | ~200MB | ~500MB |
| thread-safety | ❌ | ✅ |
| 상세 페이지 성공률 | ~40% | **100%** |
| 50건 처리 시간 | ~90초 (에러 포함) | ~90초 (에러 없음) |

메모리 300MB 추가 사용은 서버 1대 환경에서 충분히 감당 가능하다. `crawler.detail.concurrency` 설정으로 워커 수를 조절할 수 있어서, 메모리가 부족하면 1로 줄이면 된다.

---

### 1.6 headless 차단 이슈

#### 문제

독립 인스턴스를 `headless(true)`로 생성했더니, 모든 상세 페이지가 `net::ERR_EMPTY_RESPONSE`로 실패했다.

```
[SARAMIN] 상세 페이지 실패 (시도 1/3): net::ERR_EMPTY_RESPONSE
```

메인 `PlaywrightManager`는 `headless(false)`로 되어 있어서 리스트 페이지는 정상이었지만, 워커를 headless로 만들자 사람인 등 채용 사이트가 **headless 브라우저를 감지하고 응답을 거부**했다.

#### 해결

채용 사이트들은 봇 탐지가 민감하다. `navigator.webdriver` 속성 외에도, headless 모드 자체를 Chrome DevTools Protocol 레벨에서 감지하는 사이트가 있다. 워커도 `headless(false)`로 변경하여 해결했다.

```java
this.workerBrowser = workerPlaywright.chromium().launch(
    new BrowserType.LaunchOptions()
        .setHeadless(false)  // headless(true)면 채용 사이트가 차단
        .setArgs(List.of("--disable-blink-features=AutomationControlled", ...)));
```

---

## 2. 크롤링 데이터 품질 개선

### 2.1 이미지 사이즈 필터링

#### 문제

사람인 상세 페이지에서 수집한 이미지에 1x1 픽셀 트래커, 작은 아이콘, 배너 광고가 포함되어 프론트엔드에 깨진 이미지가 표시됐다.

각 사이트의 이미지 필터링 현황:

| 사이트 | 사이즈 필터 | 광고 URL 필터 |
|--------|-----------|-------------|
| 사람인 | ❌ 없음 | `isAdImage()` URL 패턴만 |
| 잡코리아 | `width > 300, height > 200` | URL 필터 있음 |
| 링커리어 | ❌ 없음 | ❌ 없음 |
| 잡플래닛 | - | 이미지 수집 안 함 (텍스트 기반) |

#### 고민

이미지 필터링에 어떤 기준을 써야 할까?

- **너무 엄격하면** (300x200 이상) 모바일 최적화 이미지나 인포그래픽이 누락
- **너무 느슨하면** (50x50 이상) 아이콘, SNS 버튼, 트래커가 포함
- 채용 공고 본문 이미지의 실제 크기를 확인해본 결과, 대부분 최소 200x100 이상

#### 해결

사람인은 Java Locator의 `boundingBox()` API로 렌더링된 실제 크기를 체크:

```java
private void extractImagesFromLocator(Locator images, List<String> imageUrls) {
    for (int i = 0; i < images.count(); i++) {
        Locator img = images.nth(i);
        String src = img.getAttribute("src");
        if (src == null || !src.startsWith("http") || isAdImage(src)) continue;

        try {
            var box = img.boundingBox();
            if (box != null && (box.width < 200 || box.height < 100)) continue;
        } catch (Exception ignored) {}

        imageUrls.add(src);
    }
}
```

링커리어는 JavaScript `evaluate()` 내에서 필터:

```javascript
result.images = Array.from(imgs)
    .filter(img => img.width > 200 && img.height > 100)
    .map(img => img.src)
    .filter(s => s.startsWith('http'))
    .slice(0, 10);
```

잡플래닛은 Playwright MCP로 실제 사이트를 확인한 결과, 상세 페이지에 공고 본문 이미지가 존재하지 않았다 (모두 3px 투명 플레이스홀더). 수집 대상에서 제외하는 것이 올바른 판단.

---

### 2.2 iframe fallback 쓰레기 데이터 방지

#### 문제

잡코리아 일부 공고에서 회사명이 "검색"으로 저장되고, description에 잡코리아 홈페이지 전체 텍스트(네비게이션, 광고, 다른 공고 목록 포함)가 들어갔다.

```sql
SELECT company, LEFT(description, 100) FROM job_postings WHERE id = 1176;
-- company: "검색"
-- description: "지역 전체검색어를 입력해 주세요.검색회원가입/로그인기업 서비스JOB 찾기..."
```

#### 원인

잡코리아 상세 페이지는 Next.js 기반으로, 공고 본문이 iframe(`GI_Read_Comt_Ifrm`) 안에 있다. iframe 내에서 `.secDetailWrap` 셀렉터로 본문을 추출하는데, 이 셀렉터를 찾지 못할 경우의 **fallback 로직**이 문제였다:

```javascript
// 기존 fallback: body 전체를 가져옴
const clone = document.body.cloneNode(true);
clone.querySelectorAll('script, style, noscript').forEach(el => el.remove());
result.text = clone.innerText;  // ← 네비게이션, 광고 등 전부 포함
```

회사명 `h2` 태그에서 추출하는 로직도, 상세 페이지가 아닌 메인 페이지의 검색창 텍스트 "검색"을 가져왔다.

#### 해결

fallback에서 body 전체를 긁는 대신, `.secDetailWrap`을 못 찾으면 빈 문자열을 반환하도록 변경:

```javascript
const detail = document.querySelector('.secDetailWrap');
if (!detail) return { text: '', images: [] };  // body fallback 제거

result.text = detail.innerText.trim();
```

회사명도 방어 로직 추가:

```java
String detailCompany = (String) detail.getOrDefault("company", "");
if (!detailCompany.isEmpty() && !detailCompany.equals("검색") && detailCompany.length() > 1) {
    data.setCompany(detailCompany);
}
```

`.secDetailWrap`이 없는 공고는 리스트 데이터(제목, 회사명, 경력 등)만 저장되고 description은 비게 되는데, 잘못된 데이터가 저장되는 것보다 훨씬 낫다.

---

### 2.3 HTML 공백 아티팩트 정리

#### 문제

잡코리아 iframe 본문에서 추출한 텍스트에 HTML 소스코드의 들여쓰기가 그대로 남아 있었다:

```
(주)한솔안전 화성점


                    안전시설물 설치/해체 직원 모집



```

#### 원인

`innerText`는 DOM의 텍스트 콘텐츠를 반환하지만, 원본 HTML의 들여쓰기 공백까지 포함한다. 특히 잡코리아 iframe은 서버 사이드 렌더링된 HTML을 그대로 제공하는데, 들여쓰기가 깊어서 공백이 대량으로 섞인다.

#### 해결

Java의 `String.lines()` 스트림으로 각 줄 정리:

```java
iframeContent = iframeContent.lines()
    .map(String::strip)           // 각 줄 앞뒤 공백 제거
    .filter(line -> !line.isEmpty()) // 빈 줄 제거
    .reduce((a, b) -> a + "\n" + b)
    .orElse("");
iframeContent = iframeContent.replaceAll("\\n{3,}", "\n\n"); // 연속 줄바꿈 압축
```

정리 후:

```
(주)한솔안전 화성점
안전시설물 설치/해체 직원 모집
포지션 및 자격요건
안전시설물 설치/해체 ( 10명 )
```

---

## 3. 운영 개선

### 3.1 Hibernate SQL 로그 폭주 해결

#### 문제

크롤링 로그를 확인하려 했으나, Hibernate SQL DEBUG 로그가 콘솔을 도배해서 크롤링 진행 상황을 전혀 볼 수 없었다.

```properties
# 기존 설정 - SQL이 이중 출력됨
spring.jpa.show-sql=true                    # Hibernate 직접 출력
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG       # Logback 통해 다시 출력
```

`show-sql=true`와 `hibernate.SQL=DEBUG`가 동시에 켜져 있어서 **같은 SQL이 2번씩** 출력됐다. 공고 목록 조회 1번에 SQL 40줄 × 2 = 80줄이 로그에 찍히니, 크롤링 로그 1줄을 보려면 수백 줄을 스크롤해야 했다.

#### 해결

```properties
# 개선 후
spring.jpa.show-sql=false                   # Hibernate 직접 출력 끔
logging.level.org.hibernate.SQL=WARN        # WARN 이상만
logging.level.org.hibernate.orm.jdbc.bind=WARN
```

---

### 3.2 파일 로깅 & 롤링 정책

#### 문제

서버 로그가 콘솔(stdout)으로만 출력되어, 크롤링 후 결과를 확인하려면 서버를 실행한 터미널 창을 찾아야 했다. 터미널 버퍼를 넘으면 이전 로그는 유실됐다.

#### 해결

Spring Boot의 내장 Logback 파일 롤링을 설정:

```properties
# dev
logging.file.name=logs/job-crawler.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=7
logging.logback.rollingpolicy.total-size-cap=100MB

# prod
logging.logback.rollingpolicy.max-history=30
logging.logback.rollingpolicy.total-size-cap=500MB
```

- 10MB마다 파일 롤링, dev는 7일/prod는 30일 보관
- `.gitignore`에 `logs/` 추가

---

## 4. 개선 전후 비교

### Playwright 병렬 처리

| 지표 | Before | After |
|------|--------|-------|
| 상세 페이지 성공률 | ~40% | **100%** |
| 에러 유형 | `Object doesn't exist` 등 3종 | 없음 |
| 50건 처리 시간 | ~90초 (실패 포함) | ~90초 (전부 성공) |
| 브라우저 프로세스 | 1개 (공유) | 3개 (독립) |
| 실질 수집량 | 약 55건/사이트 | **약 138건/4사이트** |

### 데이터 품질

| 지표 | Before | After |
|------|--------|-------|
| 이미지 필터링 (사람인) | URL 패턴만 | URL + 사이즈(200x100) |
| iframe fallback | body 전체 수집 | 빈 문자열 반환 |
| 공백 아티팩트 | HTML 들여쓰기 그대로 | strip + 압축 |
| 회사명 오류 ("검색") | 방어 없음 | 무효값 필터 |

### 핵심 교훈

1. **라이브러리의 스레딩 모델을 확인하라** — Java가 thread-safe해도, Java에서 사용하는 라이브러리가 thread-safe하다는 보장은 없다. Playwright처럼 외부 프로세스와 IPC(WebSocket)로 통신하는 라이브러리는 특히 주의가 필요하다.

2. **synchronized는 만능이 아니다** — 내부 상태를 동기화할 수는 있지만, 외부 프로세스와의 메시지 라우팅까지 동기화할 수는 없다. 근본 원인을 파악하지 않고 Lock을 추가하면 문제만 복잡해진다.

3. **공식 문서의 권장 패턴에는 이유가 있다** — Playwright 공식 문서가 "스레드마다 별도 인스턴스"를 권장하는 것은 WebSocket 단일 파이프라인이라는 아키텍처적 제약 때문이다. 이를 무시하고 우회하려 하면 불안정한 코드만 남는다.

4. **쓰레기 데이터보다 빈 데이터가 낫다** — fallback 로직이 "무조건 뭔가를 반환"하려 하면 잘못된 데이터가 DB에 쌓인다. 차라리 빈 값을 반환하고 리스트 데이터만 저장하는 것이 데이터 품질 면에서 훨씬 건전하다.
