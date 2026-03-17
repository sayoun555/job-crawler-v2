# 채용 사이트 이력서 자동 연동 개발기

> 앱에서 이력서를 한 번 작성하면, 사람인·잡코리아·잡플래닛·링커리어에 자동으로 등록되는 기능을 만들기까지의 과정

## 1. 문제 정의

### 배경
취업 자동화 플랫폼(이끼잡)을 개발하고 있다. 크롤링으로 채용 공고를 수집하고, AI로 자소서/포트폴리오를 생성하는 기능까지 구현했다. 그런데 **이력서는 여전히 각 채용 사이트에 직접 가서 수동으로 입력해야 했다.**

4개 사이트(사람인, 잡코리아, 잡플래닛, 링커리어)마다 폼 구조가 전부 다르고, 학력·경력·스킬·자격증·어학·활동·자기소개서 등 입력할 항목이 수십 개다. 하나 바꾸면 4곳 모두 수정해야 한다.

### 목표
**앱에서 이력서를 한 번 작성 → 버튼 하나로 4개 사이트에 자동 등록**

---

## 2. 설계

### 아키텍처
기존 자동 지원(AutoApply) 기능의 Strategy 패턴을 그대로 활용했다.

```
[프론트: /resume] → [ResumeController] → [ResumeService]
                                                ↓
                                     [ResumeSyncRobot]
                                          ↓ (Strategy)
                          [ResumeProvider 구현체 4개]
                               ↓ (Playwright)
                        [외부 사이트 폼 입력]
```

### 도메인 모델
Resume를 루트 애그리거트로, 7개 하위 엔티티를 `@OneToMany(cascade=ALL, orphanRemoval=true)`로 관리한다.

- Resume (기본정보 + 자기소개)
- ResumeEducation (학력)
- ResumeCareer (경력)
- ResumeSkill (스킬)
- ResumeCertification (자격증)
- ResumeLanguage (어학)
- ResumeActivity (활동/수상)
- ResumePortfolioLink (포트폴리오)

### 사이트별 폼 구조 분석
Playwright MCP로 4개 사이트의 이력서 페이지를 실제로 열어서 HTML 구조를 분석했다.

| 사이트 | 패턴 | 특이사항 |
|--------|------|----------|
| 사람인 | 섹션별 "추가" → 인라인 폼 → 저장 | jQuery 이벤트 위임, 자동완성 필수, hidden 필드 다수 |
| 잡코리아 | "필드추가" → 섹션 활성화 → 이력서저장 | beforeunload 다이얼로그 |
| 잡플래닛 | 단일 긴 폼 + "+추가" 버튼 | 시도/시군구 2단계 드롭다운 |
| 링커리어 | 섹션별 URL → "항목 추가하기" | SPA, 섹션별 네비게이션 |

---

## 3. Phase 1: 이력서 CRUD (성공)

### 구현
- 백엔드: 도메인 8개 엔티티 + Repository + Service(인터페이스+Impl) + Controller
- 프론트: `/resume` 페이지 + 11개 컴포넌트 (Card 기반 레이아웃)
- API 클라이언트: resumeApi 타입 + 함수

### 고민: enum 한글 표시
백엔드에서 `COLLEGE_4Y`, `GRADUATED` 같은 영문 enum을 프론트에서 한글로 표시해야 했다.

**해결**:
- 백엔드: enum에 `from(String)` 메서드 추가 — 영문 이름이든 한글 description이든 매핑
- 프론트: `SCHOOL_TYPE_LABEL` 매핑 객체로 영문→한글 변환

### 고민: 다크모드
앱이 다크모드인데 폼이 `bg-gray-50` (흰색)이라 입력 필드가 안 보였다.

**해결**: `bg-gray-50` → `bg-muted`, `border-gray-200` → `border-border`, `text-gray-900` → `text-foreground` — shadcn/ui 테마 대응 클래스로 교체

---

## 4. Phase 2: 사람인 자동 연동 (14번의 시도)

### 시도 1~3: 기본 Playwright (실패)
```java
page.locator("#school input[placeholder*='학교명']").fill("한국국제대학교");
```
**문제**: 사람인의 실제 HTML과 셀렉터가 달랐다. `placeholder*='학교명'`으로 찾았지만 실제로는 `placeholder='학교명 *'` (별표 포함).

### 시도 4~5: 실제 HTML 기반 셀렉터 (부분 성공)
사용자가 사람인 HTML을 직접 가져다줌. `name` 속성 기반으로 셀렉터 수정.
```java
page.locator("input[name='school_nm[]']").fill("한국국제대학교");
```
**문제**: `fill()`로 값을 넣으면 사람인의 jQuery 자동완성이 트리거되지 않아 유효성 검사 실패. `school_direct[]` hidden 필드가 세팅되지 않음.

### 시도 6~7: force:true 클릭 (실패)
```java
addBtn.first().click(new Locator.ClickOptions().setForce(true));
```
**문제**: `force:true`는 Playwright의 가시성 체크를 우회하지만, jQuery의 이벤트 위임(`evtLayerSave`, `evtWriteItem`)이 제대로 트리거되지 않았다. 로그에는 "성공"이라고 나오지만 실제 사이트에 저장 안 됨.

### 시도 8~9: page.evaluate() JS 클릭 (부분 성공)
```java
page.evaluate("() => { document.querySelector('button.evtResumeSave').click(); }");
```
**문제**: JS `element.click()`은 `confirm()` 다이얼로그를 블로킹해서 `page.onDialog()`가 동작하지 않음. `window.confirm = () => true` 오버라이드로 해결했지만, 여전히 학교명 유효성 검사 실패.

### 시도 10~11: 자동완성 드롭다운 클릭 (부분 성공)
```java
input.first().type(schoolName, new Locator.TypeOptions().setDelay(100));
// ... 3초 대기 후 드롭다운 클릭
```
**문제**: `type()`으로 입력은 됐지만, 드롭다운이 `openForm` 로케이터 밖에 있거나, display:none 컨테이너 안에 있어서 Playwright `.click()`이 타임아웃.

### 시도 12~13: "직접 등록하기" 링크 클릭 (실패)
```java
Locator directLink = openForm.locator("a.link_directly.evtReturnAutoComplete");
directLink.first().click(new Locator.ClickOptions().setForce(true));
```
**문제**: 사람인의 `evtReturnAutoComplete` jQuery 핸들러가 `force:true` 클릭에서 동작하지 않음. 클릭은 되지만 hidden 필드(`school_cd[]`)가 세팅되지 않음.

### 시도 14: JS 직접 세팅 (부분 성공, 세션 만료)
```java
page.evaluate("(name) => { input.value = name; directInput.value = 'y'; }", schoolName);
```
세션 만료로 실제 테스트 못 함.

### 근본 원인 분석
14번의 시도를 통해 파악한 핵심 문제:

1. **사람인은 jQuery 이벤트 위임을 사용** — `$(document).on('click', '.evtLayerSave', handler)` 방식. Playwright의 `force:true` 클릭은 네이티브 DOM 이벤트만 발생시켜 jQuery 핸들러가 안 먹힘
2. **자동완성 필드는 hidden 필드 연동 필수** — 학교명을 입력해도 `school_cd[]`나 `school_direct[]`가 세팅되지 않으면 유효성 검사 실패
3. **오버레이/팝업이 클릭 차단** — `sri_dimmed`, 날짜 피커, AI 자소서 팝업 등이 저장 버튼을 가림
4. **headless 브라우저의 한계** — 실시간으로 페이지 상태를 볼 수 없어 디버깅이 극도로 어려움

---

## 5. 해결: Playwright MCP 방식으로 전환

### 왜 MCP가 됐나

| Java Playwright (headless) | Playwright MCP |
|---|---|
| 미리 짜놓은 셀렉터로만 동작 | 페이지 스냅샷을 보고 실시간 판단 |
| 오버레이/팝업 대응 불가 | 스냅샷에서 보이면 클릭 |
| 자동완성 드롭다운 제어 어려움 | 드롭다운 나타나면 바로 클릭 |
| 디버깅 = 로그만 봄 | 스크린샷으로 즉시 확인 |

### MCP로 사람인 연동 성공 과정

**1단계: 학력**
```
1. 학력 "추가" 버튼 클릭 (ref로 정확히 지정)
2. 학력구분 combobox → "대학ㆍ대학원 이상 졸업" 선택
3. 대학구분 combobox → "대학교(4년)" 선택
4. 학교명: type()으로 입력 (스냅샷에는 값이 보이지만...)
5. 저장 클릭 → "저장에 필요한 정보를 작성해주세요" 에러!
6. evaluate()로 input.value 확인 → "한국국제대학교" 있음
7. 그런데 화면에서는 비어있음 → 두 번째 폼(새로 추가한 것)의 input이 비어있었음!
8. evaluate()로 마지막 폼의 school_nm[] + school_direct[]=y 직접 세팅
9. 저장 → 성공! "저장되었습니다."
```

**핵심 발견**: `type()`이 첫 번째 input(기존 학력)에 입력됐고, 새로 추가한 폼의 input은 비어있었다. JS evaluate로 **마지막 폼**의 input에 직접 세팅해야 했다.

**2단계: 경력**
```
1. 경력 "추가" 클릭
2. 회사명: JS evaluate로 career_company_nm[] 직접 세팅
3. 입사/퇴사년월: fill()
4. 직무: 클릭 → 직무 검색 팝업 열림 → "백엔드" type() + Enter
5. 검색 결과에서 "백엔드/서버개발 IT개발·데이터" 클릭
6. 근무부서, 담당업무 fill()
7. 날짜 피커가 저장 버튼 가림 → Escape로 닫음
8. 저장 → 성공!
```

**3단계: 스킬**
```
1. 스킬 "추가" 클릭
2. 추천 스킬 체크박스에서 Java, Redis, MySQL, PostgreSQL 라벨 클릭
3. 저장 → 성공!
```

**4단계: 자기소개서**
```
1. 좌측 메뉴 "자기소개서" 클릭
2. "추가" 클릭
3. 제목: fill("자기소개서")
4. 내용: JS evaluate로 textarea 세팅 (fill()은 이벤트 문제)
5. 저장 → 성공!
```

**5단계: 작성완료**
```
1. 개인정보 수집 동의 체크박스 클릭
2. "작성완료" 클릭 → "포지션 제안받기 설정을 완료해주세요"
3. "좋은 포지션이 있다면 제안 받을래요" 클릭
4. "작성완료" 다시 클릭
5. → "이력서 등록이 완료되었습니다." 페이지로 이동! 🎉
```

---

## 6. 핵심 교훈

### 1. 외부 사이트 자동화는 "보면서 하는 것"이 맞다
headless로 미리 셀렉터를 짜놓는 방식은 사이트가 복잡할수록 실패 확률이 높다. AI가 페이지 스냅샷을 보고 실시간으로 판단하는 MCP 방식이 훨씬 안정적이다.

### 2. hidden 필드가 핵심
사람인은 표시되는 input 외에 `school_cd[]`, `school_direct[]`, `career_job_category[]` 등 hidden 필드가 유효성 검사의 핵심이다. 자동완성에서 선택하지 않으면 이 필드들이 비어있어 저장이 안 된다.

### 3. jQuery 이벤트 위임 vs Playwright
jQuery의 `$(document).on('click', '.evtClass', handler)` 방식은 Playwright의 `force:true` 클릭에서 동작하지 않을 수 있다. 반면 MCP의 실제 마우스 클릭은 정상 동작한다.

### 4. 폼이 여러 개일 때 "마지막 폼" 주의
"추가" 버튼을 누르면 새 폼이 생기는데, `querySelector`로 찾으면 첫 번째(기존) 폼이 잡힌다. `querySelectorAll`로 마지막 것을 찾아야 한다.

### 5. 오버레이는 JS로 제거
날짜 피커, 안내 팝업, dimmed 배경 등이 버튼을 가린다. `page.keyboard.press('Escape')` 또는 JS evaluate로 `style.display='none'` 처리.

---

## 7. 기술 스택

- **백엔드**: Java 21, Spring Boot 3.4, JPA, PostgreSQL, Redis
- **프론트**: Next.js (App Router), TypeScript, Tailwind CSS, shadcn/ui
- **자동화**: Playwright (Java + MCP)
- **설계 패턴**: Strategy (ResumeProvider), Composition (ResumeSyncRobot)
- **코딩 원칙**: Clean Code, OOP, SOLID (AGENTS.md)

---

## 8. 다음 단계

1. 사람인 나머지 섹션 (경험/활동, 자격/어학/수상, 포트폴리오, 취업우대사항)
2. AI + MCP 아키텍처 설계 (자동화를 AI 에이전트가 수행)
3. 잡코리아, 잡플래닛, 링커리어 연동
4. 연동 결과 검증 로직
