# 500명 동시접속 대응 전략 상세 분석

## 현재 상태

- 200명 동시접속, p95 5.79ms, 성공률 100%
- 서버 1대, HikariCP 50, Redis 캐시(통계 5분), Rate Limiting 200/min
- 자원: Java ~273MB, PostgreSQL ~2MB, Redis ~1.3MB

---

## 전략 1: Redis 캐시 공격적 적용

### 무엇인가

현재는 통계 API만 Redis에 5분 캐시한다. 이를 **공고 목록, 상세 조회까지 확대**하는 것이다.

### CS 원리: Cache-Aside 패턴

```
[요청] → [Redis에 있나?]
              ├─ 있음 (HIT) → 바로 반환 (0.5ms)
              └─ 없음 (MISS) → DB 조회 (3ms) → Redis에 저장 → 반환
```

캐시 히트율이 80%면 요청 10개 중 8개는 DB를 안 거친다. DB 부하가 80% 줄어든다.

### 적용 대상

```
[현재 캐시됨]
├─ 통계 API (/jobs/stats) — 5분 TTL

[추가 캐시 대상]
├─ 공고 목록 (/jobs?page=0&size=20) — 1분 TTL
│   매번 같은 페이지를 조회하는 사용자가 많음
│   크롤링으로 새 공고가 추가돼도 1분 지연은 허용 가능
│
├─ 공고 상세 (/jobs/{id}) — 5분 TTL
│   공고 내용은 거의 변하지 않음
│   마감/수정 시 캐시 무효화(invalidation) 필요
│
└─ 합격 자소서 목록 (/cover-letters) — 10분 TTL
    변경 빈도가 매우 낮음
```

### 캐시 키 설계

```
// 공고 목록 — 파라미터별 캐시
"jobs:list:page=0&size=20&sort=createdAt,DESC" → JSON
"jobs:list:page=0&size=20&source=SARAMIN" → JSON

// 공고 상세 — ID별 캐시
"jobs:detail:3706" → JSON

// 주의: 파라미터 조합이 많으면 캐시 히트율 하락
// 해결: 자주 쓰는 조합만 캐시 (첫 3페이지, 사이트별 첫 페이지)
```

### 장점

| 항목 | 효과 |
|---|---|
| DB 부하 | 80% 감소 (히트율 80% 가정) |
| 응답 속도 | 3~5ms → 0.5~1ms |
| 구현 복잡도 | 낮음 (Spring @Cacheable 또는 수동 Redis) |
| 비용 | Redis 메모리만 약간 증가 (수 MB) |

### 단점

| 항목 | 위험 |
|---|---|
| 데이터 정합성 | 캐시된 공고가 마감되었는데 사용자에게 보임 (TTL 동안) |
| Cache Stampede | 캐시 만료 직후 동시 요청이 DB에 몰림 |
| 메모리 | 캐시 키가 많아지면 Redis 메모리 증가 |
| 무효화 복잡도 | 크롤링으로 데이터 변경 시 관련 캐시를 찾아서 삭제해야 함 |

### Cache Stampede 방어

캐시 만료 직후 100명이 동시에 같은 데이터를 요청하면 100개의 DB 쿼리가 동시에 발생한다.

```
[캐시 만료] → 요청 100개 동시 도착
    ├─ 요청 1: Redis MISS → DB 쿼리
    ├─ 요청 2: Redis MISS → DB 쿼리
    ├─ ...
    └─ 요청 100: Redis MISS → DB 쿼리
    → DB에 동일 쿼리 100개 동시 실행 → 부하 폭발
```

해결: **Mutex Lock 패턴** — 첫 번째 요청만 DB를 조회하고 나머지는 대기

```java
String cached = redis.get(key);
if (cached != null) return cached;

// 락 획득 시도
if (redis.setNX(key + ":lock", "1", 5)) {
    // 내가 첫 번째 — DB 조회 후 캐시 저장
    String data = db.query();
    redis.set(key, data, TTL);
    redis.del(key + ":lock");
    return data;
} else {
    // 다른 스레드가 조회 중 — 잠깐 대기 후 캐시 재시도
    Thread.sleep(50);
    return redis.get(key);
}
```

이 프로젝트에는 이미 통계 API에 이 패턴이 적용되어 있다.

---

## 전략 2: CDN으로 정적 자원 분리

### 무엇인가

Next.js 프론트엔드의 JS, CSS, 이미지 파일을 **CloudFront(CDN)**에 캐시하여 서버에 요청이 안 오게 하는 것이다.

### CS 원리: CDN Edge Cache

```
[사용자 — 서울] → [CloudFront 서울 엣지] → 캐시 HIT → 바로 반환 (5ms)
                                           캐시 MISS → [원본 서버] → 응답 → 엣지에 캐시

[사용자 — 부산] → [CloudFront 부산 엣지] → 캐시 HIT → 바로 반환 (3ms)
```

CDN은 전 세계에 분산된 캐시 서버다. 사용자와 **물리적으로 가까운 서버**에서 응답하므로 지연이 극히 짧다.

### 이 프로젝트에서의 트래픽 구성 (추정)

```
전체 HTTP 요청 100%
├─ 정적 자원 (JS, CSS, 이미지, 폰트): 약 60%
│   → CDN으로 분리하면 서버 요청 60% 감소
│
└─ API 요청 (JSON): 약 40%
    → 서버가 처리
```

### 장점

| 항목 | 효과 |
|---|---|
| 서버 부하 | 60% 감소 (정적 자원이 서버에 안 옴) |
| 응답 속도 | 정적 파일 50~200ms → 3~10ms |
| 대역폭 | 서버 네트워크 사용량 대폭 감소 |
| 글로벌 | 해외 사용자도 빠른 응답 |

### 단점

| 항목 | 위험 |
|---|---|
| 비용 | CloudFront 월 $1~5 (트래픽에 따라) |
| 캐시 무효화 | 배포 시 이전 JS/CSS가 캐시에 남을 수 있음 |
| 설정 복잡도 | S3 + CloudFront + Route53 연동 필요 |
| CORS | API와 정적 자원 도메인이 달라지면 CORS 설정 필요 |

### Next.js에서의 CDN 적용

```bash
# next.config.js
module.exports = {
    output: 'standalone',
    assetPrefix: 'https://cdn.example.com',  // CDN 도메인
}
```

빌드된 `_next/static/` 폴더를 S3에 업로드하고 CloudFront로 서빙하면 된다.

---

## 전략 3: DB Replica (읽기 분산)

### 무엇인가

PostgreSQL을 **Primary(쓰기)** + **Replica(읽기)** 로 분리하여 읽기 트래픽을 분산하는 것이다.

### CS 원리: Master-Slave Replication

```
[Primary (쓰기)]
    │
    │ WAL (Write-Ahead Log) 스트리밍
    ↓
[Replica (읽기)] ← SELECT 쿼리를 여기서 처리

WAL: PostgreSQL이 모든 변경 사항을 로그로 기록하는 방식.
     Replica는 이 로그를 받아서 동일한 데이터를 유지한다.
```

### 복제 지연 (Replication Lag)

Primary에서 쓰기 → Replica에 반영되기까지 약간의 지연이 있다.

```
시간 0ms: Primary에 INSERT 실행
시간 1ms: WAL 로그 생성
시간 2ms: WAL을 Replica로 전송
시간 3ms: Replica가 WAL 적용
→ 복제 지연 약 1~5ms (같은 리전)
```

이 지연 때문에:
```
1. 사용자가 지원서 저장 (Primary에 INSERT)
2. 즉시 지원서 목록 조회 (Replica에서 SELECT)
3. 방금 저장한 지원서가 안 보임! (Replica에 아직 안 도착)
```

### 해결: 쓰기 후 읽기는 Primary에서

```java
// 일반 읽기 → Replica
@Transactional(readOnly = true)
public List<JobPosting> getJobs() { ... }  // → Replica

// 쓰기 직후 읽기 → Primary
@Transactional
public JobApplication save(JobApplication app) {
    jobApplicationRepository.save(app);  // → Primary에 쓰기
    return jobApplicationRepository.findById(app.getId());  // → Primary에서 읽기
}
```

Spring에서 `@Transactional(readOnly = true)` → Replica, 그 외 → Primary로 라우팅하는 `AbstractRoutingDataSource`를 구현하면 된다.

### 이 프로젝트의 읽기/쓰기 비율

```
읽기 (Replica로 보낼 수 있음): 약 90%
├─ 공고 목록 조회
├─ 공고 상세 조회
├─ 합격 자소서 조회
├─ 프로젝트 목록 조회
├─ 지원 이력 조회
├─ 이력서 조회
└─ AI 분석 결과 조회

쓰기 (Primary에서만): 약 10%
├─ 크롤링 공고 저장
├─ 지원서 생성/수정
├─ AI 분석 결과 저장
├─ 프로젝트 CRUD
└─ 포트폴리오 저장
```

### 장점

| 항목 | 효과 |
|---|---|
| Primary 부하 | 90% 읽기가 Replica로 → Primary 부하 90% 감소 |
| 장애 대응 | Primary 죽으면 Replica를 Primary로 승격 |
| 확장성 | Replica 추가만으로 읽기 성능 선형 증가 |

### 단점

| 항목 | 위험 |
|---|---|
| 복제 지연 | 쓰기 직후 읽기 시 최신 데이터 안 보일 수 있음 (1~5ms) |
| 구현 복잡도 | AbstractRoutingDataSource 구현 필요 |
| 비용 | RDS Replica 추가 비용 (Primary와 동일) |
| 운영 복잡도 | DB 2대 관리, 모니터링 포인트 증가 |

---

## 전략 4: Gzip 응답 압축

### 무엇인가

서버가 JSON 응답을 **gzip으로 압축**하여 네트워크 전송량을 줄이는 것이다.

### CS 원리: HTTP Content-Encoding

```
[클라이언트] → Accept-Encoding: gzip
[서버] → Content-Encoding: gzip
         원본 50KB → 압축 후 15KB (70% 감소)
```

텍스트 기반 데이터(JSON, HTML)는 gzip으로 60~80% 압축된다. 바이너리(이미지, PDF)는 이미 압축되어 있어 효과 없음.

### 압축/해제 비용

```
서버: JSON 직렬화 (1ms) + gzip 압축 (0.5ms) = 1.5ms
네트워크: 50KB → 15KB 전송 (35KB 절약)
클라이언트: gzip 해제 (0.1ms)
```

CPU를 0.5ms 더 쓰지만 네트워크 35KB를 절약한다. 느린 네트워크(모바일 3G)에서 효과가 크다.

### 이 프로젝트에서의 효과

```
공고 목록 20건 JSON: 약 40~60KB → gzip 후 약 10~15KB
공고 상세 HTML 포함: 약 10~30KB → gzip 후 약 3~8KB
```

부하 테스트에서 80초간 521MB 전송 → gzip 적용 시 약 150MB로 감소 추정.

### 설정

```properties
# Spring Boot
server.compression.enabled=true
server.compression.mime-types=application/json,text/html,text/plain,text/css,application/javascript
server.compression.min-response-size=1024
```

1KB 이하 응답은 압축 오버헤드가 더 커서 압축하지 않는다.

### 장점

| 항목 | 효과 |
|---|---|
| 네트워크 | 전송량 60~70% 감소 |
| 응답 속도 | 느린 네트워크에서 체감 빠름 |
| 대역폭 비용 | AWS 아웃바운드 트래픽 비용 절감 |
| 구현 | 설정 한 줄 |

### 단점

| 항목 | 위험 |
|---|---|
| CPU | 압축에 CPU 사용 (0.3~1ms 추가) |
| 이미 빠른 환경 | 로컬/같은 VPC에서는 효과 미미 |
| HTTPS + gzip | BREACH 공격에 취약할 수 있음 (민감 데이터가 있는 경우) |

---

## 전략 5: HikariCP 커넥션 풀 50

### 무엇인가

DB 커넥션 풀 크기를 30에서 50으로 늘려 동시 DB 접근 수를 확대하는 것이다.

### CS 원리: Connection Pool

DB 커넥션은 생성 비용이 크다 (TCP 3-way handshake + 인증 + SSL 등 약 50~100ms). 매 요청마다 커넥션을 만들면 느리다.

```
[커넥션 풀 없이]
요청 1: 커넥션 생성 (50ms) → 쿼리 (1ms) → 커넥션 종료 (5ms) = 56ms
요청 2: 커넥션 생성 (50ms) → 쿼리 (1ms) → 커넥션 종료 (5ms) = 56ms

[커넥션 풀 사용]
시작 시: 커넥션 50개 미리 생성
요청 1: 풀에서 빌림 (0.01ms) → 쿼리 (1ms) → 풀에 반납 (0.01ms) = 1.02ms
요청 2: 풀에서 빌림 (0.01ms) → 쿼리 (1ms) → 풀에 반납 (0.01ms) = 1.02ms
```

### 풀 크기와 동시 접속의 관계

```
동시 요청 100개, 커넥션 풀 30개:
├─ 30개: 즉시 커넥션 획득 → 쿼리 실행
└─ 70개: 대기열에서 기다림 (connection-timeout까지)

동시 요청 100개, 커넥션 풀 50개:
├─ 50개: 즉시 커넥션 획득 → 쿼리 실행
└─ 50개: 대기열에서 기다림 (대기 시간 단축)
```

### 최적 풀 크기 공식

HikariCP 공식 권장:
```
pool_size = (core_count * 2) + effective_spindle_count

4코어 SSD 서버:
pool_size = (4 * 2) + 1 = 9 → 최소 10

하지만 이 공식은 DB 서버 기준.
애플리케이션 서버 관점에서는 동시 요청 수를 고려해야 한다.
```

실무에서는:
- 동시 접속 200명 → 풀 30~50
- 동시 접속 500명 → 풀 50~100
- 단, PostgreSQL의 max_connections 한계도 확인해야 함 (기본 100)

### PostgreSQL max_connections

```
PostgreSQL 기본: max_connections = 100
HikariCP 풀 50 + 관리 커넥션 5 = 55 사용
→ 여유 45개 (Replica, 관리 도구 등)

풀을 100으로 올리면 PostgreSQL도 max_connections를 늘려야 한다.
PostgreSQL 커넥션 1개 = 약 5~10MB 메모리
100개 = 500MB~1GB → 2GB 서버에서는 부담
```

### 장점

| 항목 | 효과 |
|---|---|
| 동시 처리 | 더 많은 요청이 DB 대기 없이 처리 |
| 응답 시간 | 커넥션 대기 시간 감소 |
| 구현 | 설정 변경만 |

### 단점

| 항목 | 위험 |
|---|---|
| 메모리 | 커넥션 1개당 ~5MB → 50개 = 250MB (DB 서버) |
| PostgreSQL 한계 | max_connections 초과 시 연결 거부 |
| 유휴 커넥션 낭비 | 트래픽 적을 때 50개가 놀고 있음 |
| 장애 확산 | DB 느려지면 50개 커넥션이 전부 점유 → 다른 서비스도 영향 |

### HikariCP 설정 상세

```properties
# 최대 풀 크기
spring.datasource.hikari.maximum-pool-size=50

# 최소 유휴 커넥션 (트래픽 적을 때 유지)
spring.datasource.hikari.minimum-idle=10

# 커넥션 획득 대기 시간 (초과 시 예외)
spring.datasource.hikari.connection-timeout=5000

# 유휴 커넥션 유지 시간 (이후 풀에서 제거)
spring.datasource.hikari.idle-timeout=300000

# 커넥션 최대 수명 (이후 새로 생성)
spring.datasource.hikari.max-lifetime=600000
```

`minimum-idle=10`이면 평소에는 10개만 유지하다가 트래픽이 몰리면 50개까지 늘어난다. 트래픽이 줄면 다시 10개로 줄어든다.

---

## 전략별 효과 요약

| 전략 | DB 부하 | 응답 속도 | 네트워크 | 구현 난이도 | 비용 |
|---|---|---|---|---|---|
| Redis 캐시 강화 | -80% | 3ms→0.5ms | - | 중 | Redis 메모리 |
| CDN | - | 정적 -90% | -60% | 중 | 월 $1~5 |
| DB Replica | -90% (읽기) | 쿼리 분산 | - | 상 | DB 2배 |
| gzip 압축 | - | 느린 환경 개선 | -70% | 하 | CPU +0.5ms |
| 커넥션 풀 50 | 대기 감소 | 경합 완화 | - | 하 | DB 메모리 |

---

## 권장 적용 순서

```
1순위: gzip + 커넥션 풀 50 — 설정만으로 즉시 적용
2순위: Redis 캐시 강화 — 코드 수정 필요하지만 효과 큼
3순위: CDN — AWS 배포 시 함께 적용
4순위: DB Replica — 500명 이상에서 필요
5순위: 수평 확장 (서버 2~3대) — Replica로도 부족할 때
6순위: Virtual Thread — 위 방법으로 부족할 때
```
