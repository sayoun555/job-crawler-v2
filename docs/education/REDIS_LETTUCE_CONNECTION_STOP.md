# Redis LettuceConnectionFactory STOPPED 현상 분석

## 현상

크롤링 실행 시 모든 사이트에서 동시에 실패하며 아래 에러 발생:
```
LettuceConnectionFactory has been STOPPED. Use start() to initialize it
```

4개 사이트(사람인, 잡코리아, 잡플래닛, 링커리어)가 동시에 0건으로 실패했고, 크롤링 자체가 시작되지 못했다.

---

## 원인: Spring Boot DevTools의 Application Context 재시작 메커니즘

### 1단계: DevTools가 클래스 변경을 감지

Spring Boot DevTools는 개발 편의를 위해 **클래스 파일 변경을 감시**한다. `.class` 파일이 바뀌면 애플리케이션을 자동으로 재시작한다.

```
[파일 변경 감지] → [기존 ApplicationContext 종료] → [새 ApplicationContext 생성]
```

### 2단계: 기존 ApplicationContext 종료 과정

종료 시 Spring은 모든 Bean의 `destroy()` 메서드를 호출한다. `LettuceConnectionFactory`도 Bean이므로 `destroy()`가 호출되면서 내부 상태가 `STOPPED`로 바뀐다.

```java
// LettuceConnectionFactory 내부
public void destroy() {
    this.state = State.STOPPED;  // 연결 풀 종료
    this.connectionProvider.destroy();  // TCP 커넥션 해제
}
```

### 3단계: 새 ApplicationContext에서 Redis 연결 재생성

정상적인 재시작이면 새 `LettuceConnectionFactory` Bean이 생성되고 `afterPropertiesSet()`에서 `start()`가 호출된다.

```java
// 정상 흐름
[새 Bean 생성] → [afterPropertiesSet()] → [start()] → [State.STARTED]
```

### 4단계: 문제 발생 — Bean 참조 불일치

DevTools 재시작은 **두 개의 ClassLoader**를 사용한다:
- **Base ClassLoader**: 변하지 않는 라이브러리 (Spring, Lettuce, Playwright 등)
- **Restart ClassLoader**: 개발자가 수정하는 애플리케이션 코드

재시작 시:
1. Restart ClassLoader만 교체된다
2. Base ClassLoader의 객체(LettuceConnectionFactory)는 그대로 유지된다
3. **기존 Factory의 `destroy()`가 호출되어 STOPPED 상태가 된다**
4. 새 ApplicationContext가 기존 Factory를 재사용하려 하면 STOPPED 상태를 만난다

```
[기존 Factory] → destroy() → STOPPED
       ↓
[새 Context] → 기존 Factory 참조 → "STOPPED입니다" → 에러
```

---

## 왜 Redis만 문제인가?

### PostgreSQL (HikariCP)은 괜찮은 이유

HikariCP는 DevTools 재시작 시 커넥션 풀을 완전히 새로 만든다. 로그에서도 확인 가능:
```
HikariPool-1 - Shutdown completed.
HikariPool-2 - Start completed.    ← 새 풀 생성
```

HikariCP는 `DataSource` Bean이 새로 생성되므로 이전 풀의 STOPPED 상태를 만나지 않는다.

### Redis (Lettuce)는 문제인 이유

`LettuceConnectionFactory`는 Spring Data Redis의 기본 커넥션 팩토리다. DevTools 재시작 시:

1. `LettuceConnectionFactory`는 Base ClassLoader에 속한다 (Spring Data Redis 라이브러리)
2. Bean 재생성 과정에서 **기존 인스턴스의 상태가 완전히 초기화되지 않는 경우**가 있다
3. 특히 `ReactiveRedisConnectionFactory`와 `RedisConnectionFactory`가 동시에 존재하면 생명주기 관리가 꼬일 수 있다

---

## 재현 조건

이 문제는 아래 조건이 동시에 충족될 때 발생한다:

1. **Spring Boot DevTools가 활성화**되어 있다 (`spring-boot-devtools` 의존성)
2. **코드 변경으로 인한 자동 재시작**이 발생한다 (Hot Reload)
3. **재시작 직후** Redis를 사용하는 기능을 호출한다
4. Redis 커넥션 팩토리의 `start()`가 아직 완료되지 않았거나 호출되지 않았다

---

## 해결 방법

### 즉시 해결: 서버 수동 재시작

DevTools 자동 재시작이 아닌 **완전한 프로세스 종료 + 재시작**을 하면 ApplicationContext가 처음부터 새로 만들어지므로 문제가 없다.

```bash
pkill -f "job-crawler.*Application"
./gradlew bootRun
```

### 근본 해결: DevTools 재시작 시 Redis 커넥션 재초기화

```java
@Component
public class RedisConnectionResetListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!connectionFactory.isRunning()) {
            connectionFactory.start();
        }
    }
}
```

### 운영 환경에서는 발생하지 않는 이유

운영 환경(`prod` 프로필)에서는:
1. DevTools가 자동으로 비활성화된다 (`java -jar`로 실행 시)
2. 자동 재시작이 없다
3. 프로세스 재시작은 Docker/systemd가 관리하므로 항상 클린 스타트

---

## 관련 개념

### Bean 생명주기 (Spring IoC Container)

```
[Bean 정의] → [인스턴스화] → [의존성 주입] → [초기화 콜백]
    → [사용 중] → [소멸 콜백] → [GC]
```

- 초기화 콜백: `@PostConstruct`, `afterPropertiesSet()`, `init-method`
- 소멸 콜백: `@PreDestroy`, `destroy()`, `destroy-method`

DevTools 재시작은 이 생명주기를 **빠르게 반복**하는데, Base ClassLoader의 Bean은 소멸만 되고 재초기화가 누락될 수 있다.

### ClassLoader 격리 (DevTools)

```
JVM
├── Base ClassLoader (불변)
│   ├── spring-boot-*.jar
│   ├── spring-data-redis-*.jar  ← LettuceConnectionFactory 여기
│   ├── lettuce-core-*.jar
│   └── ...
└── Restart ClassLoader (교체됨)
    ├── com.portfolio.jobcrawler.* ← 개발자 코드
    └── ...
```

교체되는 건 Restart ClassLoader뿐이고, Base ClassLoader의 객체 상태는 JVM 메모리에 그대로 남는다.

### Connection Pool vs Connection Factory

| 구분 | HikariCP (DB) | Lettuce (Redis) |
|---|---|---|
| 타입 | Connection Pool | Connection Factory |
| 재시작 시 | 풀 전체 새로 생성 | Factory 상태만 리셋 |
| 상태 관리 | 풀 번호로 구분 (Pool-1, Pool-2) | 단일 인스턴스 재사용 |
| DevTools 호환 | 문제 없음 | 상태 불일치 가능 |

---

## 교훈

1. **DevTools는 개발 편의 도구이지 운영 도구가 아니다** — 재시작 메커니즘이 완벽하지 않다
2. **외부 시스템 연결(Redis, MQ 등)은 DevTools 재시작에 취약하다** — DB는 괜찮지만 Redis, Kafka 같은 커넥션 팩토리 기반은 주의
3. **크롤링처럼 오래 걸리는 작업 중에 코드를 수정하면 DevTools가 재시작을 시도**할 수 있다 — 이때 Redis 연결이 끊긴다
4. **운영 환경에서는 발생하지 않는다** — DevTools 자체가 비활성화되므로
