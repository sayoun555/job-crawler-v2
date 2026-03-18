# 크롤링 데이터에서 표가 깨지는 문제: innerText에서 innerHTML로의 전환기

## 목차
1. [문제 발견](#1-문제-발견)
2. [원인 분석](#2-원인-분석)
3. [해결 방법 탐색](#3-해결-방법-탐색)
4. [시행착오](#4-시행착오)
5. [최종 해결: innerHTML + 이중 소독](#5-최종-해결-innerhtml--이중-소독)
6. [다크모드에서 글씨가 안 보이는 2차 문제](#6-다크모드에서-글씨가-안-보이는-2차-문제)
7. [결과](#7-결과)
8. [배운 점](#8-배운-점)

---

## 1. 문제 발견

사람인에서 크롤링한 채용 공고 상세 페이지를 우리 사이트에 표시하는데, 모집부문 표가 완전히 깨져서 나왔다.

원본 사이트에서는 이렇게 보인다:

```
| 구분    | 지원분야   | 구분      | 수행업무                    | 자격요건 및 우대사항              | 근무지 |
|---------|-----------|----------|---------------------------|-------------------------------|-------|
| 관리직  | 재무관리   | 신입/경력 | ㆍ출납관리 및 회계장부관리      | [우대] 경영학과 또는 회계관련 전공자 | 부안  |
| 관리직  | 사육관리   | 신입/경력 | ㆍ육계/삼계/가금 사육농장 관리  | [우대] 축산학관련 전공자           | 부안  |
```

하지만 우리 사이트에서는:

```
구분	지원분야	구분	수행업무	자격요건 및 우대사항	근무지
관리직	재무관리	신입/
경력	ㆍ출납관리 및 회계장부관리	[우대사항]
ㆍ경영학과 또는 회계관련학과 전공자	부안
(본사)
사육관리	신입/
경력	ㆍ육계/삼계/가금 사육농장 및
    직영농장 사육관리	[우대사항]
```

탭(`\t`)과 줄바꿈(`\n`)이 뒤섞여서 어디가 행이고 어디가 열인지 알 수 없는 상태였다.

더 심각한 문제는, 이 표 데이터가 `requirements`(자격 요건) 필드에까지 잘못 들어가서 "자격 요건" 섹션에 모집부문 표 전체가 표시되고 있었다.

---

## 2. 원인 분석

### Playwright의 innerText()가 문제였다

크롤러 코드를 보니, 사람인 공고 상세 페이지는 iframe 안에 본문이 있고, 그 안에서 `innerText()`로 텍스트를 추출하고 있었다.

```java
// SaraminParser.java - 기존 코드
Locator userContent = frameLoc.locator(".user_content");
if (userContent.count() > 0) {
    String text = safeText(userContent.first()); // innerText() 사용
    descBuilder.append(text);
}
```

`innerText()`는 **브라우저가 렌더링한 텍스트**를 반환한다. HTML 태그를 모두 벗기고 순수 텍스트만 준다. 이게 일반 텍스트에는 문제가 없지만, `<table>` 구조를 가진 데이터에서는 치명적이다.

### 왜 표가 깨지는가

원본 HTML:
```html
<table>
  <tr>
    <td>관리직</td>
    <td>재무관리</td>
    <td>신입/<br>경력</td>
    <td>ㆍ출납관리 및 회계장부관리</td>
  </tr>
</table>
```

`innerText()`가 이걸 변환하면:
```
관리직\t재무관리\t신입/\n경력\tㆍ출납관리 및 회계장부관리
```

`<td>` 사이에는 `\t`(탭)이, `<br>` 위치에는 `\n`(줄바꿈)이 들어간다. 문제는 **하나의 셀 안에 줄바꿈이 있으면** 한 행이 여러 줄로 나뉘어서 행과 열의 경계를 알 수 없게 된다는 것이다.

```
신입/       ← 이게 셀 안의 줄바꿈인지
경력        ← 새로운 행의 시작인지 알 수 없다
```

### requirements 필드 오염

`extractRequirementsFromDescription()` 메서드는 description에서 "자격요건"이라는 키워드가 포함된 줄 이후를 requirements에 넣는다. 그런데 모집부문 표의 헤더 셀에 "자격요건 및 우대사항"이라는 텍스트가 있어서, 표 데이터 전체가 requirements 필드로 빨려 들어갔다.

---

## 3. 해결 방법 탐색

다섯 가지 방법을 검토했다.

### 방법 1: innerHTML로 HTML 자체를 저장
크롤링 시 `innerText()` 대신 `innerHTML()`을 사용해서 HTML 태그를 그대로 가져온다. `<table>` 구조가 보존되므로 프론트에서 그대로 렌더링하면 된다.

- 장점: 표/서식 완벽 보존, 가장 정확
- 단점: XSS 방지를 위해 서버+클라이언트 이중 소독 필요
- 저장 용량: 기존 5KB → ~10KB (TEXT 컬럼이라 문제없음)

### 방법 2: iframe으로 원본 페이지 임베딩
크롤링한 HTML을 sandboxed iframe으로 렌더링한다.

- 장점: 보안 최고, 원본 그대로
- 단점: 높이 자동 조절 어려움, 다크모드 적용 불가, 접근성 나쁨

### 방법 3: 스크린샷
크롤링 시 상세 페이지를 스크린샷으로 캡처해서 이미지로 저장한다.

- 장점: 100% 원본 동일
- 단점: 텍스트 검색 불가, AI 분석 불가, 용량 큼, 모바일 반응형 불가

### 방법 4: 프론트에서 탭 데이터 파싱 (현재 방식 개선)
`innerText()`로 가져온 텍스트에서 `\t` 구분 데이터를 파싱해서 마크다운 테이블이나 HTML 테이블로 변환한다.

- 장점: 백엔드 수정 없음
- 단점: 멀티라인 셀 때문에 정확한 행 구분이 원천적으로 불가능

### 방법 5: 크롤링 시 마크다운으로 변환
`innerHTML()`을 가져온 뒤 `flexmark-java` 같은 라이브러리로 마크다운으로 변환해서 저장한다.

- 장점: 기존 `<Markdown>` 컴포넌트 활용 가능
- 단점: colspan/rowspan, 멀티라인 셀이 GFM 마크다운 스펙에서 지원되지 않음

### 선택: 방법 1 (innerHTML + 이중 소독)

**방법 4를 먼저 시도했다가 실패한 후** 방법 1을 선택했다. 이유:

1. Playwright에서 `innerText()` → `innerHTML()`은 **한 줄만 바꾸면** 되는 최소 변경
2. Jsoup이 이미 의존성에 있어서 서버 소독 추가 비용 없음
3. AI 분석에는 `Jsoup.clean(html, Safelist.none())`으로 태그를 벗기면 됨 (텍스트 추출)
4. 기존 텍스트 데이터와 공존 가능 — 프론트에서 `<table` 태그 유무로 HTML/텍스트 자동 판별

---

## 4. 시행착오

방법 1을 선택하기 전에 방법 4로 여러 차례 시도했다. 기록으로 남겨둔다.

### 시도 1: 탭을 마크다운 테이블로 변환

```typescript
// 프론트에서 탭 라인을 마크다운 테이블로 변환
function convertTabsToTable(text: string): string {
    const lines = text.split("\n");
    for (const line of lines) {
        if (line.includes("\t")) {
            const cells = line.split("\t");
            result.push("| " + cells.join(" | ") + " |");
        }
    }
}
```

**결과**: 멀티라인 셀 때문에 테이블이 잘게 쪼개졌다. "신입/\n경력"이 두 행으로 분리되어 열 수가 맞지 않았다.

### 시도 2: HTML 테이블 컴포넌트로 렌더링

열 수가 같은 줄끼리 행으로 묶고, 다른 줄은 이전 행에 합치는 로직을 구현했다.

```typescript
function TabTable({ rows }: { rows: string[][] }) {
    return (
        <table>
            <thead><tr>{headers.map(h => <th>{h}</th>)}</tr></thead>
            <tbody>{rows.map(row => <tr>{row.map(cell => <td>{cell}</td>)}</tr>)}</tbody>
        </table>
    );
}
```

**결과**: 여전히 행 병합이 부정확해서 "재무관리"는 보이는데 "사육관리"는 안 보이는 등 데이터 손실이 발생했다.

### 시도 3: 탭 줄 제거

표 데이터가 이미지에 포함되어 있으니, 탭이 포함된 줄을 아예 숨겼다.

```typescript
const cleaned = text.split("\n")
    .filter(line => !line.includes("\t"))
    .join("\n");
```

**결과**: "재무관리", "사육관리" 등 표 안에만 있는 데이터가 완전히 사라졌다. AI 분석에서는 언급되는데 화면에서는 안 보이는 모순이 발생.

### 시도 4: 탭을 구분자로 변환

```typescript
let processed = line.replace(/\t/g, "  |  ");
```

**결과**: 읽을 수는 있지만 보기 좋지 않았다. 원본 사이트와의 괴리가 컸다.

**이 시점에서 프론트만으로는 해결이 불가능하다고 판단하고, 크롤링 방식 자체를 바꾸기로 결정했다.**

---

## 5. 최종 해결: innerHTML + 이중 소독

### 5-1. 서버 소독 유틸리티

Jsoup의 `Safelist.relaxed()`를 기반으로 표 관련 태그를 추가 허용했다.

```java
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th",
                     "caption", "col", "colgroup")
            .addAttributes("td", "colspan", "rowspan", "style")
            .addAttributes("th", "colspan", "rowspan", "style")
            .addAttributes("table", "style", "width", "border",
                          "cellspacing", "cellpadding")
            .addAttributes("img", "src", "alt", "width", "height", "style")
            .addProtocols("img", "src", "http", "https");

    public static String sanitize(String dirtyHtml) {
        if (dirtyHtml == null || dirtyHtml.isBlank()) return "";
        String cleaned = Jsoup.clean(dirtyHtml, SAFELIST);
        return stripColorStyles(cleaned);
    }

    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.clean(html, Safelist.none());
    }
}
```

Jsoup의 `clean()` 메서드는 화이트리스트에 없는 모든 태그와 속성을 제거한다. `<script>`, `onclick`, `onerror` 같은 XSS 벡터가 원천 차단된다.

`toPlainText()`는 `Safelist.none()`을 사용해서 모든 HTML 태그를 제거한다. AI 프롬프트에 데이터를 넘길 때 사용한다.

### 5-2. 크롤러 수정

변경은 단 한 줄이다.

```java
// Before: 텍스트만 추출
String text = safeText(userContent.first()); // innerText()
descBuilder.append(text);

// After: HTML 구조 보존
String html = userContent.first().innerHTML();
descBuilder.append(HtmlSanitizer.sanitize(html));
```

`innerHTML()`은 DOM 요소의 내부 HTML을 문자열로 반환한다. `<table><tr><td>관리직</td>...</tr></table>` 같은 구조가 그대로 보존된다.

### 5-3. 프론트 렌더링

클라이언트에서 DOMPurify로 2차 소독 후 `dangerouslySetInnerHTML`로 렌더링한다.

```tsx
import DOMPurify from "dompurify";

function HtmlRenderer({ html }: { html: string }) {
    const clean = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
            "table", "thead", "tbody", "tr", "td", "th",
            "p", "br", "b", "strong", "em", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "div", "span", "a", "img"
        ],
        ALLOWED_ATTR: [
            "colspan", "rowspan", "href", "src", "alt",
            "width", "height", "style"
        ],
    });
    return (
        <div
            className="prose prose-sm dark:prose-invert max-w-none
                [&_table]:w-full [&_table]:border-collapse
                [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-2
                [&_th]:border [&_th]:border-border [&_th]:px-3 [&_th]:py-2
                [&_th]:font-medium [&_th]:bg-muted/50"
            dangerouslySetInnerHTML={{ __html: clean }}
        />
    );
}
```

### 5-4. 하위 호환

기존에 텍스트로 저장된 공고와 새로 HTML로 저장된 공고가 공존해야 한다. `ContentRenderer`에서 `<table`, `<div`, `<p` 태그 유무로 자동 판별한다.

```tsx
function ContentRenderer({ text }: { text: string }) {
    const isHtml = text.includes("<table") || text.includes("<div")
                || text.includes("<p");

    if (isHtml) {
        return <HtmlRenderer html={text} />;
    }

    // 기존 텍스트 데이터: ㆍ 불릿을 마크다운 리스트로 변환
    const cleaned = text.split("\n").map(line => {
        const trimmed = line.trim();
        if (trimmed.startsWith("ㆍ") || trimmed.startsWith("·")) {
            return "- " + trimmed.substring(1).trim();
        }
        return line;
    }).join("\n");
    return <Markdown>{cleaned}</Markdown>;
}
```

### 5-5. 이중 소독이 필요한 이유

왜 서버(Jsoup)와 클라이언트(DOMPurify) 양쪽에서 소독하는가?

**방어적 프로그래밍(Defense in Depth)** 원칙이다.

- Jsoup 소독에 버그가 있거나, 업데이트 전 새로운 XSS 벡터가 발견될 수 있다
- DB에 저장된 후 프론트에서 렌더링하기까지 시간차가 있어, 그 사이 Jsoup 버전의 취약점이 발견될 수 있다
- 서로 다른 라이브러리(Java Jsoup vs JS DOMPurify)가 서로의 빈틈을 보완한다

OWASP에서도 "하나의 방어 수단에 의존하지 말고, 여러 레이어의 방어를 구축하라"고 권고한다.

---

## 6. 다크모드에서 글씨가 안 보이는 2차 문제

innerHTML로 전환하니 표는 잘 나왔지만, 일부 공고에서 **글씨가 안 보이는** 새로운 문제가 발생했다.

### 원인

사람인 공고 작성자가 HTML 에디터에서 직접 스타일을 지정한 경우:

```html
<p style="color: #000000">모집 분야</p>
<div style="background: #ffffff; color: #333333">상세 내용</div>
```

우리 사이트는 다크모드(배경 `#0f172a`)인데, 크롤링된 HTML에 `color: #000000`(검은색)이 인라인으로 박혀있으면 검은 배경에 검은 글씨가 되어 안 보인다.

### 해결

`HtmlSanitizer`에 색상 관련 CSS 속성을 제거하는 로직을 추가했다.

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

`color`, `background`, `background-color` 세 가지 CSS 속성만 선택적으로 제거한다. `width`, `text-align`, `padding` 같은 레이아웃 속성은 유지한다.

정규식 `(?i)\\bcolor\\s*:[^;]+;?`의 의미:
- `(?i)` — 대소문자 무시
- `\\b` — 단어 경계 (background-color의 color와 구분)
- `color\\s*:` — "color:" 또는 "color :"
- `[^;]+` — 세미콜론 전까지의 값
- `;?` — 세미콜론이 있으면 함께 제거

---

## 7. 결과

### Before (innerText)
- 표가 탭+줄바꿈으로 깨져서 읽을 수 없음
- 모집부문 데이터가 requirements 필드에 오염
- 프론트에서 아무리 파싱해도 원본 복원 불가

### After (innerHTML + 이중 소독)
- `<table>` 구조가 그대로 보존되어 원본과 동일한 표 렌더링
- 채용 요약이 `<h3>채용 요약</h3><ul><li>...` 형태로 깔끔하게 정리
- 다크모드에서도 글씨가 정상 표시
- 기존 텍스트 데이터와 호환 유지
- AI 분석에는 `toPlainText()`로 태그를 벗겨서 전달

### 성능 영향
- 크롤링 속도: 변화 없음 (같은 페이지에서 다른 속성을 읽는 것뿐)
- 저장 용량: TEXT 5KB → 10KB (무시 가능)
- Jsoup 소독: 마이크로초 단위
- DOMPurify 소독: 1ms 미만

---

## 8. 배운 점

### 데이터 수집 단계에서 정보를 버리면 복구할 수 없다

`innerText()`는 HTML의 구조 정보(태그, 속성)를 영구적으로 버린다. 한 번 텍스트로 변환되면 원본 표 구조를 복원하는 것은 불가능하다. "나중에 프론트에서 파싱하면 되지"라는 생각은 착각이었다.

**수집 단계에서 가능한 한 많은 정보를 보존하고, 표시 단계에서 필요한 만큼 가공하는 것**이 올바른 방향이다.

### 보안과 기능은 양립할 수 있다

"innerHTML은 XSS 위험이 있으니 innerText를 쓰자"는 맞는 말이지만, 이것 때문에 기능(표 렌더링)을 포기할 필요는 없다. Jsoup + DOMPurify 이중 소독으로 보안을 유지하면서 HTML 구조를 살릴 수 있다.

### 프론트만으로 해결하려 하지 말 것

프론트에서 탭 파싱, 마크다운 변환, HTML 테이블 생성 등 4번의 시도를 했지만 전부 실패했다. 근본 원인이 "데이터 수집 방식"에 있었기 때문이다. 문제의 근원을 파악하고 그 지점을 수정하는 것이 가장 효율적이다.

### 하위 호환은 설계에 포함시킬 것

기존 텍스트 데이터와 새 HTML 데이터가 같은 DB 컬럼에 공존해야 했다. `<table` 태그 유무로 자동 판별하는 단순한 방법으로 해결했다. 데이터 마이그레이션 없이 점진적으로 전환할 수 있었다.
