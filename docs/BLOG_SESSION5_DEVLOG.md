# 취업 자동화 플랫폼 개발기 (세션 5): 코드 품질, 파싱 버그, 그리고 마감 공고 처리

## 목차
1. [Setter 남발 412줄 → Clean Code 250줄로의 여정](#1-setter-남발-412줄--clean-code-250줄로의-여정)
2. ["5년 이상, JIRA, react" — 경력과 기술스택이 뒤섞인 콤마 지옥](#2-5년-이상-jira-react--경력과-기술스택이-뒤섞인-콤마-지옥)
3. [requirements에 페이지 전체가 들어간다고요?](#3-requirements에-페이지-전체가-들어간다고요)
4. [같은 공고를 10명이 분석하면 API 호출도 10번?](#4-같은-공고를-10명이-분석하면-api-호출도-10번)
5. [마감된 공고가 영원히 살아있는 문제](#5-마감된-공고가-영원히-살아있는-문제)
6. [세션 4 미문서화: AI 적합률 프롬프트 60% 가중치 개선](#6-세션-4-미문서화-ai-적합률-프롬프트-60-가중치-개선)
7. [세션 4 미문서화: OCR 이미지 텍스트 추출](#7-세션-4-미문서화-ocr-이미지-텍스트-추출)
8. [세션 4 미문서화: 4개 사이트 각각 다른 마감일 파싱](#8-세션-4-미문서화-4개-사이트-각각-다른-마감일-파싱)
9. [적합률 근거 팝업 — "왜 32%인데?"에 답하기](#9-적합률-근거-팝업--왜-32인데에-답하기)
10. [공고 삭제 시 고아 데이터 — 연관 데이터 동기 삭제](#10-공고-삭제-시-고아-데이터--연관-데이터-동기-삭제)
11. [필터 패널 정리 — 전체 탭 vs 사이트별 탭](#11-필터-패널-정리--전체-탭-vs-사이트별-탭)

---

## 1. Setter 남발 412줄 → Clean Code 250줄로의 여정

### 문제

`CrawledJobData` 클래스를 열어보니 `@Setter`가 클래스 레벨에 붙어있었다. 412줄짜리 거대한 DTO인데, 크롤러 파서들이 이런 식으로 사용하고 있었다:

```java
// 기존 코드 - 파서에서 이렇게 씀
CrawledJobData data = new CrawledJobData();
data.setTitle(title);
data.setCompanyName(company);
data.setLocation(location);
data.setSalary(salary);
data.setExperience(experience);
data.setEducation(education);
data.setEmploymentType(employmentType);
data.setDescription(description);
data.setRequirements(requirements);
data.setPreferred(preferred);
data.setTechStack(techStack);
data.setDeadline(deadline);
data.setSourceSite(sourceSite);
data.setSourceUrl(sourceUrl);
// ... 20개 넘는 setter 호출
```

AGENTS.md에 명시된 원칙들을 전부 위반하고 있었다:

- **Tell Don't Ask 위반**: 객체에게 "이걸 해줘"가 아니라 "너의 필드를 내가 직접 세팅할게"
- **SRP 위반**: 하나의 클래스가 데이터 보관, 변환 로직, AI 프롬프트 생성까지 전부 담당
- **@Setter 남발**: 어디서든 아무 필드나 바꿀 수 있어서 객체의 상태를 예측할 수 없음

### 고민: 어디까지 분리할 것인가?

리팩토링의 가장 어려운 부분은 **경계 설정**이다.

**선택지 1: Builder 패턴으로 전환**
`CrawledJobData.builder().title(title).company(company)...build()` 형태. Immutable하게 만들 수 있지만, 크롤링 특성상 데이터가 한 번에 다 모이지 않는다. 기본 정보는 목록 페이지에서, 상세 정보는 상세 페이지에서, 분류 정보는 후처리에서 채워진다. Builder로는 이런 **단계적 조립**이 어색하다.

**선택지 2: 도메인 이벤트 기반 분리**
각 파싱 단계를 이벤트로 모델링하는 방법. 과도한 추상화다. 크롤링 데이터 조립은 이벤트로 분리할 만큼 복잡한 비즈니스 로직이 아니다.

**선택지 3: enrich 메서드 패턴**
`@Setter`를 제거하고, 의미 단위로 묶인 `enrichXxx()` 메서드를 제공한다. "기본 정보를 채워줘", "상세 정보를 채워줘"라는 **의도가 드러나는 인터페이스**를 만든다.

### 시도

선택지 3을 선택했다. 먼저 setter 호출 패턴을 분석했다.

파서들의 setter 호출을 나열해보니 4가지 그룹으로 나뉘었다:

1. **기본 정보**: title, companyName, location, sourceUrl, sourceSite
2. **상세 내용**: description, requirements, preferred
3. **조건**: salary, experience, education, employmentType, deadline
4. **분류**: techStack, jobCategory

이 그룹이 곧 `enrich` 메서드가 된다.

```java
// Before: 20개 setter를 아무 순서로 호출
data.setTitle("백엔드 개발자");
data.setCompanyName("카카오");
data.setLocation("판교");
data.setSourceUrl("https://...");
data.setSalary("5000만원");
data.setExperience("3년");
// ... (20줄 이상)

// After: 의미 단위 4개 메서드
data.enrichBasicInfo("백엔드 개발자", "카카오", "판교", "https://...", SourceSite.SARAMIN);
data.enrichJobDetail(description, requirements, preferred);
data.enrichConditions("5000만원", "3년", "대졸", "정규직", deadline);
data.enrichClassification(techStackSet, "IT/개발");
```

### 변환 로직 분리: CrawledJobDataConverter

`CrawledJobData` 안에 `toJobPosting()` 메서드가 있었다. DTO가 엔티티 변환까지 책임지고 있었다.

```java
// Before: CrawledJobData 안에 있던 변환 로직
public class CrawledJobData {
    // ... 20개 필드 ...

    public JobPosting toJobPosting() {
        JobPosting posting = new JobPosting();
        posting.setTitle(this.title);
        posting.setCompanyName(this.companyName);
        // ... 15줄 이상의 변환 코드
        if (this.techStack != null && !this.techStack.isEmpty()) {
            posting.setTechStack(String.join(",", this.techStack));
        }
        return posting;
    }
}
```

이걸 `CrawledJobDataConverter`로 분리했다:

```java
public class CrawledJobDataConverter {

    public static JobPosting toJobPosting(CrawledJobData data) {
        return JobPosting.builder()
                .title(data.getTitle())
                .companyName(data.getCompanyName())
                .location(data.getLocation())
                .salary(data.getSalary())
                .experience(data.getExperience())
                .education(data.getEducation())
                .employmentType(data.getEmploymentType())
                .description(data.getDescription())
                .requirements(data.getRequirements())
                .preferred(data.getPreferred())
                .techStack(joinTechStack(data.getTechStack()))
                .deadline(data.getDeadline())
                .sourceUrl(data.getSourceUrl())
                .sourceSite(data.getSourceSite())
                .build();
    }

    private static String joinTechStack(Set<String> techStack) {
        if (techStack == null || techStack.isEmpty()) return null;
        return String.join(",", techStack);
    }
}
```

### AI 프롬프트 생성 분리: AiPromptDataBuilder

`CrawledJobData`에 AI 프롬프트용 데이터를 조합하는 로직도 들어있었다.

```java
// Before: CrawledJobData 안에 있던 프롬프트 관련 로직
public String toPromptSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("제목: ").append(title).append("\n");
    sb.append("회사: ").append(companyName).append("\n");
    if (requirements != null) sb.append("자격요건: ").append(requirements).append("\n");
    if (preferred != null) sb.append("우대사항: ").append(preferred).append("\n");
    // ... 10줄 이상
    return sb.toString();
}
```

이 역시 `AiPromptDataBuilder`로 분리:

```java
public class AiPromptDataBuilder {

    public static String buildJobSummary(CrawledJobData data) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "제목", data.getTitle());
        appendIfPresent(sb, "회사", data.getCompanyName());
        appendIfPresent(sb, "자격요건", data.getRequirements());
        appendIfPresent(sb, "우대사항", data.getPreferred());
        appendIfPresent(sb, "기술스택", joinTechStack(data.getTechStack()));
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }
}
```

### 결과

| 항목 | Before | After |
|------|--------|-------|
| CrawledJobData 줄 수 | 412줄 | ~250줄 |
| @Setter | 클래스 레벨 (모든 필드 노출) | 제거 |
| 데이터 조립 방식 | setter 20개+ 개별 호출 | enrich 4개 메서드 |
| 변환 로직 위치 | CrawledJobData 내부 | CrawledJobDataConverter |
| AI 프롬프트 생성 | CrawledJobData 내부 | AiPromptDataBuilder |
| 클래스 수 | 1개 | 3개 (역할별 분리) |

### 교훈

리팩토링에서 가장 중요한 건 **"왜 분리하는가"에 대한 기준**이다. 나의 기준은:

1. **변경 이유가 다른가?** — 파싱 로직이 바뀔 때와 엔티티 구조가 바뀔 때는 변환 로직이 다르게 영향받는다. → 분리
2. **테스트하기 어려운가?** — 변환 로직을 테스트하려면 CrawledJobData의 모든 필드를 알아야 했다. → 분리
3. **읽기 어려운가?** — 412줄에서 "이 메서드는 뭘 하는 거지?"를 찾는 데 시간이 걸렸다. → 분리

하지만 **과도한 분리도 독**이다. enrich 메서드를 20개로 쪼개거나, 각 필드마다 Value Object를 만드는 건 크롤링 DTO에는 오버엔지니어링이다.

---

## 2. "5년 이상, JIRA, react" — 경력과 기술스택이 뒤섞인 콤마 지옥

### 문제

잡플래닛에서 크롤링한 데이터에 이런 값이 들어왔다:

```
techStack: "5년 이상, JIRA, react, git, 합격보상 100만원"
```

경력 정보("5년 이상"), 기술스택("JIRA, react, git"), 그리고 채용 프로모션 문구("합격보상 100만원")가 하나의 콤마 구분 텍스트로 뒤섞여 있었다.

원인은 잡플래닛의 공고 카드 UI 구조다. 태그 형태로 `경력 | 기술스택 | 프로모션`이 한 줄에 나열되는데, 크롤러가 이걸 통째로 가져와서 콤마로 join하고 있었다.

### 고민

**방법 1: 정규식으로 분리**
`\d+년` 패턴으로 경력을 추출하고 나머지를 기술스택으로 분류. 간단하지만, "3~5년", "경력무관", "신입/경력" 같은 변형이 있다.

**방법 2: 키워드 기반 필터**
기술이 아닌 것들의 목록(NON_TECH_KEYWORDS)을 만들어서 필터링. 새로운 비기술 태그가 추가되면 목록에 추가해야 하지만, 기술스택의 종류가 비기술 태그보다 훨씬 많으므로 **비기술 목록이 더 관리하기 쉽다**.

**방법 3: 별도 필드로 분리**
크롤링 단계에서 DOM 셀렉터를 다르게 해서 경력/기술/프로모션을 분리. 이상적이지만 잡플래닛 HTML 구조상 같은 컨테이너에 들어있어서 셀렉터 분리가 어렵다.

### 시도

방법 1 + 방법 2를 조합했다.

```java
// 1단계: 콤마로 분리
String[] tokens = rawText.split(",");

// 2단계: 경력 패턴 매칭 → experience 필드로 이동
private static final Pattern EXPERIENCE_PATTERN =
    Pattern.compile("\\d+년|경력\\s*무관|신입|경력");

for (String token : tokens) {
    String trimmed = token.trim();
    if (EXPERIENCE_PATTERN.matcher(trimmed).find()) {
        experience = trimmed;  // experience 필드에 저장
        continue;
    }
    if (!isNonTech(trimmed)) {
        techStack.add(trimmed);  // 기술스택으로 분류
    }
}
```

```java
// 3단계: 비기술 키워드 필터링
private static final Set<String> NON_TECH_KEYWORDS = Set.of(
    "합격보상", "취업축하금", "인턴", "계약직", "정규직",
    "상시채용", "급구", "즉시입사", "재택근무", "하이브리드"
);

private static boolean isNonTech(String token) {
    return NON_TECH_KEYWORDS.stream()
            .anyMatch(keyword -> token.contains(keyword));
}
```

### 해결

Stream 기반으로 깔끔하게 정리:

```java
Set<String> techStack = Arrays.stream(rawText.split(","))
        .map(String::trim)
        .filter(t -> !t.isBlank())
        .filter(t -> !EXPERIENCE_PATTERN.matcher(t).find())  // 경력 제외
        .filter(t -> !isNonTech(t))                           // 비기술 제외
        .collect(Collectors.toCollection(LinkedHashSet::new));
```

`LinkedHashSet`을 사용한 이유: 중복 제거 + 순서 보존. 잡플래닛에서 "react, react" 같은 중복이 간혹 들어온다.

### Before / After

```
Before:
  techStack: "5년 이상, JIRA, react, git, 합격보상 100만원"
  experience: null

After:
  techStack: ["JIRA", "react", "git"]
  experience: "5년 이상"
```

### 교훈

크롤링 데이터는 **구조화되지 않은 텍스트**인 경우가 많다. 완벽한 파싱을 목표로 하기보다, **"잘못 분류되면 어떤 영향이 있는가?"**를 기준으로 허용 범위를 정하는 게 현실적이다. 기술스택에 "5년 이상"이 들어가면 AI 적합률 분석에 노이즈가 되지만, 기술스택에서 하나가 빠지는 건 상대적으로 덜 치명적이다. 그래서 **"비기술을 제거"하는 방향**으로 필터를 설계했다.

---

## 3. requirements에 페이지 전체가 들어간다고요?

### 문제

사람인에서 크롤링한 공고의 `requirements` 필드에 이런 값이 들어있었다:

```
채용 요약경력: 경력 3년 이상학력: 대졸 이상(4년)연봉: 3,600만원 이상
우대사항: AWS, Docker근무지: 서울 강남구복리후생: 4대보험, 연차,
인센티브모집기간: 2026.03.01 ~ 2026.03.31채용절차: 서류전형 > 1차 면접 >
2차 면접 > 최종합격... (수백 줄 계속)
```

`requirements`에 채용 요약부터 페이지 끝까지 전체 텍스트가 들어가고 있었다. 자격요건이 아니라 **페이지 전체 덤프**였다.

### 원인

`extractRequirementsFromDescription()` 메서드가 description 텍스트에서 "자격" 키워드를 찾아 그 이후를 전부 requirements로 넣고 있었다.

```java
// 문제의 코드
private String extractRequirementsFromDescription(String description) {
    String[] lines = description.split("\n");
    boolean found = false;
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
        if (line.contains("자격")) {
            found = true;
        }
        if (found) {
            sb.append(line).append("\n");  // "자격" 이후 전부 추가
        }
    }
    return sb.toString();
}
```

문제점 두 가지:

1. **종료 조건이 없다** — "자격" 키워드가 나오면 그 뒤 **모든 텍스트**를 가져온다
2. **description이 이미 HTML로 자격요건을 포함하고 있다** — innerHTML 전환 이후 description에 이미 `<h3>자격요건</h3><ul>...` 형태로 구조화된 자격요건이 들어있다

### 고민

**시도 1: 종료 키워드 추가**
"채용절차", "복리후생", "근무지" 같은 키워드가 나오면 멈추도록 수정.

```java
if (line.contains("채용절차") || line.contains("복리후생")) {
    break;
}
```

→ 불완전. 공고마다 섹션 순서가 다르다. 어떤 공고는 "채용절차"가 자격요건 안에 있고, 어떤 공고는 아예 없다.

**시도 2: 길이 제한**
500자 이상이면 자르기.

→ 근본적 해결이 아니다. 500자까지는 여전히 쓰레기 데이터.

**시도 3: 메서드 자체를 삭제**

innerHTML 전환 이후의 상황을 다시 생각해봤다:

- `description`: HTML로 전체 공고 내용이 들어있음. `<h3>자격요건</h3>` 섹션이 이미 포함됨.
- 사람인 채용 요약 DL: `<dt>우대사항</dt><dd>AWS, Docker</dd>` 형태로 별도 존재.

**description이 이미 자격요건을 포함하고 있으므로, 별도로 추출할 필요가 없다.**

### 해결

`extractRequirementsFromDescription()` 메서드를 삭제했다.

```java
// Before: description에서 자격요건을 이중으로 추출
String requirements = extractRequirementsFromDescription(description);
data.enrichJobDetail(description, requirements, preferred);

// After: 채용 요약의 우대사항만 유지
String preferred = extractFromSummaryDl("우대사항");
data.enrichJobDetail(description, null, preferred);
```

requirements 필드는 채용 요약 DL(`<dl>` 태그)에서 추출한 간단한 요약만 사용하고, 상세한 자격요건은 description의 HTML 섹션에서 사용자가 직접 확인하도록 했다.

### 교훈

**innerHTML 전환은 생각보다 많은 것을 바꾼다.** innerText 시대에 필요했던 "텍스트에서 구조를 복원하는" 코드들이 innerHTML 전환 후에는 불필요해지거나, 오히려 해가 된다. 대규모 변경 후에는 **기존 파싱 로직이 여전히 유효한지** 전수 점검이 필요하다.

---

## 4. 같은 공고를 10명이 분석하면 API 호출도 10번?

### 문제

AI 기업 분석 기능을 사용하면, 유저가 공고를 보고 "AI 분석" 버튼을 누르면 OpenAI API를 호출해서 기업 분석 결과를 생성한다. 이 결과가 **유저별로 저장**되고 있었다.

```java
// 기존: userId + jobPostingId + type으로 저장
AiAnalysis analysis = AiAnalysis.builder()
        .userId(userId)
        .jobPostingId(jobPostingId)
        .type(AnalysisType.COMPANY)
        .content(aiResult)
        .build();
aiAnalysisRepository.save(analysis);
```

문제: 같은 공고에 대한 기업 분석은 **유저와 무관하게 동일한 결과**다. 회사의 재무제표, 기업문화, 뉴스는 누가 물어봐도 같다. 10명이 같은 공고의 기업 분석을 요청하면 OpenAI API를 10번 호출하게 된다.

반면 **적합률 분석**은 유저의 이력서/기술스택과 공고를 비교하므로 유저별로 결과가 달라야 한다.

### 고민

**방법 1: 캐시 레이어 추가**
Redis에 결과를 캐시하고 TTL을 설정. 하지만 기업 분석 결과는 변하지 않으므로 TTL 기반 만료가 적합하지 않다. DB에 이미 저장하는 로직이 있는데 캐시를 또 추가하면 복잡도만 높아진다.

**방법 2: 공유 저장 (userId=0)**
기업 분석은 `userId=0`으로 저장해서 전체 유저가 공유. 적합률은 기존대로 유저별 저장.

방법 2가 기존 테이블 구조를 변경하지 않으면서 깔끔하게 해결된다.

### 해결

```java
// 기업 분석: 공유 (userId=0)
public AiAnalysisResponse analyzeCompany(Long jobPostingId) {
    // 이미 분석된 결과가 있으면 재사용
    Optional<AiAnalysis> existing = aiAnalysisRepository
            .findByJobPostingIdAndType(jobPostingId, AnalysisType.COMPANY);

    if (existing.isPresent()) {
        return AiAnalysisResponse.from(existing.get());  // API 호출 없이 반환
    }

    // 없으면 API 호출 후 userId=0으로 저장
    String aiResult = openAiService.analyzeCompany(jobPosting);
    AiAnalysis analysis = AiAnalysis.builder()
            .userId(0L)  // 공유 분석
            .jobPostingId(jobPostingId)
            .type(AnalysisType.COMPANY)
            .content(aiResult)
            .build();
    aiAnalysisRepository.save(analysis);
    return AiAnalysisResponse.from(analysis);
}

// 적합률 분석: 유저별 (기존 유지)
public AiAnalysisResponse analyzeFit(Long userId, Long jobPostingId) {
    Optional<AiAnalysis> existing = aiAnalysisRepository
            .findByUserIdAndJobPostingIdAndType(userId, jobPostingId, AnalysisType.FIT);

    if (existing.isPresent()) {
        return AiAnalysisResponse.from(existing.get());
    }

    // 유저의 이력서 + 공고 비교 → 유저별로 다른 결과
    String aiResult = openAiService.analyzeFit(user, jobPosting);
    AiAnalysis analysis = AiAnalysis.builder()
            .userId(userId)
            .jobPostingId(jobPostingId)
            .type(AnalysisType.FIT)
            .content(aiResult)
            .build();
    aiAnalysisRepository.save(analysis);
    return AiAnalysisResponse.from(analysis);
}
```

레포지토리에 조회 메서드 추가:

```java
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // 공유 분석 조회 (기업 분석)
    Optional<AiAnalysis> findByJobPostingIdAndType(
            Long jobPostingId, AnalysisType type);

    // 유저별 분석 조회 (적합률)
    Optional<AiAnalysis> findByUserIdAndJobPostingIdAndType(
            Long userId, Long jobPostingId, AnalysisType type);
}
```

### 결과

| 시나리오 | Before | After |
|---------|--------|-------|
| 10명이 같은 공고 기업 분석 | API 10회 호출 | API 1회 호출 + 9회 DB 조회 |
| API 비용 (gpt-4 기준) | $0.30~$0.50 | $0.03~$0.05 |
| 응답 속도 (2번째부터) | 3~5초 (API 호출) | <100ms (DB 조회) |

### 교훈

**"이 데이터는 누구의 것인가?"**를 명확히 구분해야 한다.

- 기업 분석 = **공고의 속성** → 공유 가능
- 적합률 분석 = **유저와 공고의 관계** → 유저별 고유

이 구분을 설계 시점에 했다면 처음부터 이렇게 만들었을 것이다. "일단 userId 넣어두자"는 관성적 설계가 불필요한 API 호출을 만들었다.

---

## 5. 마감된 공고가 영원히 살아있는 문제

### 문제

두 가지 문제가 동시에 있었다.

**문제 1: deadline=NULL인 공고가 영원히 열려있음**
"상시채용"이나 마감일이 명시되지 않은 공고의 deadline이 NULL로 저장된다. 현재 로직은 `deadline != null && deadline < now` 일 때만 마감 처리하므로, **deadline이 NULL이면 영원히 "채용중"**으로 표시된다. 실제로는 3개월 전에 마감된 공고가 여전히 목록에 나타나고 있었다.

**문제 2: 원본 사이트에서 삭제된 공고를 감지할 수 없음**
채용이 완료되어 원본 사이트에서 공고가 삭제/비활성화되어도, 우리 DB에는 그대로 남아있다. 유저가 해당 공고를 클릭하면 404 페이지로 이동하게 된다.

### 고민: 어떻게 "마감되었는지" 확인할 것인가?

**방법 1: 주기적 Playwright 재크롤링**
모든 공고의 원본 URL에 Playwright로 접속해서 페이지 상태 확인. 정확하지만 **비용이 엄청나다**. DB에 공고가 5,000개 있으면 5,000개 페이지를 Playwright로 열어야 한다. 서버 1대에서 감당 불가.

**방법 2: HTTP HEAD 요청**
`HttpClient`로 HEAD 요청만 보내서 응답 코드 확인. 빠르지만, 많은 채용 사이트가 공고 삭제 시 404 대신 **메인 페이지로 리다이렉트**(302 → 200)한다. HEAD 요청으로는 이걸 잡을 수 없다.

**방법 3: HTTP GET + 키워드 검출**
GET 요청으로 응답 본문까지 확인. 리다이렉트 감지 + 응답 본문에서 "마감", "종료" 같은 키워드 검색. HEAD보다 느리지만 Playwright보다는 100배 빠르다.

### 시도: HEAD 요청만으로

처음에 HEAD 요청만으로 시도했다.

```java
// 1차 시도: HEAD만
HttpResponse<Void> response = httpClient.send(
    HttpRequest.newBuilder(URI.create(url)).method("HEAD", noBody()).build(),
    HttpResponse.BodyHandlers.discarding()
);
if (response.statusCode() == 404 || response.statusCode() == 410) {
    return true; // 마감됨
}
```

테스트 결과:
- 사람인: 삭제된 공고 → 302 → 메인 페이지 (200) → **감지 실패**
- 잡플래닛: 삭제된 공고 → 200 + "마감된 공고입니다" 텍스트 → **감지 실패** (본문 안 봄)
- 잡코리아: 삭제된 공고 → 404 → **감지 성공**

HEAD 요청만으로는 **4개 사이트 중 1개만** 감지할 수 있었다.

### 해결: PostingUrlValidator

GET 요청 + 다중 감지 전략으로 최종 구현:

```java
@Component
public class PostingUrlValidator {

    private static final Set<String> CLOSED_KEYWORDS = Set.of(
        "마감", "종료", "삭제", "만료", "채용이 완료",
        "존재하지 않", "페이지를 찾을 수 없"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)  // 리다이렉트 수동 추적
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * URL이 여전히 유효한 공고인지 검증
     * @return true면 마감/삭제됨, false면 아직 유효
     */
    public boolean isPostingClosed(String url) {
        try {
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 ...")
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            int status = response.statusCode();

            // 1. 명확한 에러 코드
            if (status == 404 || status == 410 || status == 403) {
                return true;
            }

            // 2. 리다이렉트 → 메인 페이지로 가는지 확인
            if (status == 301 || status == 302) {
                String location = response.headers()
                        .firstValue("Location").orElse("");
                if (isMainPageRedirect(url, location)) {
                    return true;
                }
            }

            // 3. 200이지만 본문에 마감 키워드가 있는 경우
            if (status == 200) {
                String body = response.body();
                return CLOSED_KEYWORDS.stream()
                        .anyMatch(keyword -> body.contains(keyword));
            }

            return false;
        } catch (Exception e) {
            log.warn("URL 검증 실패: {} - {}", url, e.getMessage());
            return false;  // 네트워크 에러는 마감으로 판단하지 않음
        }
    }

    private boolean isMainPageRedirect(String originalUrl, String redirectUrl) {
        try {
            URI original = URI.create(originalUrl);
            URI redirect = URI.create(redirectUrl);
            // 같은 도메인이면서 경로가 "/" 또는 "/main"이면 메인 리다이렉트
            return original.getHost().equals(redirect.getHost())
                    && (redirect.getPath().equals("/")
                        || redirect.getPath().startsWith("/main"));
        } catch (Exception e) {
            return false;
        }
    }
}
```

스케줄러와 관리자 API:

```java
@Component
public class PostingExpirationScheduler {

    private final JobPostingRepository jobPostingRepository;
    private final PostingUrlValidator urlValidator;

    // 매일 새벽 3시 실행
    @Scheduled(cron = "0 0 3 * * *")
    public void validateExpiredPostings() {
        // deadline NULL + 7일 이상 지난 공고만 대상
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<JobPosting> candidates = jobPostingRepository
                .findByDeadlineIsNullAndCreatedAtBeforeAndStatusNot(
                    threshold, PostingStatus.CLOSED);

        int closedCount = 0;
        for (JobPosting posting : candidates) {
            if (urlValidator.isPostingClosed(posting.getSourceUrl())) {
                posting.close();  // status = CLOSED
                closedCount++;
            }
            // 사이트 부담 줄이기 위해 딜레이
            Thread.sleep(1000);
        }

        log.info("[마감 검증] 대상: {}건, 마감 처리: {}건",
                 candidates.size(), closedCount);
    }
}
```

```java
// 관리자 수동 실행 API
@PostMapping("/api/v1/admin/validate-postings")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, Integer>> validatePostings() {
    int closedCount = postingExpirationScheduler.validateExpiredPostings();
    return ResponseEntity.ok(Map.of("closedCount", closedCount));
}
```

### 감지 결과

| 사이트 | 삭제된 공고 응답 | 감지 방법 |
|--------|---------------|----------|
| 사람인 | 302 → 메인 페이지 | 리다이렉트 감지 |
| 잡플래닛 | 200 + "마감된 공고입니다" | 키워드 검출 |
| 잡코리아 | 404 | 상태 코드 |
| 링커리어 | 200 + "채용이 완료되었습니다" | 키워드 검출 |

### 교훈

웹 크롤링에서 **"데이터가 없다"는 정보도 중요한 데이터**다. 수집할 때만 신경 쓰고 데이터의 라이프사이클(생성 → 유효 → 만료 → 삭제)을 관리하지 않으면, 오래된 무효 데이터가 쌓여서 서비스 신뢰도가 떨어진다.

또한 **사이트마다 "삭제"를 표현하는 방식이 다르다**는 것을 배웠다. 404, 리다이렉트, 200+안내문구 — 세 가지 패턴을 모두 커버해야 했다.

---

## 6. 세션 4 미문서화: AI 적합률 프롬프트 60% 가중치 개선

### 문제

AI 적합률 분석이 "기술스택이 50% 일치하는데 적합률 85%"처럼 비직관적인 결과를 냈다.

### 원인

기존 프롬프트가 기술스택 매칭에 충분한 가중치를 두지 않았다.

```
기존 프롬프트:
"지원자의 이력서와 채용 공고를 비교하여 적합률을 0~100%로 평가해주세요."
```

이렇게 모호하게 지시하면 GPT가 자체적으로 가중치를 정하는데, 경력 연수나 학력 같은 "쉽게 비교 가능한" 항목에 가중치를 많이 두고, 기술스택 매칭은 상대적으로 낮게 평가했다.

### 해결

프롬프트에 명시적 가중치를 부여:

```
수정된 프롬프트:
"적합률 평가 기준:
- 기술스택 일치도: 60% 가중치 (필수 기술 미충족 시 최대 50%)
- 경력 수준 적합도: 20% 가중치
- 자격요건 충족도: 15% 가중치
- 우대사항 해당 여부: 5% 가중치

기술스택은 단순 키워드 매칭이 아니라 유사 기술도 고려하세요.
예: React를 요구하는데 Vue.js 경험이 있으면 부분 인정 (프론트엔드 프레임워크)
예: AWS를 요구하는데 GCP 경험이 있으면 부분 인정 (클라우드 플랫폼)"
```

### 결과

```
Before: 기술스택 50% 일치 → 적합률 85% (왜?)
After:  기술스택 50% 일치 → 적합률 52% (기술 가중치 60% 반영)
```

기술스택이 핵심인 개발자 채용에서, 기술 매칭에 60% 가중치를 주니 결과가 훨씬 직관적이 되었다.

---

## 7. 세션 4 미문서화: OCR 이미지 텍스트 추출

### 문제

일부 채용 공고가 자격요건이나 업무 내용을 **이미지로** 올린다. 특히 디자인 직군이나 소규모 기업 공고에서 이런 경우가 잦다. 크롤러가 `<img>` 태그만 가져오면 AI 분석에 텍스트 정보가 전달되지 않는다.

### 고민

**방법 1: 클라우드 OCR (Google Vision, AWS Textract)**
정확도 높지만 API 호출 비용 발생. 이미지 한 장당 $0.0015~$0.005.

**방법 2: Tesseract OCR (오픈소스, 로컬 실행)**
무료. 한글 인식 가능 (traineddata 필요). 정확도는 클라우드보다 낮지만, 채용 공고 이미지는 대부분 **깔끔한 폰트의 텍스트**라서 충분하다.

서버 1대 운영이고 비용을 최소화해야 하므로 Tesseract를 선택.

### 해결

```java
// Tesseract OCR 서비스
public class OcrTextExtractor {

    private final Tesseract tesseract;

    public OcrTextExtractor() {
        this.tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
        tesseract.setLanguage("kor+eng");  // 한국어 + 영어
        tesseract.setPageSegMode(6);        // 단일 텍스트 블록
    }

    public String extractText(String imageUrl) {
        try {
            BufferedImage image = ImageIO.read(new URL(imageUrl));
            if (image == null) return "";

            // 50px 미만 이미지는 스킵 (아이콘/placeholder)
            if (image.getWidth() < 50 || image.getHeight() < 50) return "";

            return tesseract.doOCR(image).trim();
        } catch (Exception e) {
            log.debug("OCR 실패: {} - {}", imageUrl, e.getMessage());
            return "";
        }
    }
}
```

크롤러에서 description 내 이미지를 발견하면 OCR 추출 텍스트를 description에 추가:

```java
// 이미지가 있는 경우 OCR 텍스트 추가
Elements images = Jsoup.parse(descriptionHtml).select("img[src]");
for (Element img : images) {
    String ocrText = ocrExtractor.extractText(img.attr("src"));
    if (!ocrText.isBlank()) {
        descBuilder.append("\n<!-- OCR 추출 -->\n").append(ocrText);
    }
}
```

### 결과

이미지 기반 공고에서도 AI 분석이 가능해졌다. Tesseract의 한글 인식 정확도는 약 85~90% 수준인데, AI 프롬프트에 "OCR로 추출된 텍스트이므로 오타가 있을 수 있음"을 명시하여 GPT가 문맥으로 보정하도록 했다.

---

## 8. 세션 4 미문서화: 4개 사이트 각각 다른 마감일 파싱

### 문제

마감일 표시 형식이 사이트마다 전부 다르다:

```
사람인:    "~03/31(월)"
잡코리아:  "2026.03.31"
잡플래닛:  "2026-03-31T23:59:59"
링커리어:  "D-7" 또는 "상시"
```

하나의 `parseDeadline()` 메서드로 처리하려니 예외 케이스가 끊임없이 늘어났다.

### 고민

**방법 1: 공통 파서 + 예외 분기**
하나의 메서드에서 정규식으로 여러 형식을 시도. 유지보수 지옥.

**방법 2: 사이트별 파서**
각 파서 클래스 내부에서 자기 사이트의 마감일 형식만 처리. SRP에 부합.

### 해결

각 사이트별 파서에 deadline 파싱 로직을 내장:

```java
// SaraminParser
private LocalDateTime parseDeadline(String text) {
    // "~03/31(월)" → 올해 03/31
    Matcher m = Pattern.compile("~?(\\d{2})/(\\d{2})").matcher(text);
    if (m.find()) {
        int month = Integer.parseInt(m.group(1));
        int day = Integer.parseInt(m.group(2));
        LocalDate date = LocalDate.of(LocalDate.now().getYear(), month, day);
        // 이미 지난 날짜면 내년
        if (date.isBefore(LocalDate.now())) {
            date = date.plusYears(1);
        }
        return date.atTime(23, 59, 59);
    }
    if (text.contains("상시")) return null;
    return null;
}

// JobKoreaParser
private LocalDateTime parseDeadline(String text) {
    // "2026.03.31"
    try {
        return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                .atTime(23, 59, 59);
    } catch (DateTimeParseException e) {
        return null;
    }
}

// JobPlanetParser
private LocalDateTime parseDeadline(String text) {
    // "2026-03-31T23:59:59"
    try {
        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (DateTimeParseException e) {
        return null;
    }
}

// LinkareerParser
private LocalDateTime parseDeadline(String text) {
    // "D-7" → 오늘 + 7일
    Matcher m = Pattern.compile("D-(\\d+)").matcher(text);
    if (m.find()) {
        int days = Integer.parseInt(m.group(1));
        return LocalDate.now().plusDays(days).atTime(23, 59, 59);
    }
    if (text.contains("상시") || text.contains("채용시")) return null;
    return null;
}
```

### 교훈

**"공통화"가 항상 좋은 것은 아니다.** 4개 사이트의 마감일 형식은 공통점보다 차이점이 많다. 억지로 공통 메서드를 만들면 `if-else` 분기가 4개 생기고, 새 사이트를 추가할 때마다 기존 분기에 영향을 준다. **각자의 맥락에서 각자 처리하는 게 더 깔끔하다.**

특히 사람인의 "~03/31(월)" 형태는 연도가 없어서 "이미 지난 날짜면 내년으로 판단"하는 로직이 필요한데, 이런 사이트 고유의 비즈니스 로직이 공통 파서에 들어가면 다른 파서를 읽는 사람이 혼란스러워진다.

---

## 9. 적합률 근거 팝업 — "왜 32%인데?"에 답하기

### 문제

AI 적합률이 숫자만 나오니까 유저 입장에서 "왜 이 점수야?"라는 의문이 생겼다. 32%가 나오면 기술이 안 맞는 건지, 경력이 부족한 건지 알 수 없었다.

### 고민

AI가 이미 JSON으로 `totalScore`, `matched`, `missing`, `summary`를 반환하고 있었는데, 기존 코드에서 숫자만 추출하고 나머지를 **버리고 있었다**. 새 API를 만들 필요 없이 기존 응답을 활용하면 됐다.

### 해결

```java
// 기존: 숫자만 추출
int score = node.get("totalScore").asInt();

// 변경: JSON 전체 반환 + DB 저장
Map<String, Object> result = aiTextGenerator.calculateMatchScoreWithReason(profile, jobString);
String reasonJson = objectMapper.writeValueAsString(result);
saveOrUpdateAnalysis(userId, jobPostingId, AnalysisType.MATCH_SCORE, reasonJson, score);
```

프론트엔드에서는 적합률 영역 클릭 시 팝업이 뜨면서:
- **매칭된 기술**: 초록색 Badge
- **부족한 기술**: 빨간색 Badge
- **요약**: 한 줄 설명

### 교훈

**이미 있는 데이터를 버리지 마라.** AI 응답에 유용한 정보가 있었는데 파싱 과정에서 숫자만 남기고 버렸다. 데이터 파이프라인에서 "이 데이터가 나중에 쓸모있을까?"를 한 번 더 생각하자.

---

## 10. 공고 삭제 시 고아 데이터 — 연관 데이터 동기 삭제

### 문제

관리자가 "전체 삭제"를 누르면 `job_postings`만 삭제되고, `ai_analysis_results`, `notification_history`는 남아서 고아 데이터가 쌓였다. FK 제약 없이 `Long jobPostingId`로만 참조하고 있었기 때문.

### 해결

모든 삭제 경로(단건/선택/전체/사이트별)에서 연관 데이터를 먼저 삭제하도록 수정:

```java
public void deleteJob(Long id) {
    aiAnalysisResultRepository.deleteByJobPostingId(id);      // AI 분석 결과
    notificationHistoryRepository.deleteByJobPostingId(id);   // 알림 이력
    jobApplicationRepository.deleteByJobPostingId(id);        // 지원 이력
    jobPostingRepository.deleteById(id);                      // 공고 본체
}
```

### 교훈

FK가 없는 소프트 참조는 편리하지만, **삭제 시 연쇄 정리를 개발자가 직접 보장**해야 한다. 엔티티 간 참조 관계를 문서화하지 않으면 이런 누락이 반복된다.

---

## 11. 필터 패널 정리 — 전체 탭 vs 사이트별 탭

### 문제

전체 탭에서 사람인 직무, 잡플래닛 직무, 링커리어 직무가 전부 보였다. 4개 사이트 직무 카테고리가 다 다른데 전체 탭에서 보여봤자 의미 없고 혼란만 줬다.

### 해결

- 전체 탭: 경력/학력/지역/정렬만 표시
- 사이트 탭: 해당 사이트 전용 직무 + 경력/학력/지역/지원방식/정렬

잡코리아 직무 필터도 DB 실제 값과 안 맞아서 수정:
```javascript
// 기존: "백엔드", "프론트엔드" (부분 매칭에 의존)
// 수정: "백엔드개발자", "프론트엔드개발자" (DB 정확한 값)
```

---

## 마무리: 세션 5에서 배운 것들

### 패턴 1: 리팩토링의 경계

CrawledJobData 리팩토링에서 가장 고민한 건 "어디까지 분리할 것인가"였다. @Setter 제거는 확실한 개선이지만, 각 필드를 Value Object로 만드는 건 과도하다. **"변경 이유가 다른 코드를 분리한다"**는 SRP의 원칙을 기계적으로 적용하지 않고, **실제 변경 패턴**을 보고 판단해야 한다.

### 패턴 2: 데이터 전환의 잔재

innerHTML 전환은 크롤링 품질을 크게 개선했지만, innerText 시대의 파싱 로직(`extractRequirementsFromDescription`)이 유물처럼 남아서 버그를 만들었다. 대규모 아키텍처 변경 후에는 **기존 코드가 새 맥락에서도 유효한지** 전수 점검이 필요하다.

### 패턴 3: "같은 데이터"의 기준

AI 기업 분석은 공고에 종속적인 데이터인데 유저별로 저장하고 있었다. **"이 데이터의 주인은 누구인가?"**를 처음부터 정의했다면 중복 API 호출은 없었을 것이다.

### 패턴 4: 크롤링 데이터의 라이프사이클

수집 → 저장 → **만료 → 정리**까지가 크롤링 시스템의 완전한 사이클이다. 수집에만 집중하고 만료 처리를 빼먹으면 무효한 데이터가 쌓여서 유저 경험이 나빠진다. `PostingUrlValidator`는 "데이터도 유통기한이 있다"는 인식의 결과물이다.

### 패턴 5: 사이트별 차이를 존중하라

마감일 형식, 경력/기술 혼합 방식, 삭제 공고 표현 방식 — 전부 사이트마다 다르다. 억지로 공통화하면 코드가 더 복잡해진다. **각 사이트의 맥락을 각 파서가 책임지는 구조**가 장기적으로 유지보수하기 좋다.
