# 취업 자동화 플랫폼 개발기: 하루 동안 마주친 9가지 문제와 해결 과정

## 목차
1. [꺼놨는데 왜 크롤링이 돌아? — 스케줄러 상태 휘발성 문제](#1-꺼놨는데-왜-크롤링이-돌아--스케줄러-상태-휘발성-문제)
2. [Redis가 날아가면 알림이 또 와요 — 알림 중복 방지 영속화](#2-redis가-날아가면-알림이-또-와요--알림-중복-방지-영속화)
3. [세션도 Redis에 있었다 — 외부 사이트 세션의 이중 저장](#3-세션도-redis에-있었다--외부-사이트-세션의-이중-저장)
4. [일반 유저는 어떻게 연동하지? — 일회용 로그인 설계](#4-일반-유저는-어떻게-연동하지--일회용-로그인-설계)
5. [사람인 로그인 버튼이 안 눌려요 — jQuery 이벤트와 Playwright의 충돌](#5-사람인-로그인-버튼이-안-눌려요--jquery-이벤트와-playwright의-충돌)
6. [표가 깨져요 — innerText에서 innerHTML로의 전환](#6-표가-깨져요--innertext에서-innerhtml로의-전환)
7. [글씨가 안 보여요 — 인라인 CSS와 다크모드 충돌](#7-글씨가-안-보여요--인라인-css와-다크모드-충돌)
8. [크롤링하면 트랜잭션 롤백 에러 — @Transactional의 함정](#8-크롤링하면-트랜잭션-롤백-에러--transactional의-함정)
9. [30분마다 로그아웃 — JWT 자동 갱신 누락](#9-30분마다-로그아웃--jwt-자동-갱신-누락)

---

## 1. 꺼놨는데 왜 크롤링이 돌아? — 스케줄러 상태 휘발성 문제

### 문제

관리자 대시보드에서 크롤링 스케줄을 off로 꺼놨는데, 서버를 재시작하면 다시 9시, 2시에 크롤링이 돌았다.

### 원인

스케줄러의 on/off 상태가 `AtomicBoolean`으로 JVM 메모리에만 저장되어 있었다.

```java
// CrawlerScheduler.java - 기존 코드
private final AtomicBoolean enabled = new AtomicBoolean(true); // 항상 true로 시작

public boolean toggleEnabled() {
    boolean newState = !enabled.get();
    enabled.set(newState);
    return newState;
}
```

프론트에서 toggle API를 호출하면 메모리의 `enabled` 값이 바뀌지만, **서버가 재시작되면 `true`로 리셋**된다. DB나 설정 파일에 저장하는 로직이 없었다.

### 고민

두 가지 방법을 검토했다.

1. **application.properties에 저장** — 간단하지만, 런타임에 properties 파일을 수정하는 건 Spring의 설계 철학에 맞지 않는다. 재시작 시 로드되는 정적 설정이지, 동적 상태를 저장하는 곳이 아니다.

2. **DB에 저장** — 스케줄러 설정 전용 테이블을 만들어서 `enabled`, `crawlCron1`, `crawlCron2`, `maxPages`를 영속화한다.

### 해결

`SchedulerConfig` 엔티티를 만들어서 DB에 저장했다. 싱글 row 패턴으로, 테이블에 항상 1개의 row만 존재한다.

```java
@Entity
@Table(name = "scheduler_config")
public class SchedulerConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String crawlCron1;

    @Column(nullable = false)
    private String crawlCron2;

    @Column(nullable = false)
    private int maxPages;

    @Column(nullable = false)
    private boolean enabled;

    public static SchedulerConfig createDefault() {
        return new SchedulerConfig("0 0 9 * * MON-FRI",
                                    "0 0 14 * * MON-FRI", 50, true);
    }

    public boolean toggleEnabled() {
        this.enabled = !this.enabled;
        return this.enabled;
    }
}
```

`CrawlerScheduler`는 시작 시 DB에서 설정을 로드하고, 매 실행 시점마다 `enabled`를 DB에서 확인한다.

```java
@PostConstruct
public void init() {
    SchedulerConfig config = loadOrCreateConfig();
    scheduleCrawl("crawl1", config.getCrawlCron1());
    scheduleCrawl("crawl2", config.getCrawlCron2());
}

private void scheduleCrawl(String taskId, String cron) {
    scheduleTask(taskId, cron, () -> {
        SchedulerConfig config = loadOrCreateConfig();
        if (!config.isEnabled()) {
            log.info("=== [스케줄] 자동 크롤링 비활성화 상태 - 건너뜀 ===");
            return;
        }
        // 크롤링 실행...
    });
}

private SchedulerConfig loadOrCreateConfig() {
    return schedulerConfigRepository.findById(1L)
            .orElseGet(() -> schedulerConfigRepository.save(
                SchedulerConfig.createDefault()));
}
```

### 결과

서버를 재시작해도 off 상태가 유지된다. 실제로 재시작 후 로그에서 `enabled: false`가 출력되는 것을 확인했다.

```
[스케줄러] 초기화 - 크롤링: 0 0 9 * * MON-FRI, 0 0 14 * * MON-FRI / enabled: false
```

---

## 2. Redis가 날아가면 알림이 또 와요 — 알림 중복 방지 영속화

### 문제

유저에게 새 공고 매칭 알림을 디스코드 웹훅으로 보내는데, **이미 보낸 알림을 또 보내는** 문제가 있었다. Redis를 재시작하거나 키가 만료되면 중복 알림이 발생했다.

### 원인

알림 발송 이력을 Redis에만 저장하고 있었다.

```java
// 기존 코드
String redisKey = "notified:job:" + user.getId() + ":" + job.getId();
if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) continue;

// 알림 발송 후
redisTemplate.opsForValue().set(redisKey, "1", Duration.ofDays(7));
```

Redis는 인메모리 데이터 스토어다. 재시작하면 데이터가 날아가고, TTL 7일이 지나면 키가 삭제된다. 7일 전에 보낸 알림과 동일한 공고가 아직 유효하면 다시 알림이 간다.

### 고민

Redis에 RDB/AOF 영속화를 설정하는 방법도 있지만, 알림 이력은 **영구적으로 보관해야 하는 데이터**이므로 DB가 맞다.

### 해결

`NotificationHistory` 엔티티를 만들고 `(userId, jobPostingId)` 유니크 제약을 걸었다.

```java
@Entity
@Table(name = "notification_history",
        uniqueConstraints = @UniqueConstraint(
            columnNames = {"userId", "jobPostingId"}))
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long jobPostingId;

    @Column(nullable = false)
    private LocalDateTime notifiedAt;
}
```

서비스에서 Redis 대신 DB를 조회한다.

```java
// Before: Redis
if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) continue;
redisTemplate.opsForValue().set(redisKey, "1", NOTIFIED_TTL);

// After: DB
if (notificationHistoryRepository.existsByUserIdAndJobPostingId(
        user.getId(), job.getId())) continue;
notificationHistoryRepository.save(
    NotificationHistory.of(user.getId(), job.getId()));
```

### 교훈

**"이 데이터가 날아가면 어떤 일이 일어나는가?"**를 기준으로 저장소를 선택해야 한다.

- 날아가도 되는 데이터 (캐시, 임시 세션) → Redis
- 날아가면 안 되는 데이터 (이력, 설정, 상태) → DB

---

## 3. 세션도 Redis에 있었다 — 외부 사이트 세션의 이중 저장

### 문제

사람인/잡플래닛 등 외부 사이트의 로그인 세션(쿠키)을 Redis에만 저장하고 있었다. Redis가 재시작되면 유저가 다시 로그인해야 했다.

### 원인

`AuthSessionManager`가 Redis만 사용했다.

```java
// 기존: Redis에만 저장
public void saveSessionString(Long userId, String site, String cookiesJson) {
    String key = buildSessionKey(userId, site);
    redisTemplate.opsForValue().set(key, cookiesJson, 24, TimeUnit.HOURS);
}

public String getSession(Long userId, String site) {
    return redisTemplate.opsForValue().get(buildSessionKey(userId, site));
}
```

### 해결

DB를 원본으로, Redis를 캐시로 사용하는 **Cache-Aside 패턴**을 적용했다.

```java
// 저장: DB(원본) + Redis(캐시) 동시 저장
@Transactional
public void saveSessionString(Long userId, String site, String cookiesJson) {
    // DB 저장 (원본)
    SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
    externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
            .ifPresent(account -> account.updateSessionCookies(cookiesJson));

    // Redis 캐시
    String key = buildSessionKey(userId, site);
    redisTemplate.opsForValue().set(key, cookiesJson, 24, TimeUnit.HOURS);
}

// 조회: Redis → 없으면 DB에서 복구
@Transactional(readOnly = true)
public String getSession(Long userId, String site) {
    String key = buildSessionKey(userId, site);
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) return cached;

    // Redis에 없으면 DB에서 복구
    SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
    return externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
            .filter(ExternalAccount::hasValidSession)
            .map(account -> {
                String cookies = account.getSessionCookies();
                // Redis에 다시 캐시
                redisTemplate.opsForValue().set(key, cookies, 24, TimeUnit.HOURS);
                return cookies;
            })
            .orElse(null);
}
```

이미 `ExternalAccount` 엔티티에 `sessionCookies` 컬럼이 있었지만 활용하지 않고 있었다. 기존 인프라를 최대한 활용한 수정이다.

---

## 4. 일반 유저는 어떻게 연동하지? — 일회용 로그인 설계

### 문제

외부 채용 사이트 연동(자동 지원에 필요)을 위해서는 유저의 사이트 로그인 세션이 필요하다. 기존에는 서버에서 Playwright headed 브라우저를 띄워서 유저가 직접 로그인하는 방식이었는데, 이건 **서버 앞에 앉아있는 사람만** 가능하다.

### 고민: 세 가지 선택지

"유저의 로그인 세션을 서버가 어떻게 확보하는가"가 핵심이다.

**선택지 1: 브라우저 확장 프로그램**
유저가 Chrome 확장을 설치하고, 본인 브라우저에서 사이트에 로그인한 뒤, 확장이 쿠키를 캡처해서 서버로 전송한다.

- 장점: 비밀번호가 서버를 거치지 않음, 소셜 로그인도 지원
- 단점: 확장 설치 필요

**선택지 2: 일회용 로그인**
유저가 사이트 ID/PW를 입력하면, 서버가 Playwright headless로 한 번 로그인하고, 쿠키만 저장한 뒤 비밀번호를 즉시 폐기한다.

- 장점: 설치 없음, UX 가장 간단
- 단점: 비밀번호가 서버를 한 번 거침 (HTTPS로 암호화), 소셜 로그인 불가

**선택지 3: OAuth**
채용 사이트가 OAuth API를 제공하면 가장 이상적이지만, **사람인/잡코리아/잡플래닛/링커리어 어디에도 OAuth가 없다**. 조사 결과 공고 검색 API만 존재.

### 선택: 둘 다 구현

- 소셜 로그인(카카오/네이버) 유저 → 확장 프로그램
- ID/PW 로그인 유저 → 일회용 로그인

```java
// 일회용 로그인 - 비밀번호는 메서드 스코프를 벗어나면 GC 대상
@Transactional
public boolean onetimeLogin(Long userId, SourceSite site,
                             String loginId, String password) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

    boolean success = autoApplyRobot.loginAndSaveSession(
            userId, site.name(), loginId, password);
    // password는 이 메서드 스코프를 벗어나면 GC 대상
    // 어디에도 저장하지 않음

    if (success) {
        String cookies = authSessionManager.getSession(userId, site.name());
        externalAccountRepository.findByUserIdAndSite(userId, site)
                .ifPresentOrElse(
                    existing -> existing.updateSessionCookies(cookies),
                    () -> externalAccountRepository.save(
                        ExternalAccount.createCookieSession(user, site, cookies)));
    }
    return success;
}
```

---

## 5. 사람인 로그인 버튼이 안 눌려요 — jQuery 이벤트와 Playwright의 충돌

### 문제

일회용 로그인을 구현했는데, Playwright가 사람인 로그인 폼에 ID/PW를 입력하고 로그인 버튼을 클릭해도 **로그인이 안 됐다**.

### 원인

사람인 로그인 폼을 조사하니:

```html
<form onsubmit="try {return check(this);} catch (e) {};">
    <input type="text" id="id" class="txt_tool">
    <input type="password" id="password" class="txt_tool">
    <button type="submit" class="btn_login BtnType SizeML">로그인</button>
</form>
```

`onsubmit`에 `check(this)` 함수가 있다. 이 함수가 폼 유효성 검사를 하는데, Playwright의 `fill()` 메서드는 **DOM의 value 속성을 직접 변경**한다. 이때 jQuery의 `keyup`, `input`, `change` 이벤트가 발생하지 않아서 `check()` 함수가 "입력값이 비어있다"고 판단한다.

### 해결

`fill()` 대신 `click()` + `type()`을 사용하면 **실제 키보드 입력처럼** 한 글자씩 타이핑된다. 각 글자마다 `keydown`, `keypress`, `input`, `keyup` 이벤트가 발생한다.

```java
// Before: jQuery 이벤트가 발생하지 않음
idInput.fill(loginId);
pwdInput.fill(password);

// After: 실제 키 입력처럼 동작
idInput.click();
idInput.type(loginId, new Locator.TypeOptions().setDelay(50));
pwdInput.click();
pwdInput.type(password, new Locator.TypeOptions().setDelay(50));
```

`setDelay(50)`은 각 글자 사이에 50ms 간격을 둔다. 너무 빠르면 사이트의 입력 핸들러가 따라오지 못할 수 있다.

### 교훈

Playwright의 `fill()`과 `type()`은 동작 방식이 완전히 다르다.

| | `fill()` | `type()` |
|--|----------|----------|
| 동작 | DOM value 직접 설정 | 키보드 이벤트 시뮬레이션 |
| 속도 | 즉시 | 글자 수 × delay |
| 이벤트 | `input`, `change`만 | `keydown`, `keypress`, `input`, `keyup` 전부 |
| jQuery 호환 | 안 되는 경우 있음 | 대부분 호환 |

**jQuery나 레거시 JS를 사용하는 사이트**에서는 `type()`이 안전하다.

---

## 6. 표가 깨져요 — innerText에서 innerHTML로의 전환

### 문제

사람인에서 크롤링한 공고의 모집부문 표가 완전히 깨져서 읽을 수 없었다.

### 원인

`innerText()`가 HTML 테이블 구조를 파괴한다. `<td>` 사이에 탭(`\t`)을, `<br>` 위치에 줄바꿈(`\n`)을 넣어서 반환하는데, 셀 안에 줄바꿈이 있으면 행/열 구분이 불가능해진다.

### 시행착오

프론트에서만 해결하려고 4번 시도했다.

1. **탭 → 마크다운 테이블 변환** → 멀티라인 셀 때문에 깨짐
2. **탭 → HTML 테이블 컴포넌트** → 행 병합 부정확
3. **탭 줄 숨기기** → 데이터 손실 ("재무관리" 등 안 보임)
4. **탭 → 파이프 구분자** → 보기 나쁨

전부 실패한 후 크롤링 방식 자체를 바꾸기로 결정.

### 해결: innerHTML + 이중 소독

```java
// 크롤러: innerText() → innerHTML()
String html = userContent.first().innerHTML();
descBuilder.append(HtmlSanitizer.sanitize(html));
```

```java
// 서버 소독 (Jsoup)
public static String sanitize(String dirtyHtml) {
    String cleaned = Jsoup.clean(dirtyHtml, SAFELIST);
    return stripColorStyles(cleaned);
}
```

```tsx
// 클라이언트 소독 (DOMPurify) + 렌더링
function HtmlRenderer({ html }: { html: string }) {
    const clean = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ["table", "tr", "td", "th", "p", "br",
                       "b", "strong", "ul", "ol", "li", ...],
    });
    return <div dangerouslySetInnerHTML={{ __html: clean }} />;
}
```

프론트에서는 HTML 여부를 자동 감지해서 기존 텍스트 데이터와 호환 유지한다.

```tsx
function ContentRenderer({ text }: { text: string }) {
    const isHtml = text.includes("<table") || text.includes("<div");
    if (isHtml) return <HtmlRenderer html={text} />;
    return <Markdown>{text}</Markdown>;
}
```

### 교훈

**데이터 수집 단계에서 정보를 버리면 복구할 수 없다.** 가능한 한 원본에 가까운 형태로 수집하고, 표시 단계에서 가공해야 한다.

---

## 7. 글씨가 안 보여요 — 인라인 CSS와 다크모드 충돌

### 문제

innerHTML로 전환한 뒤, 일부 공고에서 **글씨가 전혀 안 보이는** 현상이 발생했다.

### 원인

사람인 공고 작성자가 HTML 에디터에서 직접 지정한 인라인 스타일:

```html
<p style="color: #000000">모집 분야</p>
<div style="background: #ffffff; color: #333333">상세 내용</div>
```

우리 사이트는 다크모드(배경 `#0f172a`)인데, `color: #000000`(검은색)이 인라인으로 박혀있으면 **검은 배경에 검은 글씨**가 된다.

### 고민

1. **style 속성 전부 제거** — 색상 문제는 해결되지만, `text-align: center`, `width` 같은 레이아웃 속성까지 날아간다.
2. **색상 관련 속성만 선택적으로 제거** — 레이아웃은 유지하면서 색상만 우리 테마를 따르게 한다.

### 해결

정규식으로 `color`, `background`, `background-color` 속성만 제거한다.

```java
private static String stripColorStyles(String html) {
    Document doc = Jsoup.parse(html);
    for (Element el : doc.select("[style]")) {
        String style = el.attr("style");
        String stripped = style
                .replaceAll("(?i)\\bcolor\\s*:[^;]+;?", "")
                .replaceAll("(?i)\\bbackground-color\\s*:[^;]+;?", "")
                .replaceAll("(?i)\\bbackground\\s*:[^;]+;?", "")
                .trim();
        if (stripped.isEmpty()) {
            el.removeAttr("style");
        } else {
            el.attr("style", stripped);
        }
    }
    return doc.body().html();
}
```

정규식 `(?i)\\bcolor\\s*:[^;]+;?` 해석:
- `(?i)` — 대소문자 무시 (`Color`, `COLOR` 모두 매칭)
- `\\b` — 단어 경계. `background-color`의 `color`와 구분하기 위해 필요
- `\\s*:` — 콜론 앞 공백 허용 (`color :` 도 매칭)
- `[^;]+` — 세미콜론 전까지 모든 값 (`#000000`, `rgb(0,0,0)`, `black` 등)
- `;?` — 마지막 속성이면 세미콜론이 없을 수 있음

---

## 8. 크롤링하면 트랜잭션 롤백 에러 — @Transactional의 함정

### 문제

관리자 대시보드에서 크롤링을 실행하면 다음 에러가 발생했다:

```
UnexpectedRollbackException: Transaction silently rolled back
because it has been marked as rollback-only
```

### 원인

`crawlAll()` 메서드에 `@Transactional`이 걸려있었다.

```java
@Transactional  // 문제의 원인
public int crawlAll(String keyword, String jobCategory, int maxPages) {
    for (JobScraper scraper : scrapers) {
        try {
            List<CrawledJobData> data = scraper.scrapeJobs(...);
            total += saveNewPostingsInBatch(data);
        } catch (Exception e) {
            log.error("[{}] 크롤링 실패: {}", scraper.getSiteName(), e.getMessage());
            // 여기서 catch해도 트랜잭션은 이미 rollback-only 마킹됨
        }
    }
    return total;
    // 여기서 commit 시도 → rollback-only 상태라 UnexpectedRollbackException 발생
}
```

Spring의 `@Transactional` 기본 동작:

1. 메서드 시작 시 트랜잭션 시작
2. **RuntimeException이 발생하면** 트랜잭션을 `rollback-only`로 마킹
3. `catch`로 예외를 잡아도 마킹은 취소되지 않음
4. 메서드가 정상 종료되어 `commit`을 시도하면 `rollback-only` 상태이므로 `UnexpectedRollbackException` 발생

크롤링은 네트워크 I/O가 많아서 예외가 빈번하다. 하나의 사이트에서 예외가 발생하면 다른 사이트의 결과까지 전부 롤백된다.

### 해결

크롤링 메서드에서 `@Transactional`을 제거했다. `saveAll()`은 Spring Data JPA의 `SimpleJpaRepository`에 이미 `@Transactional`이 달려있어서 저장은 자체적으로 트랜잭션 처리된다.

```java
// @Transactional 제거
public int crawlAll(String keyword, String jobCategory, int maxPages) {
    for (JobScraper scraper : scrapers) {
        try {
            List<CrawledJobData> data = scraper.scrapeJobs(...);
            total += saveNewPostingsInBatch(data);
            // saveAll()은 자체 트랜잭션으로 커밋됨
        } catch (Exception e) {
            log.error("[{}] 크롤링 실패: {}", scraper.getSiteName(), e.getMessage());
            // 예외가 나도 이미 저장된 건 유지됨
        }
    }
    return total;
}
```

### 교훈

**`@Transactional`은 "이 메서드의 모든 DB 작업이 전부 성공하거나 전부 실패해야 한다"는 의미**이다. 크롤링처럼 독립적인 작업들의 묶음에는 적합하지 않다.

적용 기준:
- **써야 할 때**: 주문 생성 (주문 + 재고 차감 + 결제가 원자적이어야 함)
- **쓰면 안 될 때**: 크롤링 (사이트 A 실패가 사이트 B 저장에 영향을 주면 안 됨)

---

## 9. 30분마다 로그아웃 — JWT 자동 갱신 누락

### 문제

사이트를 사용하다 보면 약 30분마다 로그아웃이 되어 다시 로그인해야 했다.

### 원인

JWT Access Token의 만료 시간이 30분(1,800,000ms)이었고, 프론트에서 토큰 만료 시 **refresh를 시도하지 않고 바로 로그아웃**시키고 있었다.

```typescript
// 기존 코드 - 만료되면 바로 로그아웃
useEffect(() => {
    const interval = setInterval(() => {
        if (isTokenExpired()) {
            logout();                      // refresh 시도 없이 바로 로그아웃
            window.location.href = "/login";
        }
    }, 60_000);
    return () => clearInterval(interval);
}, [token]);
```

Refresh Token(7일 유효)이 localStorage에 저장되어 있었지만 **사용하지 않고 있었다**.

### 해결

만료 2분 전에 자동으로 refresh하도록 수정했다.

```typescript
const refreshToken = useCallback(async (): Promise<boolean> => {
    if (refreshingRef.current) return false; // 중복 갱신 방지
    refreshingRef.current = true;

    const saved = localStorage.getItem("refreshToken");
    if (!saved) { refreshingRef.current = false; return false; }

    try {
        const result = await authApi.refresh(saved);
        localStorage.setItem("token", result.accessToken);
        if (result.refreshToken)
            localStorage.setItem("refreshToken", result.refreshToken);
        const expiry = Date.now() + (result.expiresIn * 1000);
        localStorage.setItem("tokenExpiry", String(expiry));
        setToken(result.accessToken);
        return true;
    } catch {
        return false;
    } finally {
        refreshingRef.current = false;
    }
}, []);

// 만료 2분 전에 자동 갱신
const isTokenExpiringSoon = useCallback(() => {
    const expiry = localStorage.getItem("tokenExpiry");
    if (!expiry) return false;
    return Date.now() > parseInt(expiry) - 120_000; // 2분 전
}, []);

useEffect(() => {
    if (!token) return;
    const interval = setInterval(async () => {
        if (isTokenExpiringSoon()) {
            const ok = await refreshToken();
            if (!ok) { logout(); window.location.href = "/login"; }
        }
    }, 60_000);
    return () => clearInterval(interval);
}, [token]);
```

핵심 포인트:
- **만료 전에 갱신**: 만료 후 갱신하면 이미 401이 발생한 상태라 UX가 나쁘다. 2분 전에 미리 갱신한다.
- **중복 방지**: `refreshingRef`로 여러 탭/컴포넌트에서 동시에 refresh 요청하는 것을 막는다.
- **페이지 로드 시 복구**: 이미 만료된 상태로 페이지를 열면 refresh를 시도하고, 실패하면 로그아웃한다.

### 결과

Refresh Token이 유효한 7일 동안은 로그아웃 없이 사용할 수 있다. Access Token은 28분마다 자동 갱신된다.

---

## 마무리

하루 동안 마주친 9가지 문제의 공통된 패턴이 있다.

### 패턴 1: 휘발성 데이터의 함정
스케줄러 상태, 알림 이력, 세션 — 전부 "날아가면 안 되는 데이터"를 휘발성 저장소(메모리, Redis)에 넣어서 생긴 문제다. **데이터의 생명주기와 저장소의 특성이 일치하는지** 확인해야 한다.

### 패턴 2: 수집 단계에서의 정보 손실
`innerText()`가 HTML 구조를 파괴한 것처럼, 데이터를 처음 가져올 때 정보를 버리면 나중에 복구할 수 없다. **원본에 가까운 형태로 수집하고, 표시 단계에서 가공**하는 것이 원칙이다.

### 패턴 3: 외부 시스템의 동작을 가정하지 말 것
jQuery의 이벤트 핸들링, 사람인의 인라인 스타일, Spring의 트랜잭션 마킹 — 전부 "이렇게 동작할 것이다"라는 가정이 틀린 경우다. **실제로 테스트하고, 실패하면 원인을 파악**하는 반복이 필요하다.
