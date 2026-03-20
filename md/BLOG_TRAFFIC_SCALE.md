# 서버 1대에서 대규모 트래픽을 고려한 설계 경험

> 이끼잡 프로젝트에서 서버 1대(4GB RAM, 2코어)라는 제약 속에서 대규모 트래픽을 견딜 수 있는 구조를 설계한 과정을 정리합니다.
> 각 문제를 발견하고, 선택지를 비교하고, 해결하고, 검증한 전체 흐름을 기술 블로그 형태로 상세히 기록합니다.

---

## 1. AI 요청 Semaphore 동시 제한

### 문제

이끼잡은 채용 공고별로 AI 적합률 분석, 자소서 생성, 포트폴리오 생성, 기업 분석 등 다양한 AI 기능을 제공합니다. 문제는 이 모든 요청이 외부 AI API(OpenClaw)를 호출한다는 점입니다.

만약 10명의 유저가 동시에 자소서 생성을 요청하면 어떻게 될까요?

1. **OpenClaw API 과부하**: 외부 API에 10개의 요청이 동시에 몰리면 응답 지연 또는 429(Too Many Requests) 에러가 발생합니다.
2. **톰캣 스레드 고갈**: Spring Boot 내장 톰캣의 기본 스레드 풀은 200개입니다. AI 요청 하나가 30초~1분 걸리므로, 동시 요청이 쌓이면 다른 일반 API(공고 목록 조회, 로그인 등)까지 응답 불가 상태가 됩니다.
3. **연쇄 장애**: 톰캣 스레드가 AI 요청에 점유되면 헬스체크 실패 → 로드밸런서 제외 → 서비스 전체 장애로 이어집니다.

### 고민: 어떤 방식으로 제한할 것인가

| 방식 | 장점 | 단점 | 판단 |
|------|------|------|------|
| **Rate Limiting (IP당 분당 N회)** | 구현 간단, 악의적 요청 차단 | AI 요청만 선별 제한 어려움. 이미 전체 API에 적용 중 | 부적합 |
| **메시지 큐 (Redis Queue)** | 순서 보장, 확장성 좋음 | 서버 1대에서 큐 컨슈머 별도 관리 복잡 | 과도한 설계 |
| **java.util.concurrent.Semaphore** | JVM 내부에서 동시 실행 수 직접 제한, 대기/타임아웃 지원 | 분산 환경에서는 서버별 독립 카운트 | 서버 1대이므로 최적 |

**결론**: 서버가 1대이므로 JVM 내부의 `Semaphore`가 가장 단순하고 효과적입니다. 분산 환경이 되면 Redis 기반 분산 세마포어로 전환하면 됩니다.

### 해결: Semaphore(5)로 동시 AI 요청 제한

`OpenClawAiGenerator.callHttpApi()` 메서드에 Semaphore를 적용했습니다.

**핵심 코드** (`OpenClawAiGenerator.java`):

```java
// AI API 동시 요청 제한 (서버 과부하 방지)
private static final int MAX_CONCURRENT_AI_REQUESTS = 5;
private final java.util.concurrent.Semaphore aiSemaphore =
        new java.util.concurrent.Semaphore(MAX_CONCURRENT_AI_REQUESTS);

private String callHttpApi(String prompt, List<String> imageUrls) throws Exception {
    // 30초 대기 후에도 슬롯을 얻지 못하면 사용자에게 안내 메시지
    if (!aiSemaphore.tryAcquire(30, java.util.concurrent.TimeUnit.SECONDS)) {
        throw new RuntimeException(
            "AI 요청이 많아 대기 시간을 초과했습니다. 잠시 후 다시 시도해주세요.");
    }
    try {
        return callHttpApiInternal(prompt, imageUrls);
    } finally {
        aiSemaphore.release();  // 반드시 반환 (try-finally 필수)
    }
}
```

**동작 흐름**:

```
유저 A 요청 → Semaphore.acquire() → 슬롯 1/5 사용 → AI API 호출 중...
유저 B 요청 → Semaphore.acquire() → 슬롯 2/5 사용 → AI API 호출 중...
유저 C 요청 → Semaphore.acquire() → 슬롯 3/5 사용 → AI API 호출 중...
유저 D 요청 → Semaphore.acquire() → 슬롯 4/5 사용 → AI API 호출 중...
유저 E 요청 → Semaphore.acquire() → 슬롯 5/5 사용 → AI API 호출 중...
유저 F 요청 → Semaphore.acquire() → 대기... (30초 타임아웃)
유저 A 완료 → Semaphore.release() → 유저 F가 슬롯 획득 → AI API 호출
```

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| 동시 AI 요청 | 무제한 (톰캣 스레드 200개까지) | 최대 5개 동시 실행 |
| 6번째 요청 | 톰캣 스레드 점유하며 무한 대기 | 30초 대기 후 사용자 안내 메시지 |
| 일반 API 영향 | AI 과부하 시 전체 API 응답 지연 | AI와 일반 API 격리 |
| 외부 API 부하 | 동시 200개 요청 가능 | 최대 5개로 제한 |

### 배운 점

- `Semaphore.acquire()`가 아닌 `tryAcquire(timeout)`을 사용해야 합니다. `acquire()`는 무한 대기이므로 톰캣 스레드를 영원히 붙잡습니다.
- `finally` 블록에서 반드시 `release()`를 호출해야 합니다. 예외 발생 시 release를 빠뜨리면 슬롯이 영구적으로 줄어들어 결국 0개가 됩니다.
- 서버 1대 환경에서는 JVM Semaphore가 분산 락보다 단순하고 오버헤드가 없습니다.

---

## 2. DB 검색 인덱스 최적화 (GIN 트라이그램)

### 문제

이끼잡의 공고 검색 API는 키워드로 제목(title)과 회사명(company)을 동시에 검색합니다.

```sql
-- JPQL이 생성하는 실제 SQL
WHERE LOWER(j.title) LIKE LOWER('%spring%')
   OR LOWER(j.company) LIKE LOWER('%카카오%')
```

`LIKE '%keyword%'`는 와일드카드가 앞에 붙어있어 **B-Tree 인덱스를 사용할 수 없습니다**. PostgreSQL은 이 쿼리를 실행할 때 테이블의 모든 행을 순차적으로 읽는 **Full Table Scan(Seq Scan)**을 수행합니다.

1000건일 때는 10ms 이내로 괜찮지만, 만 건 이상이 되면 100ms를 넘기기 시작합니다. 공고는 매일 크롤링으로 누적되므로 시간이 지날수록 느려지는 구조입니다.

### 고민: 어떤 인덱스 전략을 쓸 것인가

| 방식 | 장점 | 단점 | 판단 |
|------|------|------|------|
| **Elasticsearch** | 형태소 분석, 한국어 토크나이징, 자동완성 | 별도 서버 필요 (메모리 1GB+), 동기화 복잡 | 서버 1대 제약 |
| **PostgreSQL Full Text Search** | 내장 기능, 별도 서버 불필요 | 한국어 지원 빈약 (영어/유럽어 최적화) | 한국어 공고에 부적합 |
| **PostgreSQL pg_trgm + GIN** | 한국어 부분 매칭 지원, 별도 서버 불필요 | 인덱스 크기 증가, 소량 데이터에서는 Seq Scan이 더 빠름 | 채택 |

### 개념: 트라이그램(Trigram)이란

트라이그램은 문자열을 3글자씩 잘라서 만든 토큰입니다.

```
"spring" → {"  s", " sp", "spr", "pri", "rin", "ing", "ng ", "g  "}
"카카오"  → {" 카카", "카카오", "카오 "}
```

PostgreSQL의 `pg_trgm` 확장은 이 트라이그램들을 GIN(Generalized Inverted Index) 인덱스에 저장합니다. `LIKE '%spring%'` 쿼리가 오면 'spring'의 트라이그램과 겹치는 행만 빠르게 찾아냅니다.

### 해결

**1단계: pg_trgm 확장 + GIN 인덱스 생성**

```sql
-- PostgreSQL에서 pg_trgm 확장 활성화
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- title, company에 GIN 트라이그램 인덱스 생성
CREATE INDEX idx_job_title_trgm ON job_postings USING gin (title gin_trgm_ops);
CREATE INDEX idx_job_company_trgm ON job_postings USING gin (company gin_trgm_ops);
```

**2단계: JPA @Index 추가** (`JobPosting.java`):

```java
@Entity
@Table(name = "job_postings", indexes = {
        @Index(name = "idx_job_source", columnList = "source"),
        @Index(name = "idx_job_deadline", columnList = "deadline"),
        @Index(name = "idx_job_company", columnList = "company"),
        @Index(name = "idx_job_url", columnList = "url"),
        @Index(name = "idx_job_source_closed", columnList = "source, closed"),
        @Index(name = "idx_job_closed_deadline", columnList = "closed, deadline"),
        @Index(name = "idx_job_title", columnList = "title"),
        @Index(name = "idx_job_category", columnList = "jobCategory"),
        @Index(name = "idx_job_created", columnList = "createdAt")
})
```

**검색 JPQL** (`JobPostingRepository.java`):

```java
@Query("SELECT j FROM JobPosting j WHERE j.closed = false " +
        "AND (:source IS NULL OR j.source = :source) " +
        "AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')) " +
        "   OR LOWER(j.company) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))) " +
        // ... 기타 조건
        )
Page<JobPosting> searchJobs(...);
```

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| LIKE '%keyword%' | Full Table Scan | GIN 트라이그램 인덱스 활용 |
| 1,000건 검색 | ~5ms (Seq Scan) | ~5ms (Seq Scan이 더 빠름) |
| 10,000건 검색 | ~100ms+ (Seq Scan) | ~20ms (GIN Index Scan) |
| title/company 인덱스 | 없음 | B-Tree + GIN 트라이그램 |
| 정렬/필터 인덱스 | source만 있음 | source, deadline, jobCategory, createdAt 추가 |

### 배운 점

- 1000건 수준에서는 PostgreSQL이 Seq Scan을 선택합니다. GIN 인덱스를 읽는 오버헤드가 Seq Scan보다 크기 때문입니다. 이는 정상입니다.
- `EXPLAIN ANALYZE`로 실행 계획을 확인하면 데이터가 충분히 많아야 인덱스를 사용하는 것을 볼 수 있습니다.
- 복합 인덱스(`source, closed`)는 통계 API의 `countBySourceAndClosedFalse()` 같은 자주 사용되는 쿼리에 효과적입니다.

---

## 3. Cache Stampede 방지 (분산 락)

### 문제

이끼잡의 메인 페이지에는 사이트별 공고 수 통계가 표시됩니다. 이 통계 API에는 Redis 캐시(5분 TTL)가 적용되어 있습니다.

문제는 **캐시가 만료되는 순간**에 발생합니다. 5분마다 캐시가 사라지면, 그 시점에 동시에 들어온 요청들이 **전부 DB를 직접 조회**합니다.

```
[5분 경과 - 캐시 만료]
유저 A → Redis MISS → DB 쿼리 (countBySourceAndClosedFalse x 4)
유저 B → Redis MISS → DB 쿼리 (countBySourceAndClosedFalse x 4)
유저 C → Redis MISS → DB 쿼리 (countBySourceAndClosedFalse x 4)
유저 D → Redis MISS → DB 쿼리 (countBySourceAndClosedFalse x 4)
유저 E → Redis MISS → DB 쿼리 (countBySourceAndClosedFalse x 4)
→ DB에 20개 쿼리가 동시에 실행 (5명 x 4개 count 쿼리)
```

### 개념: Cache Stampede란 무엇인가

Cache Stampede(캐시 쇄도)는 캐시가 만료되는 순간, 대기 중이던 다수의 요청이 동시에 원본 데이터 소스(DB)로 몰려드는 현상입니다. "떼 지어 달려드는(stampede)" 것에서 이름이 유래했습니다.

**왜 위험한가?**

1. **DB 과부하**: 캐시가 있을 때 0개이던 DB 쿼리가 순간적으로 수십~수백 개로 폭증합니다.
2. **연쇄 지연**: DB 응답이 느려지면 커넥션 풀이 가득 차고, 다른 쿼리도 대기하게 됩니다.
3. **반복 발생**: TTL 기반 캐시는 주기적으로 만료되므로, 5분마다 같은 현상이 반복됩니다.

트래픽이 적을 때는 동시 요청이 1~2개라 문제가 안 됩니다. 하지만 동시 접속이 100명, 1000명이 되면 캐시 만료 순간마다 DB에 수백 개 쿼리가 몰립니다.

### 고민: 어떤 방식으로 방지할 것인가

| 방식 | 장점 | 단점 | 판단 |
|------|------|------|------|
| **분산 락 (Redis setIfAbsent)** | 첫 요청만 DB 조회, 나머지 대기 | 대기 시간 발생 | 채택 |
| **캐시 워밍 (스케줄러)** | Stampede 원천 차단 | 스케줄러 실패 시 캐시 없음 | 보조 수단 |
| **Probabilistic Early Expiration** | 만료 전 확률적 갱신 | 구현 복잡, 불필요한 갱신 가능 | 과도한 설계 |

### 해결: Redis setIfAbsent 분산 락

`JobPostingServiceImpl.getStats()` 메서드에 분산 락을 적용했습니다.

**핵심 코드** (`JobPostingServiceImpl.java`):

```java
private static final String STATS_CACHE_KEY = "cache:job:stats";
private static final String STATS_LOCK_KEY = "lock:job:stats";
private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(5);
private static final Duration LOCK_TTL = Duration.ofSeconds(10);

@Override
public Map<String, Long> getStats() {
    // 1단계: Redis 캐시 확인
    try {
        Object cached = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
        if (cached instanceof String json) {
            log.debug("[캐시 히트] 공고 통계");
            return objectMapper.readValue(json, new TypeReference<>() {});
        }
    } catch (Exception e) {
        log.debug("[캐시 미스] 역직렬화 실패, DB 조회: {}", e.getMessage());
    }

    // 2단계: 분산 락 획득 시도 (setIfAbsent = SETNX)
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(STATS_LOCK_KEY, "1", LOCK_TTL);

    if (Boolean.FALSE.equals(locked)) {
        // 다른 스레드가 이미 DB 조회 중 → 500ms 대기 후 캐시 재확인
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        try {
            Object retry = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
            if (retry instanceof String json2) {
                return objectMapper.readValue(json2, new TypeReference<>() {});
            }
        } catch (Exception ignored) {}
    }

    // 3단계: 락을 획득한 스레드만 DB 조회
    long saramin = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.SARAMIN);
    long jobplanet = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBPLANET);
    long linkareer = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.LINKAREER);
    long jobkorea = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBKOREA);

    Map<String, Long> stats = new HashMap<>();
    stats.put("saramin", saramin);
    stats.put("jobplanet", jobplanet);
    stats.put("linkareer", linkareer);
    stats.put("jobkorea", jobkorea);
    stats.put("total", saramin + jobplanet + linkareer + jobkorea);

    // 4단계: 캐시 저장 (이후 다른 스레드는 여기서 읽음)
    try {
        String json = objectMapper.writeValueAsString(stats);
        redisTemplate.opsForValue().set(STATS_CACHE_KEY, json, STATS_CACHE_TTL);
        log.debug("[캐시 저장] 공고 통계 (5분 TTL)");
    } catch (Exception e) {
        log.warn("[캐시 저장 실패] {}", e.getMessage());
    }
    return stats;
}
```

**동작 흐름**:

```
[캐시 만료 시점]

유저 A → 캐시 MISS → setIfAbsent("lock:job:stats") → 성공(locked) → DB 조회 시작
유저 B → 캐시 MISS → setIfAbsent("lock:job:stats") → 실패 → 500ms 대기
유저 C → 캐시 MISS → setIfAbsent("lock:job:stats") → 실패 → 500ms 대기
유저 D → 캐시 MISS → setIfAbsent("lock:job:stats") → 실패 → 500ms 대기
유저 E → 캐시 MISS → setIfAbsent("lock:job:stats") → 실패 → 500ms 대기

[200ms 후] 유저 A → DB 조회 완료 → 캐시 저장

[500ms 후] 유저 B,C,D,E → 캐시 재확인 → 캐시 히트! → DB 조회 없이 반환
```

### 테스트 결과

5개 동시 요청을 보내서 로그를 확인했습니다:

```
[캐시 미스] 역직렬화 실패, DB 조회
[캐시 저장] 공고 통계 (5분 TTL)    ← 1회만 발생!
[캐시 히트] 공고 통계               ← 나머지 4개는 캐시에서 읽음
[캐시 히트] 공고 통계
[캐시 히트] 공고 통계
[캐시 히트] 공고 통계
```

**"캐시 저장" 로그가 정확히 1회만 발생**했습니다. 5개 동시 요청에서 DB 조회가 1번만 실행된 것입니다.

### Before / After

| 항목 | Before | After |
|------|--------|-------|
| 캐시 만료 시 DB 쿼리 | 동시 요청 수 x 4 (count 쿼리) | **1 x 4 (첫 스레드만)** |
| 나머지 스레드 | 전부 DB 직접 조회 | 500ms 대기 후 캐시에서 읽기 |
| DB 부하 (동시 5명) | 20개 쿼리 | **4개 쿼리** |
| 락 안전장치 | 없음 | 10초 TTL (데드락 방지) |

### 배운 점

- `setIfAbsent`에 반드시 TTL을 설정해야 합니다. 락을 획득한 스레드가 예외로 죽으면 영원히 락이 풀리지 않습니다(데드락). 10초 TTL로 자동 해제됩니다.
- 500ms 대기 후 캐시를 재확인하는 로직이 핵심입니다. DB 조회가 500ms 이내에 끝나야 이 구조가 동작합니다. 실제 4개 count 쿼리는 인덱스(`idx_job_source_closed`)를 타서 50ms 이내에 끝납니다.
- 락을 못 얻은 스레드가 대기 후에도 캐시에 값이 없으면 DB를 직접 조회합니다. 이는 의도된 설계입니다(최악의 경우에도 응답은 해야 하므로).

---

## 4. AI 비동기 큐 + 폴링

### 문제

AI 자소서 생성과 포트폴리오 생성은 30초~1분이 걸립니다. OpenClaw API가 프롬프트를 처리하는 데 시간이 필요하기 때문입니다.

기존 동기 방식에서는 유저가 "자소서 생성" 버튼을 클릭하면:

```
유저 클릭 → [30초~1분 화면 멈춤] → 결과 반환
```

이 동안 유저는 로딩 스피너만 보면서 기다려야 합니다. 브라우저 탭을 닫거나 새로고침하면 진행 중이던 결과를 잃어버립니다. 모바일에서는 타임아웃이 발생하기도 합니다.

### 개념: 동기 vs 비동기, 메시지 큐 패턴

**동기(Synchronous) 방식**: 클라이언트가 요청을 보내고, 서버가 작업을 완료할 때까지 연결을 유지한 채 기다립니다. 작업이 오래 걸리면 클라이언트도 그만큼 대기합니다.

```
Client ──요청──> Server ──처리 중(30초)──> Client에 응답
         (30초간 HTTP 연결 유지)
```

**비동기(Asynchronous) 방식**: 클라이언트가 요청을 보내면 서버가 즉시 "접수 완료" 응답을 보내고, 실제 작업은 백그라운드에서 처리합니다. 클라이언트는 나중에 결과를 확인합니다.

```
Client ──요청──> Server ──접수 완료(taskId)──> Client (1초)
                  └── 백그라운드 처리(30초)
Client ──폴링──> Server ──PROCESSING
Client ──폴링──> Server ──COMPLETED + 결과
```

**메시지 큐 패턴**: 요청을 큐에 넣고, 워커가 순서대로 꺼내서 처리하는 구조입니다. 이끼잡에서는 Redis Hash를 태스크 저장소로 사용합니다.

### 해결: Redis 기반 태스크 큐 + 비동기 엔드포인트 + 폴링

**1. AiTaskQueue** - Redis 기반 태스크 관리 (`AiTaskQueue.java`):

```java
@Component
@RequiredArgsConstructor
public class AiTaskQueue {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String TASK_PREFIX = "ai:task:";
    private static final Duration TASK_TTL = Duration.ofMinutes(10);

    public String enqueue(String taskType, Long userId) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        String key = TASK_PREFIX + taskId;

        Map<String, String> task = new ConcurrentHashMap<>();
        task.put("status", PENDING);
        task.put("type", taskType);
        task.put("userId", String.valueOf(userId));
        task.put("result", "");

        redisTemplate.opsForHash().putAll(key, task);
        redisTemplate.expire(key, TASK_TTL);

        log.info("[AI큐] 태스크 등록: {} ({}, userId={})", taskId, taskType, userId);
        return taskId;
    }

    public void complete(String taskId, Long userId, String result) {
        updateRedis(taskId, COMPLETED, result);
        notifyUser(userId, taskId, COMPLETED, result);  // WebSocket 알림
        log.info("[AI큐] 태스크 완료: {} ({}자)", taskId,
                result != null ? result.length() : 0);
    }
}
```

**2. 비동기 엔드포인트** (`AiController.java`):

```java
/** 비동기 자소서 생성 (즉시 taskId 반환) */
@PostMapping("/async/cover-letter/{jobId}")
public ResponseEntity<ApiResponse<Map<String, String>>> asyncCoverLetter(
        Authentication auth, @PathVariable Long jobId,
        @RequestParam(required = false) Long templateId) {
    Long userId = (Long) auth.getPrincipal();
    String taskId = aiTaskQueue.enqueue("COVER_LETTER", userId);

    // 백그라운드 스레드에서 AI 처리
    new Thread(() -> {
        try {
            String text = aiAutomationService.generateCoverLetter(userId, jobId, templateId);
            aiTaskQueue.complete(taskId, userId, text);
        } catch (Exception e) {
            aiTaskQueue.fail(taskId, userId, e.getMessage());
        }
    }).start();

    // 즉시 taskId 반환 (1초 이내)
    return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
}

/** 폴링 엔드포인트 (WebSocket 미연결 시 fallback) */
@GetMapping("/async/status/{taskId}")
public ResponseEntity<ApiResponse<Map<Object, Object>>> getTaskStatus(
        @PathVariable String taskId) {
    Map<Object, Object> task = aiTaskQueue.getTask(taskId);
    if (task.isEmpty()) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "NOT_FOUND")));
    }
    return ResponseEntity.ok(ApiResponse.ok(task));
}
```

**전체 흐름**:

```
1. POST /api/v1/ai/async/cover-letter/123
   → taskId: "abcb94e0" 즉시 반환 (1초)

2. 백그라운드에서 AI 처리 시작
   → Redis: ai:task:abcb94e0 { status: "PROCESSING" }

3. GET /api/v1/ai/async/status/abcb94e0
   → { status: "PROCESSING", type: "COVER_LETTER" }

4. 30초 후 AI 처리 완료
   → Redis: ai:task:abcb94e0 { status: "COMPLETED", result: "..." }
   → WebSocket: /topic/ai/{userId} 로 알림 발송

5. GET /api/v1/ai/async/status/abcb94e0
   → { status: "COMPLETED", result: "자소서 텍스트 2660자..." }
```

### 테스트 결과

```
[요청] POST /api/v1/ai/async/cover-letter/15 → taskId: abcb94e0 (응답: 0.8초)
[3초 후 폴링] GET /async/status/abcb94e0 → { status: "PROCESSING" }
[30초 후 폴링] GET /async/status/abcb94e0 → { status: "COMPLETED", result: "...(2660자)" }
```

### Before / After

| 항목 | Before (동기) | After (비동기) |
|------|---------------|----------------|
| 유저 대기 시간 | 30초~1분 (화면 멈춤) | **1초** (즉시 taskId 반환) |
| 탭 닫기/새로고침 | 결과 유실 | taskId로 나중에 조회 가능 |
| 모바일 타임아웃 | 30초 초과 시 에러 | 폴링으로 안정적 수신 |
| 동시 요청 | 톰캣 스레드 점유 30초씩 | 즉시 반환, 백그라운드 처리 |

### 배운 점

- Redis Hash는 간단한 태스크 큐로 활용하기에 적합합니다. 별도의 메시지 브로커(RabbitMQ, Kafka) 없이 태스크 상태 관리가 가능합니다.
- 태스크에 TTL(10분)을 설정해서 완료 후 자동 삭제되도록 했습니다. Redis 메모리를 무한히 차지하는 것을 방지합니다.
- 폴링 간격은 클라이언트에서 3초로 설정했습니다. 너무 짧으면 서버 부하, 너무 길면 실시간성이 떨어집니다.

---

## 5. WebSocket 실시간 알림 (STOMP)

### 문제

4번에서 구현한 폴링 방식에는 근본적인 한계가 있습니다.

1. **불필요한 네트워크 요청**: AI 처리가 30초 걸리면, 그 동안 클라이언트는 3초마다 10번의 불필요한 GET 요청을 보냅니다.
2. **실시간성 부족**: 폴링 간격이 3초이면, AI가 완료되고 최대 3초 후에야 유저가 결과를 받습니다.
3. **서버 부하**: 동시 100명이 폴링하면 초당 33개의 상태 확인 요청이 발생합니다.

### 개념: WebSocket vs HTTP 폴링 vs SSE

| 방식 | 연결 | 방향 | 오버헤드 | 적합한 상황 |
|------|------|------|----------|------------|
| **HTTP 폴링** | 매 요청마다 새 연결 | 클라이언트 → 서버 | 높음 (헤더 반복) | 간단한 상태 확인 |
| **SSE (Server-Sent Events)** | 단방향 지속 연결 | 서버 → 클라이언트 | 중간 | 서버 → 클라이언트 푸시 |
| **WebSocket** | 양방향 지속 연결 | 양방향 | 낮음 | 실시간 양방향 통신 |

**STOMP(Simple Text Oriented Messaging Protocol)**: WebSocket 위에서 동작하는 메시징 프로토콜입니다. pub/sub 패턴을 지원하여, 채널을 구독(subscribe)하면 해당 채널로 발행(publish)된 메시지를 실시간으로 받을 수 있습니다.

**SockJS**: WebSocket을 지원하지 않는 브라우저나 프록시 환경에서 자동으로 폴링 방식으로 전환해주는 라이브러리입니다. 폴백(fallback) 기능을 제공합니다.

### 해결: Spring WebSocket + STOMP + SockJS

**1. WebSocket 설정** (`WebSocketConfig.java`):

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 구독할 prefix (/topic/ai/...)
        config.enableSimpleBroker("/topic");
        // 클라이언트가 메시지를 보낼 prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();  // SockJS fallback 지원
    }
}
```

**2. 서버 → 클라이언트 알림 발송** (`AiTaskQueue.java`):

```java
private void notifyUser(Long userId, String taskId, String status, String result) {
    try {
        Map<String, Object> message = Map.of(
                "taskId", taskId,
                "status", status,
                "result", result != null ? result : ""
        );
        // 유저별 채널로 메시지 발송
        messagingTemplate.convertAndSend("/topic/ai/" + userId, message);
        log.debug("[WebSocket] 알림 발송: userId={}, taskId={}, status={}",
                userId, taskId, status);
    } catch (Exception e) {
        log.warn("[WebSocket] 알림 발송 실패: {}", e.getMessage());
    }
}
```

**3. 클라이언트 연결** (프론트엔드, `@stomp/stompjs` + `sockjs-client`):

```typescript
// auth-context에서 로그인 성공 시 WebSocket 연결
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const stompClient = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  onConnect: () => {
    // 유저별 채널 구독
    stompClient.subscribe(`/topic/ai/${userId}`, (message) => {
      const data = JSON.parse(message.body);
      // { taskId: "abcb94e0", status: "COMPLETED", result: "..." }
      if (data.status === 'COMPLETED') {
        showNotification('AI 생성이 완료되었습니다!');
        updateResult(data.result);
      }
    });
  }
});

stompClient.activate();
```

**전체 아키텍처**:

```
[클라이언트]                         [서버]
    │                                  │
    ├─ POST /async/cover-letter ────> │ taskId 즉시 반환
    │                                  │ └─ 백그라운드 AI 처리 시작
    │                                  │
    ├─ STOMP SUBSCRIBE ────────────> │ /topic/ai/{userId} 구독
    │   /topic/ai/{userId}            │
    │                                  │
    │      ... AI 처리 중 (30초) ...   │
    │                                  │
    │ <── STOMP MESSAGE ────────────── │ { status: "COMPLETED", result: "..." }
    │   (실시간 푸시, 0ms 지연)         │
```

### 폴링 fallback 유지

WebSocket이 연결되지 않은 상황(프록시 차단, 네트워크 불안정 등)에서도 결과를 받을 수 있도록 폴링 엔드포인트를 유지합니다.

```typescript
// WebSocket 연결 상태 확인
if (stompClient.connected) {
  // WebSocket으로 실시간 수신 → 폴링 불필요
} else {
  // fallback: 3초 간격 폴링
  const interval = setInterval(async () => {
    const res = await fetch(`/api/v1/ai/async/status/${taskId}`);
    const data = await res.json();
    if (data.status === 'COMPLETED' || data.status === 'FAILED') {
      clearInterval(interval);
      handleResult(data);
    }
  }, 3000);
}
```

### 테스트 결과

```
[AI큐] 태스크 등록: abcb94e0 (COVER_LETTER, userId=2)
[AI큐] 태스크 완료: abcb94e0 (2660자)
[WebSocket] 알림 발송: userId=2, taskId=abcb94e0, status=COMPLETED
```

WebSocket 알림이 정상 발송되며, 클라이언트가 구독 중이면 **완료 즉시(0ms 지연)** 결과를 수신합니다.

### Before / After

| 항목 | Before (폴링만) | After (WebSocket + 폴링 fallback) |
|------|------------------|-----------------------------------|
| 결과 수신 지연 | 최대 3초 (폴링 간격) | **0ms** (즉시 푸시) |
| 불필요한 요청 | 30초간 10회 GET | **0회** (서버가 푸시) |
| 서버 부하 (100명) | 초당 33개 상태 확인 | WebSocket 연결 유지만 |
| 브라우저 호환성 | 100% | 100% (SockJS fallback) |
| 네트워크 단절 | 폴링 실패 → 결과 못 받음 | 폴링 fallback 유지 |

### 배운 점

- STOMP의 `/topic/ai/{userId}` 패턴으로 유저별 격리된 채널을 만들 수 있습니다. 다른 유저의 AI 결과가 섞이지 않습니다.
- `SimpMessagingTemplate.convertAndSend()`는 서버 내부 어디서든 호출 가능합니다. Controller뿐 아니라 Service, Component에서도 WebSocket 메시지를 보낼 수 있습니다.
- WebSocket만 의존하면 연결 실패 시 결과를 받을 수 없습니다. 반드시 폴링 fallback을 유지해야 합니다.
- SockJS의 `withSockJS()` 설정으로 WebSocket을 지원하지 않는 환경(구형 프록시, 방화벽)에서도 자동으로 XHR 스트리밍 또는 Long Polling으로 전환됩니다.

---

## 전체 아키텍처 변화 요약

| 영역 | Before | After | 핵심 기술 |
|------|--------|-------|-----------|
| **AI 동시 요청** | 무제한 (톰캣 스레드 200개 점유) | 최대 5개 동시, 30초 대기 초과 시 안내 | `java.util.concurrent.Semaphore(5)` |
| **DB 검색** | Full Table Scan (LIKE '%keyword%') | GIN 트라이그램 인덱스 활용 | `pg_trgm` + GIN Index |
| **캐시 만료 처리** | 동시 요청 전부 DB 직접 조회 | 첫 스레드만 DB 조회, 나머지 캐시 대기 | Redis `setIfAbsent` 분산 락 |
| **AI 응답 대기** | 30초~1분 화면 멈춤 (동기) | 1초 내 접수 → 백그라운드 처리 | Redis 태스크 큐 + 비동기 엔드포인트 |
| **결과 전달** | HTTP 폴링 (3초 간격, 불필요한 요청) | WebSocket 실시간 푸시 (0ms 지연) | STOMP over SockJS |
| **JPA 인덱스** | source 인덱스만 | 9개 인덱스 (B-Tree + 복합) | `@Index` 어노테이션 |

### 서버 1대 제약에서의 설계 원칙

이 프로젝트를 진행하며 서버 1대 환경에서 지켜야 할 설계 원칙을 정리했습니다:

1. **JVM 내부 동시성 제어를 우선 사용한다**: 분산 락보다 Semaphore가 단순하고 빠릅니다. 서버가 늘어나면 그때 분산 방식으로 전환합니다.
2. **Redis는 만능이 아니다**: 캐시, 큐, 락 모두 Redis에 의존하면 Redis 장애 시 모든 기능이 멈춥니다. DB를 원본으로 두고 Redis를 캐시로 사용합니다(Cache-Aside).
3. **비동기 처리는 fallback이 필수다**: WebSocket이 끊기면 폴링, Redis가 죽으면 DB 직접 조회. 항상 대안 경로를 두어야 합니다.
4. **인덱스는 데이터 규모에 맞게**: 1000건에서 GIN 인덱스는 오히려 느립니다. EXPLAIN ANALYZE로 확인하고, 데이터가 쌓이면 자연스럽게 효과가 나타나도록 설계합니다.
5. **유저 경험 우선**: 서버 내부의 최적화보다 유저가 체감하는 응답 시간이 중요합니다. 30초 대기를 1초로 줄이는 비동기 전환이 가장 큰 개선이었습니다.
