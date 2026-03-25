# Java 21 Virtual Thread 심층 분석

## Virtual Thread란?

기존 Java 스레드(플랫폼 스레드)는 OS 스레드와 1:1로 매핑된다. OS 스레드는 비싸다 — 스택 메모리 1MB, 생성/소멸 비용, 컨텍스트 스위칭 비용. 그래서 스레드 풀(Tomcat 200개)로 제한해서 쓴다.

Virtual Thread는 JVM이 직접 관리하는 **경량 스레드**다. OS 스레드 위에서 JVM이 스케줄링한다.

```
[기존]
요청 1 → 플랫폼 스레드 1 → OS 스레드 1
요청 2 → 플랫폼 스레드 2 → OS 스레드 2
...
요청 200 → 플랫폼 스레드 200 → OS 스레드 200
요청 201 → 대기열에서 기다림 (스레드 없음)

[Virtual Thread]
요청 1 → Virtual 스레드 1 ─┐
요청 2 → Virtual 스레드 2 ─┤
...                         ├→ 캐리어 스레드 (OS 스레드 4~8개)
요청 500 → Virtual 스레드 500─┘
    ↑ JVM이 I/O 대기 시 자동으로 캐리어에서 분리/재탑승
```

**캐리어 스레드**: Virtual Thread가 실제로 실행되는 OS 스레드. CPU 코어 수만큼만 있으면 된다. 4코어면 캐리어 4개로 Virtual Thread 수만 개를 돌릴 수 있다.

---

## 문제점 1: synchronized 블록에서 Pinning (고정)

### 무슨 뜻인가

Virtual Thread가 `synchronized` 블록 안에 있을 때 I/O 대기가 발생하면, 원래는 캐리어 스레드에서 분리되어야 하지만 **분리가 안 된다**. 캐리어 스레드에 "핀(pin)"으로 고정된 채 놀고 있게 된다.

### 왜 이런 일이 생기는가

Java의 `synchronized`는 **모니터 락(Monitor Lock)**이라는 OS 수준 동기화를 사용한다.

```
[Virtual Thread A] → synchronized(lock) 진입 → 모니터 락 획득
                     → DB 쿼리 실행 (I/O 대기)
                     → 이때 캐리어에서 분리하고 싶지만...
                     → 모니터 락은 OS 스레드에 바인딩되어 있음
                     → 캐리어에서 분리하면 락도 풀려버림
                     → 그래서 분리 못하고 캐리어에 고정(pinned)
```

모니터 락은 "이 OS 스레드가 이 락을 소유한다"는 구조다. Virtual Thread가 캐리어에서 내려가면 OS 스레드가 바뀌니까 락 소유권이 깨진다. 그래서 JVM이 안전을 위해 분리를 막는다.

### 이 프로젝트에서 어디가 위험한가

```java
// HikariCP 내부 — 커넥션 획득 시 synchronized 사용
synchronized (this) {
    connection = pool.getConnection();  // I/O 대기 발생 가능
}

// Playwright 내부 — 브라우저 통신 시 synchronized 가능
synchronized (browserLock) {
    page.navigate(url);  // 네트워크 I/O 대기
}
```

HikariCP와 Playwright가 내부적으로 `synchronized`를 쓸 수 있다. 이 안에서 I/O 대기가 발생하면 캐리어 스레드가 고정되어 **Virtual Thread의 이점이 사라진다**.

### 해결 방법

```java
// synchronized 대신 ReentrantLock 사용
private final ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    connection = pool.getConnection();
} finally {
    lock.unlock();
}
```

`ReentrantLock`은 모니터 락이 아니라 Java 수준 락이라 Virtual Thread가 캐리어에서 자유롭게 분리/재탑승할 수 있다. 하지만 **라이브러리 내부 코드**는 우리가 바꿀 수 없다.

HikariCP 5.1.0은 Virtual Thread 호환 작업이 진행 중이지만 완전하지 않다. Lettuce(Redis)는 Netty 기반이라 비동기 I/O를 써서 상대적으로 안전하다.

---

## 문제점 2: ThreadLocal 메모리 폭발

### 무슨 뜻인가

`ThreadLocal`은 스레드마다 독립적인 변수를 가지는 저장소다. 플랫폼 스레드가 200개면 ThreadLocal 변수도 200개. 그런데 Virtual Thread가 10만 개 생기면 ThreadLocal 변수도 10만 개가 된다.

### 기존 구조에서는 왜 문제가 없었나

```
[플랫폼 스레드 풀 — 200개 고정]
Thread-1: ThreadLocal → SecurityContext (1KB)
Thread-2: ThreadLocal → SecurityContext (1KB)
...
Thread-200: ThreadLocal → SecurityContext (1KB)
합계: 200KB — 문제 없음
```

스레드 풀이 고정이라 ThreadLocal 개수도 고정이다.

### Virtual Thread에서 왜 문제인가

```
[Virtual Thread — 요청마다 생성]
VT-1: ThreadLocal → SecurityContext (1KB)
VT-2: ThreadLocal → SecurityContext (1KB)
...
VT-100000: ThreadLocal → SecurityContext (1KB)
합계: 100MB — 메모리 폭발
```

Virtual Thread는 요청마다 새로 생성되고, 각각 ThreadLocal을 가진다. 동시 요청이 많으면 ThreadLocal이 쌓인다.

### 이 프로젝트에서 어디가 위험한가

```java
// Spring Security — SecurityContextHolder
SecurityContextHolder.setContext(context);
// 내부: ThreadLocal<SecurityContext>에 저장

// Spring Transaction — TransactionSynchronizationManager
TransactionSynchronizationManager.bindResource(key, value);
// 내부: ThreadLocal<Map<Object, Object>>에 저장

// JPA — EntityManager
// 내부: ThreadLocal로 영속성 컨텍스트 관리
```

Spring의 핵심 인프라가 전부 ThreadLocal 기반이다. Virtual Thread를 쓰면 이 모든 ThreadLocal이 스레드 수만큼 복제된다.

### 해결 방법

Java 21에서는 `ScopedValue`라는 대안이 제공된다 (Preview):

```java
// ThreadLocal (기존) — 스레드에 바인딩, 수동 정리 필요
private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

// ScopedValue (새로운 방식) — 스코프가 끝나면 자동 정리
private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

ScopedValue.runWhere(CURRENT_USER, user, () -> {
    // 이 블록 안에서만 유효
    processRequest();
});
// 블록 밖에서는 자동으로 사라짐 — 메모리 누수 없음
```

하지만 Spring Framework 자체가 아직 ScopedValue로 전환되지 않았다. Spring 6.2+(Boot 3.4+)에서 점진적으로 지원 중이다.

---

## 문제점 3: 디버깅 어려움

### 무슨 뜻인가

기존 스레드 덤프:
```
"http-nio-8080-exec-1" #15 — WAITING
   at com.example.Service.getData(Service.java:42)
   at com.example.Controller.list(Controller.java:18)

"http-nio-8080-exec-2" #16 — RUNNABLE
   at com.example.Service.process(Service.java:55)
```
200개라 읽을 수 있다.

Virtual Thread 덤프:
```
"" #10001 virtual — WAITING
"" #10002 virtual — WAITING
"" #10003 virtual — RUNNABLE
... (10만 줄)
```
이름도 없고 수만 개라 **어디서 문제가 생겼는지 찾기 어렵다**.

### 왜 이렇게 되는가

Virtual Thread는 이름이 기본적으로 비어있고 (`""`), ID만 있다. `jstack`(스레드 덤프 도구)이 수만 개 스레드를 출력하면 분석이 불가능에 가깝다.

### 해결 방법

```java
// Virtual Thread에 이름 부여
Thread.ofVirtual()
    .name("request-", 0)  // request-0, request-1, ...
    .start(task);

// 또는 구조화된 동시성(Structured Concurrency) 사용
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var task1 = scope.fork(() -> callDB());
    var task2 = scope.fork(() -> callRedis());
    scope.join();
    // 부모-자식 관계가 명확 → 디버깅 용이
}
```

Spring Boot 3.4에서 `spring.threads.virtual.enabled=true` 설정 시 Tomcat이 자동으로 Virtual Thread에 이름을 부여하므로 이 문제는 줄어든다.

---

## 문제점 4: 라이브러리 호환성

### 무슨 뜻인가

Virtual Thread가 제대로 작동하려면 I/O 대기 시 캐리어에서 분리되어야 한다. 그런데 라이브러리가 내부적으로 `synchronized`, 네이티브 코드, 파일 락 등을 쓰면 분리가 안 된다.

### 이 프로젝트의 주요 라이브러리 호환성

```
[라이브러리]          [Virtual Thread 호환]    [이유]

HikariCP 5.1.0       △ 부분 호환              내부 synchronized 있음
                                               커넥션 획득 시 pinning 가능

Lettuce 6.4          ○ 호환                   Netty 비동기 I/O 기반
(Redis)                                        synchronized 거의 없음

Playwright 1.49      △ 미검증                 네이티브 프로세스 통신
                                               내부 동기화 구조 불명확

Spring Security      ○ 호환 (6.2+)            ThreadLocal → 부분 ScopedValue
                                               Virtual Thread 공식 지원

Spring Data JPA      ○ 호환 (3.4+)            EntityManager 관리 개선
                                               Virtual Thread 테스트 통과

Jackson (JSON)       ○ 호환                   CPU 바운드라 영향 없음

JSoup                ○ 호환                   단순 파싱, I/O 없음
```

### 호환되지 않으면 어떻게 되는가

최악의 경우: 캐리어 스레드 4개가 전부 pinned → 새 Virtual Thread가 실행 불가 → **서버 전체 멈춤(hang)**

```
캐리어-1: [VT-100 synchronized 안에서 DB 대기] — pinned
캐리어-2: [VT-200 synchronized 안에서 DB 대기] — pinned
캐리어-3: [VT-300 synchronized 안에서 DB 대기] — pinned
캐리어-4: [VT-400 synchronized 안에서 DB 대기] — pinned
→ 모든 캐리어가 고정됨
→ VT-401 ~ VT-10000은 실행할 캐리어가 없음
→ 서버 응답 불가
```

이게 **Virtual Thread의 가장 위험한 시나리오**다.

### 해결 방법

캐리어 스레드 수를 늘려서 여유를 둔다:
```bash
-Djdk.virtualThreadScheduler.parallelism=16
```
기본값은 CPU 코어 수와 같다. 늘리면 pinning 영향을 완화할 수 있다.

---

## 문제점 5: CPU 바운드 작업에는 효과 없음

### 무슨 뜻인가

Virtual Thread의 이점은 **I/O 대기 시간을 활용**하는 것이다.

```
[I/O 바운드 — Virtual Thread 효과 큼]
DB 쿼리: CPU 0.1ms 작업 + 네트워크 2ms 대기
→ 대기 2ms 동안 다른 Virtual Thread가 CPU 사용

[CPU 바운드 — Virtual Thread 효과 없음]
JSON 직렬화: CPU 1ms 작업 + 대기 0ms
→ CPU가 1ms 내내 점유됨. 분리할 타이밍이 없음
```

### 작업별 분류

```
[이 프로젝트의 작업 유형]

I/O 바운드 (Virtual Thread 효과 있음):
├─ DB 쿼리 (PostgreSQL) — 네트워크 대기
├─ Redis 조회 — 네트워크 대기
├─ OpenClaw AI API 호출 — HTTP 대기 (수십 초)
├─ Playwright 페이지 로딩 — 네트워크 대기
├─ Notion/GitHub API 호출 — HTTP 대기
└─ 파일 업로드/다운로드 — 디스크 I/O 대기

CPU 바운드 (Virtual Thread 효과 없음):
├─ JSON 직렬화 (Jackson) — CPU 연산
├─ HTML 파싱 (JSoup) — CPU 연산
├─ AI 프롬프트 문자열 조립 — CPU 연산
├─ 정규식 매칭 — CPU 연산
└─ HtmlSanitizer 소독 — CPU 연산
```

### 왜 효과가 없는가

Virtual Thread의 스케줄링은 **yield point**(양보 지점)에서 발생한다. Yield point는 I/O 대기, `Thread.sleep()`, `LockSupport.park()` 등에서 만들어진다.

CPU를 계속 쓰고 있으면 yield point가 없다. 캐리어 스레드를 계속 점유한다. 이 상태에서 Virtual Thread를 아무리 많이 만들어도 **실행할 캐리어가 없으면 대기열에 쌓일 뿐**이다.

```
4코어, Virtual Thread 1000개, 전부 CPU 바운드라면:
→ 동시에 4개만 실행 가능
→ 나머지 996개는 대기
→ 플랫폼 스레드 4개 쓰는 것과 동일
→ Virtual Thread 오버헤드만 추가됨
```

### 이 프로젝트에서의 판단

이 서비스의 핵심 API(공고 목록, 상세, 통계)는 **DB 조회 → JSON 직렬화**다.
- DB 조회: I/O 바운드 → Virtual Thread 효과 있음
- JSON 직렬화: CPU 바운드 → 효과 없음

DB 쿼리가 0.1~1ms이고 JSON 직렬화가 1~3ms이면, 전체 응답 시간에서 I/O 비중이 크지 않다. **이미 DB가 충분히 빠르면 Virtual Thread 효과가 제한적**이다.

반면 AI API 호출(30~60초)이나 크롤링(수초)은 I/O 대기가 길어서 Virtual Thread 효과가 극대화된다.

---

## 결론: 이 프로젝트에 적용해야 하는가

### 적용하면 좋은 점
- AI 비동기 작업, 크롤링처럼 I/O 대기가 긴 작업의 동시 처리량 증가
- Tomcat 스레드 풀 200개 한계 해소
- 메모리 효율 (스레드당 1MB → 수 KB)

### 적용하면 위험한 점
- HikariCP pinning 가능성 → 서버 hang 위험
- Playwright 호환성 미검증
- ThreadLocal 메모리 누수 가능성
- 디버깅 복잡도 증가

### 현실적 판단
```
현재: 200명 동시접속, p95 5.79ms, 에러율 0% — 문제 없음
목표: 500명 동시접속

권장 순서:
1순위: 수평 확장 (서버 2~3대) — 가장 안전하고 검증됨
2순위: Redis 캐시 강화 + CDN — 서버 부하 자체를 줄임
3순위: Virtual Thread — 위 방법으로 부족할 때, 충분한 테스트 후 적용
```

Virtual Thread는 **은탄환이 아니다**. 적용 전에 반드시:
1. pinning 감지 로그 활성화: `-Djdk.tracePinnedThreads=short`
2. 부하 테스트로 hang 여부 확인
3. HikariCP + Playwright 동시 사용 시나리오 검증
