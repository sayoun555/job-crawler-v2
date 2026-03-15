# 🔧 트러블슈팅 기록

> 개발 과정에서 마주친 기술적 문제들과 해결 과정을 기록합니다.

---

## 1. H2 인메모리 DB → PostgreSQL 전환

### 🚨 문제
개발 환경에서 H2 인메모리 DB(`jdbc:h2:mem:jobcrawler`)를 사용하던 중, **서버를 재시작할 때마다 모든 데이터(회원 정보, 공고, 지원서 등)가 증발**하는 문제 발생.
프론트엔드에 남아있는 이전 세션의 JWT 토큰으로 API 요청 시, 존재하지 않는 유저/공고 ID를 참조하면서 `EntityNotFoundException` → `500 Internal Server Error` 발생.

### 🤔 원인 분석
- `spring.jpa.hibernate.ddl-auto=create-drop` 설정으로 매 기동 시 스키마가 재생성됨
- JWT 토큰은 브라우저 `localStorage`에 영속적으로 저장되지만, 토큰 안의 `userId`에 해당하는 레코드는 DB에 없는 상태

### 🛠 해결
1. Homebrew로 설치된 로컬 PostgreSQL을 `brew services start postgresql@14`로 기동
2. `jobcrawler` DB 및 계정 생성
3. `application-dev.properties`를 PostgreSQL 연결로 교체, `ddl-auto=update`로 변경

```diff
-spring.datasource.url=jdbc:h2:mem:jobcrawler
-spring.datasource.driverClassName=org.h2.Driver
-spring.jpa.hibernate.ddl-auto=create-drop
-spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
+spring.datasource.url=jdbc:postgresql://localhost:5432/jobcrawler
+spring.datasource.driverClassName=org.postgresql.Driver
+spring.jpa.hibernate.ddl-auto=update
+spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### 💡 결과
서버 재시작 후에도 데이터가 유지되며, 영구적인 개발 환경에서 안정적인 테스트 가능.

---

## 2. PostgreSQL `LOWER(bytea)` 함수 타입 오류

### 🚨 문제
H2에서 PostgreSQL로 전환 직후, `GET /api/v1/jobs?page=0&size=20` 엔드포인트에서 `500` 에러 발생.

```
InvalidDataAccessResourceUsageException: 
ERROR: function lower(bytea) does not exist
Hint: No function matches the given name and argument types.
```

### 🤔 원인 분석
`JobPostingRepository`의 JPQL 쿼리에서 `:keyword` 파라미터가 `null`로 전달될 때, **H2는 null을 String으로 추론**했으나, **PostgreSQL은 null을 `bytea`(바이너리) 타입으로 추론**함.
이로 인해 `LOWER(CONCAT('%', :keyword, '%'))` 구문이 `LOWER(bytea)`로 해석되어 함수 매칭 실패.

### 🛠 해결
JPQL 내에서 `CAST(:keyword AS text)`를 적용하여 PostgreSQL이 파라미터 타입을 올바르게 추론하도록 강제:

```diff
-AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
-   OR LOWER(j.company) LIKE LOWER(CONCAT('%', :keyword, '%')))
+AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
+   OR LOWER(j.company) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
```

### 💡 결과
H2와 PostgreSQL 환경 모두에서 동작하는 JPQL 쿼리로 수정 완료. `null` 키워드 전달 시에도 정상 페이징 응답.

---

## 3. React Hydration Error (`<button>` 중첩)

### 🚨 문제
Next.js 프론트엔드에서 React Hydration 에러 발생:
```
In HTML, <button> cannot be a descendant of <button>.
This will cause a hydration error.
```

### 🤔 원인 분석
Base UI의 `DialogTrigger`, `SheetTrigger`, `DropdownMenuTrigger` 컴포넌트가 내부적으로 `<button>` 태그를 렌더링하는데, 그 안에 또다시 `<Button>` 컴포넌트(역시 `<button>`)를 자식으로 넣으면 HTML 표준 위반이 발생.

```tsx
// ❌ 잘못된 사용: <button> 안에 <button>
<SheetTrigger>
    <Button variant="outline">필터</Button>
</SheetTrigger>
```

### 🛠 해결
Base UI의 `render` prop 패턴을 올바르게 적용하여 트리거 컴포넌트가 자식 `<Button>`의 DOM을 그대로 사용하도록 변경:

```tsx
// ✅ 올바른 사용: render prop으로 Button을 트리거의 렌더 요소로 지정
<SheetTrigger render={
    <Button variant="outline" size="sm" className="gap-2">
        필터 {activeCount > 0 && <Badge variant="secondary">{activeCount}</Badge>}
    </Button>
} />
```

### 💡 결과
`filter-panel.tsx`, `navbar.tsx`, `projects/page.tsx`, `applications/page.tsx`, `templates/page.tsx` 5개 파일의 버튼 중첩을 모두 해소.

---

## 4. Base UI `nativeButton` 경고

### 🚨 문제
Navbar에서 `<Button render={<Link>}>` 패턴 사용 시 Base UI 경고:
```
Base UI: A component that acts as a button expected a native <button>
because the `nativeButton` prop is true.
```

### 🤔 원인 분석
`<Button render={<Link href="/login">로그인</Link>} />`는 실제로 `<a>` 태그를 렌더링하지만, Base UI의 Button primitive는 기본적으로 `nativeButton={true}`이므로 네이티브 `<button>`을 기대함.

### 🛠 해결
`<Link>`를 렌더링하는 `<Button>`에 `nativeButton={false}`를 명시:

```tsx
<Button variant="ghost" nativeButton={false}
    render={<Link href="/login">로그인</Link>} />
```

### 💡 결과
콘솔 경고 완전 제거, 시맨틱 HTML 접근성 유지.

---

## 5. OpenClaw HTTP API 엔드포인트 미활성화

### 🚨 문제
로컬 AI(OpenClaw)를 HTTP API(`/v1/chat/completions`)로 연동 시도했으나, `Connection refused` 또는 `404 Not Found` 발생.

### 🤔 원인 분석
OpenClaw의 기본 설정에서 `chatCompletions` HTTP 엔드포인트가 **비활성화** 상태였음.
OpenClaw 소스코드(`node_modules`)를 직접 분석하여 정확한 설정 키를 발견.

### 🛠 해결
`~/.openclaw/openclaw.json`에 다음 설정 추가 후 게이트웨이 재시작:

```json
{
  "gateway.http.endpoints.chatCompletions.enabled": true
}
```

### 💡 결과
`http://localhost:18789/v1/chat/completions` 엔드포인트 정상 작동 확인. CLI 대신 깔끔한 HTTP REST 방식 AI 연동 성공.

---

## 6. 포트 충돌 (`Port 8080 was already in use`)

### 🚨 문제
Spring Boot 서버를 재시작할 때 간헐적으로 `Port 8080 was already in use` 에러 발생.

### 🤔 원인 분석
이전 프로세스가 완전히 종료되기 전에 새 프로세스가 같은 포트를 바인딩하려 함. `kill` 후 대기 시간 부족.

### 🛠 해결
```bash
lsof -ti :8080 | xargs kill -9; sleep 2; ./gradlew bootRun
```

### 💡 결과
강제 종료 후 2초 대기를 통해 포트 해제를 보장한 후 재기동하여 충돌 방지.
