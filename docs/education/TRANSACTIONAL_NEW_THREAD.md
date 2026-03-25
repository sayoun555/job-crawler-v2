# @Transactional이 새 스레드에서 작동하지 않는 문제

## 현상

포트폴리오 AI 생성이 완료되었는데 DB에 저장되지 않음. 로그에는 "생성 완료"가 찍히지만 프로젝트의 `aiPortfolioContent` 필드가 비어있음.

---

## 원인: Spring @Transactional은 프록시 기반이다

### Spring의 트랜잭션 동작 원리

```
[Controller] → [프록시(AOP)] → [실제 Service 메서드]
                    ↑
              여기서 트랜잭션 시작/커밋
```

Spring은 `@Transactional`이 붙은 메서드를 직접 호출하지 않는다. **CGLIB 프록시**가 메서드를 감싸서 실행 전에 트랜잭션을 열고, 실행 후에 커밋한다.

```java
// 개발자가 작성한 코드
@Transactional
public String generateProjectPortfolio(Long userId, Long projectId) {
    // ...
    projectRepository.save(project);  // ← 커밋될까?
    return content;
}
```

```java
// Spring이 실제로 실행하는 코드 (프록시)
public String generateProjectPortfolio$$proxy(Long userId, Long projectId) {
    TransactionStatus tx = transactionManager.getTransaction(...);  // 트랜잭션 시작
    try {
        String result = 실제메서드.generateProjectPortfolio(userId, projectId);
        transactionManager.commit(tx);  // 커밋
        return result;
    } catch (Exception e) {
        transactionManager.rollback(tx);  // 롤백
        throw e;
    }
}
```

### 문제: 새 스레드에서 호출하면 프록시를 거치지 않는다

```java
// AiController.java
new Thread(() -> {
    try {
        // 이 호출은 프록시를 거치지 않음!
        String content = aiAutomationService.generateProjectPortfolio(userId, projectId);
        aiTaskQueue.complete(taskId, userId, content);
    } catch (Exception e) {
        aiTaskQueue.fail(taskId, userId, e.getMessage());
    }
}).start();
```

실제로는 `aiAutomationService`가 프록시 객체이므로 `@Transactional`이 작동해야 하지만, **트랜잭션 컨텍스트는 ThreadLocal에 저장**된다.

```
메인 스레드: ThreadLocal → [TransactionSynchronizationManager]
새 스레드:   ThreadLocal → [비어있음]
```

Spring의 트랜잭션 매니저는 `TransactionSynchronizationManager`를 사용하는데, 이 클래스는 **ThreadLocal 기반**이다. 새 스레드에서는 ThreadLocal이 비어있어서:

1. 프록시가 트랜잭션을 시작하려 한다
2. 새 트랜잭션이 열린다
3. `projectRepository.save(project)` 실행
4. 메서드가 끝나면 커밋해야 하는데...
5. **readOnly=true 트랜잭션 안에서 쓰기 작업이 무시**되거나, 트랜잭션 전파 설정에 따라 커밋이 안 될 수 있다

이 프로젝트의 경우 클래스 레벨에 `@Transactional(readOnly = true)`가 걸려있었다:

```java
@Transactional(readOnly = true)  // ← 클래스 레벨
public class AiAutomationServiceImpl {

    @Transactional  // ← 메서드 레벨로 readOnly 오버라이드
    public String generateProjectPortfolio(...) {
        // 새 스레드에서 호출 시 readOnly 오버라이드가 제대로 적용되지 않을 수 있음
    }
}
```

---

## 해결: TransactionTemplate으로 명시적 트랜잭션

```java
// Before — @Transactional (새 스레드에서 불안정)
@Transactional
public String generateProjectPortfolio(Long userId, Long projectId) {
    // ... AI 생성 ...
    project.updatePortfolioContent(content);
    projectRepository.save(project);  // 커밋 안 될 수 있음
    return content;
}

// After — TransactionTemplate (새 스레드에서도 확실)
public String generateProjectPortfolio(Long userId, Long projectId) {
    // ... AI 생성 (트랜잭션 밖에서 — DB 안 씀) ...

    // 저장만 명시적 트랜잭션으로
    var txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.executeWithoutResult(status -> {
        Project p = projectRepository.findById(projectId).orElseThrow();
        p.updatePortfolioContent(content);
        projectRepository.save(p);
    });

    return content;
}
```

### TransactionTemplate이 안전한 이유

```java
// TransactionTemplate 내부
public <T> T execute(TransactionCallback<T> action) {
    TransactionStatus status = this.transactionManager.getTransaction(this);  // 직접 트랜잭션 시작
    try {
        T result = action.doInTransaction(status);
        this.transactionManager.commit(status);  // 직접 커밋
        return result;
    } catch (Exception e) {
        this.transactionManager.rollback(status);  // 직접 롤백
        throw e;
    }
}
```

프록시에 의존하지 않고 **직접 트랜잭션을 열고 커밋**한다. 어떤 스레드에서 호출하든 동일하게 작동한다.

---

## 설계 원칙: AI 생성과 DB 저장을 분리

```
[AI 생성 — 오래 걸림, 트랜잭션 불필요]
         ↓
[DB 저장 — 짧음, 트랜잭션 필수]
```

AI 호출은 수십 초가 걸리는데 이 시간 동안 DB 트랜잭션을 열어두면:
- 커넥션을 오래 점유
- 타임아웃 위험
- 다른 요청에 영향

그래서 AI 생성은 트랜잭션 밖에서, 저장만 짧은 트랜잭션으로 처리하는 게 정석이다.

---

## 같은 문제가 발생하는 다른 패턴

### 1. @Async + @Transactional

```java
@Async
@Transactional  // ← 새 스레드에서 호출되므로 주의
public void asyncProcess() { ... }
```

Spring의 `@Async`는 새 스레드에서 실행한다. `@Transactional`과 함께 쓰면 동일한 문제가 발생할 수 있다. `@Async` 메서드 안에서 `TransactionTemplate`을 쓰는 게 안전하다.

### 2. CompletableFuture

```java
CompletableFuture.runAsync(() -> {
    service.transactionalMethod();  // ← 새 스레드
});
```

### 3. 내부 메서드 호출 (self-invocation)

```java
public class MyService {
    public void outer() {
        inner();  // ← 프록시를 거치지 않음!
    }

    @Transactional
    public void inner() { ... }  // ← 트랜잭션 안 열림
}
```

같은 클래스 내에서 `@Transactional` 메서드를 직접 호출하면 프록시를 거치지 않는다. 이것도 같은 원리.

---

## 정리

| 호출 방식 | @Transactional 작동 | TransactionTemplate 작동 |
|---|---|---|
| Controller → Service (일반) | O | O |
| new Thread() → Service | 불안정 | O |
| @Async → Service | 불안정 | O |
| CompletableFuture → Service | 불안정 | O |
| 같은 클래스 내부 호출 | X | O |

**원칙: 새 스레드에서 DB 쓰기가 필요하면 TransactionTemplate을 쓴다.**
