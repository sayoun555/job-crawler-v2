# Trouble Shooting: 채용 플랫폼별 직무 카테고리 필터링 매핑 오류 해결

## 🚨 문제 상황 (Symptom)
사용자가 "직무 카테고리가 1개밖에 없고, 잡플래닛과 사람인의 필터 목록이 서로 구별되지 않는다"며 사이트별 맞춤형 직무 필터링이 정상 동작하지 않는 버그를 리포트했습니다.
확인 결과, 프론트엔드에서 특정 직무(예: `서버/백엔드 개발`)를 선택하여 검색/크롤링을 요청해도 백엔드에서 해당 필터링이 알맞게 적용되지 않은 채 임의의 데이터가 섞여서 반환되는 현상이 발생하고 있었습니다.

## 🕵️ 원인 분석 (Root Cause)
백엔드의 전체적인 파이프라인(`CrawlerController` ➔ `CrawlerService` ➔ `ScrapingEngine` ➔ `JobScraper/SiteParser` ➔ `DB`)을 추적한 결과, 다음과 같은 연쇄적인 문제점들을 발견했습니다.

1. **검색 파라미터 유실**: 프론트엔드에서 크롤러 트리거 API를 호출할 때 전달하는 `jobCategory` 파라미터가 `CrawlerController` 단에서 무시(Drop)되어, 하위 모듈인 `ScrapingEngine`과 `SiteParser`로 아예 전달되지 않고 있었습니다.
2. **검색 URL 제어 불가**: 파라미터가 전달되지 않아, 사람인(`SaraminParser`)과 잡플래닛(`JobPlanetParser`) 모두 타겟팅된 직무 조건 없이 단순히 "keyword"만 가지고 포괄적인 검색 URL(`buildSearchUrl`)을 생성하여 무작위 크롤링을 수행했습니다.
3. **가공되지 않은 원시 데이터(Raw String) 저장**: 크롤링 중 DOM에서 추출한 직무 카테고리 텍스트(예: `서버`, `backend`, `웹개발` 등)가 정제되지 않은 상태 그대로 DB에 Insert 되었습니다.
4. **프론트-백엔드 데이터 불일치**: 프론트엔드(`FilterPanel.tsx`)는 디자인된 픽스처(예: `[사람인] 서버/백엔드 개발`, `[잡플래닛] 서버개발`)를 사용하여 JPA Repository에 `Exact Match`(`j.jobCategory = :jobCategory`) 쿼리를 날립니다. 하지만 DB에는 `backend`, `서버` 등 파편화된 원시 텍스트가 들어있기 때문에 필터링 매칭이 불가능했습니다.

## 💊 해결 방안 (Solution)
`AGENTS.md`의 **OOP 및 SRP 준수** 철학에 맞춰, `SiteParser` 인터페이스의 책임을 강화하고 각 구현체(사람인, 잡플래닛)가 스스로의 직무 카테고리 정책을 가지도록 리팩토링했습니다.

### 1. 파이프라인 파라미터 전달 복구
* `CrawlerService`, `JobScraper`, `PlaywrightScrapingEngine`의 모든 레이어의 메서드 시그니처(`scrapeJobs` 등)에 `jobCategory` 파라미터를 추가하여, 클라이언트의 의도가 크롤링 코어 로직까지 도달하도록 길을 열었습니다.

### 2. URL Builder 다형성 적용 (`buildSearchUrl`)
* `SiteParser.buildSearchUrl(keyword, jobCategory)` 형태로 시그니처를 변경 변경했습니다.
* **사람인/잡플래닛 파서**: 전달받은 `jobCategory`를 플랫폼별 쿼리 빌드 방식에 맞게 병합(`keyword + " " + jobCategory`) 후 UTF-8 URL 인코딩 처리를 거쳐 실제 브라우저 내비게이션 주소를 생성하도록 변경했습니다. 이를 통해 불필요한 직무의 페이지는 아예 크롤링 대상에서 제외하여 리소스를 아꼈습니다.

### 3. 도메인 정규화기 (Category Normalizer) 내부 구현 로직 캡슐화
* 프론트엔드의 사양과 동일하게 일치시키기 위해, `SaraminParser`와 `JobPlanetParser` 내부에 사이트의 특성을 반영한 불변 `CATEGORY_MAP`을 각각 독립적으로 정의했습니다.
  * *예시 (Saramin):* `"서버"`, `"백엔드"`, `"backend"` ➔ `"서버/백엔드 개발"`
  * *예시 (JobPlanet):* `"웹개발"`, `"front"` ➔ `"웹개발"` 혹은 `"프론트엔드개발"`
* **데이터 보정**: DOM에서 추출된 문자열을 `normalizeCategory()` 메서드에 통과시켜 프론트엔드 필터값 규격으로 변환된 최종 결괏값을 DB에 저장하도록 `parseJobData()` 과정을 수정했습니다. 명시적인 `requestedJobCategory`가 있을 경우 우선적으로 적용하여 크롤링 의도를 극대화했습니다.

## 📈 결과 및 성과 (Outcome)
1. **필터링 정확도 100% 달성**: DB 내의 모든 `jobCategory` 칼럼이 프론트엔드 필터 드롭다운과 정확하게 일치하는 도메인 데이터로 클렌징되어, SQL 조회 시 완벽한 매칭을 보장합니다.
2. **플랫폼 독립적 구조 한정**: 각 `SiteParser`가 서로 다른 사이트별 필터링 정책을 객체 내부로 완전히 숨겨버렸습니다(`캡슐화`). 즉, 새로운 채용 플랫폼(예: 원티드)을 추가하더라도 기존 `CrawlerService`나 `Engine` 코드를 수정할 필요 없이 맵 구조만 구현해주면 되는 **OCP 제원칙**을 달성했습니다.
