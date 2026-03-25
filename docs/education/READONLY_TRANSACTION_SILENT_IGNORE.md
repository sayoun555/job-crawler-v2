# @Transactional(readOnly=true) 클래스에서 쓰기가 조용히 무시되는 문제

## 현상

AI 포트폴리오 생성이 완료되고 로그에 "DB 저장 완료"가 찍히지만, 실제 DB에는 저장되지 않음. 에러도 없음. 관리자/일반 유저 모두 동일.

---

## 원인 추적 과정

### 1차 가설: 새 스레드에서 @Transactional이 안 먹는다

비동기 API가 `new Thread()`에서 `generateProjectPortfolio()`를 호출하므로, Spring 프록시 기반 `@Transactional`이 새 스레드에서 작동하지 않을 수 있다고 봤다.

**조치:** `TransactionTemplate`으로 명시적 트랜잭션 적용

**결과:** 여전히 저장 안 됨. 로그에 "DB 저장 완료"가 찍히는데 DB에 반영 안 됨.

### 2차 가설: readOnly 트랜잭션이 쓰기를 무시한다

클래스 레벨에 `@Transactional(readOnly = true)`가 걸려 있었다:

```java
@Transactional(readOnly = true)  // ← 클래스 레벨
public class AiAutomationServiceImpl {
```

`TransactionTemplate`의 기본 전파 속성은 `PROPAGATION_REQUIRED`다. 이미 readOnly 트랜잭션이 열려 있으면 그 트랜잭션에 참여한다.

```
[readOnly 트랜잭션 열림 (클래스 레벨)]
    ↓
[TransactionTemplate — PROPAGATION_REQUIRED]
    → 기존 readOnly 트랜잭션에 참여
    → save() 호출
    → DB가 readOnly 모드이므로 쓰기 무시
    → 에러 없이 성공한 것처럼 반환
    ↓
[로그: "DB 저장 완료"] ← 실제로는 커밋 안 됨
```

**핵심:** PostgreSQL은 readOnly 트랜잭션에서 INSERT/UPDATE를 에러 없이 무시할 수 있다. 특히 JPA의 dirty checking 방식에서는 flush 시점에 readOnly면 변경사항을 아예 보내지 않는다.

---

## 해결

`TransactionTemplate`에 `PROPAGATION_REQUIRES_NEW`를 설정해 readOnly 트랜잭션과 무관한 새 쓰기 트랜잭션을 강제로 연다.

```java
// Before — readOnly 트랜잭션에 참여 (쓰기 무시됨)
var txTemplate = new TransactionTemplate(transactionManager);
txTemplate.executeWithoutResult(status -> {
    projectRepository.save(p);  // 커밋 안 됨
});

// After — 새 쓰기 트랜잭션 강제 생성
var txTemplate = new TransactionTemplate(transactionManager);
txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
txTemplate.executeWithoutResult(status -> {
    projectRepository.save(p);  // 정상 커밋
});
```

---

## 왜 에러가 안 나는가

이 문제가 위험한 이유는 **실패해도 에러가 나지 않는다**는 점이다.

### JPA의 동작 방식

```
1. save(entity) 호출
2. 영속성 컨텍스트에 entity 등록 (메모리)
3. 트랜잭션 커밋 시점에 flush → DB로 SQL 전송
4. readOnly = true이면 flush를 생략
5. 결과: save()는 성공, SQL은 안 나감, 에러 없음
```

JPA의 `FlushMode`가 readOnly 트랜잭션에서는 `MANUAL`로 바뀌어 자동 flush가 안 된다. `save()` 자체는 영속성 컨텍스트에 등록만 하므로 성공하고, 실제 SQL이 DB로 나가지 않는다.

### 로그가 "저장 완료"를 찍는 이유

```java
projectRepository.save(p);  // ← 여기서 에러 안 남
log.info("DB 저장 완료");   // ← 그래서 여기까지 도달
```

`save()` 이후 `log.info()`까지 정상 실행되니까 개발자 입장에서는 저장된 줄 안다.

---

## 트랜잭션 전파 속성 비교

| 전파 속성 | 동작 | readOnly 환경에서 |
|---|---|---|
| REQUIRED (기본) | 기존 트랜잭션 있으면 참여 | readOnly에 참여 → 쓰기 무시 |
| REQUIRES_NEW | 기존 트랜잭션 무시, 새로 생성 | 새 쓰기 트랜잭션 → 정상 커밋 |
| NOT_SUPPORTED | 트랜잭션 없이 실행 | 트랜잭션 자체가 없어 autocommit |

---

## 설계 원칙

### 클래스 레벨 readOnly + 메서드 레벨 쓰기 = 위험

```java
@Transactional(readOnly = true)  // 클래스 레벨
public class MyService {

    @Transactional  // 메서드 레벨로 오버라이드 — 일반 호출에서는 OK
    public void save() { ... }
}
```

일반 호출에서는 메서드 레벨 `@Transactional`이 클래스 레벨을 오버라이드한다. 하지만 **새 스레드에서 호출하면 프록시를 거치지 않아 오버라이드가 안 될 수 있다.**

### 새 스레드 + readOnly 클래스 = REQUIRES_NEW 필수

```java
// 새 스레드에서 DB 쓰기가 필요할 때
var tx = new TransactionTemplate(transactionManager);
tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
tx.executeWithoutResult(status -> {
    repository.save(entity);
});
```

이 패턴이면 어떤 상황에서든 쓰기가 보장된다.

---

## 디버깅 팁

이 문제를 빠르게 발견하려면:

1. **저장 후 즉시 조회로 검증**
```java
repository.save(entity);
Entity check = repository.findById(id).orElse(null);
log.info("검증: {}", check != null ? check.getField() : "NULL");
```

2. **SQL 로그 활성화**
```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
```
readOnly에서 flush가 생략되면 INSERT/UPDATE SQL이 안 찍힌다.

3. **트랜잭션 상태 로그**
```java
log.info("readOnly: {}", TransactionSynchronizationManager.isCurrentTransactionReadOnly());
```
