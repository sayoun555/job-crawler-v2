# Scrapling vs Playwright 비교 정리

[D4Vinci/Scrapling](https://github.com/D4Vinci/Scrapling) 레포지토리를 분석하여 기존에 널리 사용되는 `Playwright` 와의 차이점 및 장단점을 비교한 문서입니다. `Scrapling`은 단순한 브라우저 자동화 도구를 넘어, 웹 스크래핑에 특화된 프레임워크입니다.

## 1. 개요
* **Playwright**: Microsoft에서 만든 **엔드투엔드(E2E) 웹 테스팅 및 브라우저 자동화 도구**입니다. 범용성이 가장 크지만 그레이 영역(안티 봇, 클라우드플레어 우회 등) 처리에는 플러그인이 추가적으로 필요합니다.
* **Scrapling**: Python 생태계에서 새롭게 등장한 **지능형 웹 스크래핑 자동화 프레임워크**입니다. 내부적으로 HTTP 요청(빠름)과 브라우저 제어(Playwright 기반)를 자유롭게 섞어 쓸 수 있도록 캡슐화되어 스크래핑에 최적화된 높은 편의성을 제공합니다.

---

## 2. 주요 비교 (어느 것이 더 좋은가?)

결론부터 말씀드리면, **"테스트 자동화나 UI 인터랙션"이 주 목적이라면 여전히 Playwright가 정답**이지만, **"대용량 병렬 구조의 웹 크롤링 및 안티봇 우회"가 목적이라면 Scrapling이 압도적으로 유리합니다.**

### ① 안티-봇(Anti-bot) 및 스텔스 기능
* **Playwright**: 탐지 우회를 위해 `playwright-stealth` 같은 서드파티 라이브러리를 직접 연동해야 하며, Cloudflare의 턴스타일(Turnstile)이나 다양한 캡차를 통과하기 위해 복잡한 로직을 직접 짜야 합니다.
* **Scrapling**: 스크래핑 전용 프레임워크답게 애초에 `StealthyFetcher`, `StealthySession` 클래스를 네이티브로 제공합니다. 브라우저 TLS 핑거프린트 위조 뿐만 아니라 Cloudflare의 방어막이나 Interstitial 레이어를 뚫는 로직이 내장되어 있습니다 (`solve_cloudflare=True`).

### ② 세션 및 프록시 제어 (Proxy Rotation)
* **Playwright**: 프록시를 설정할 수 있으나, 보통 컨텍스트(BrowserContext)나 브라우저 단위로 고정됩니다. 매 요청마다 프록시를 회전(순환)시키기 위해선 IP 체인저나 외부 프록시 서버에 로직을 위임해야 합니다.
* **Scrapling**: 기본적으로 사이클릭(장애 조치 포함) 프록시 회전을 지원하는 `ProxyRotator` 모듈이 들어 있습니다. 세션을 유지하면서도 요청마다 프록시를 동적으로 돌리는 것이 훨씬 수월합니다.

### ③ 성능 및 아키텍처 관점 (Playwright와의 병행)
* **Playwright**: 순수 브라우저 엔진(헤드리스 렌더링)이기 때문에 무조건 브라우저를 띄우므로, 파싱 속도 오버헤드와 메모리 점유가 큽니다.
* **Scrapling**: **가장 강력한 차별점입니다.**
    * API나 정적 페이지는 **`Fetcher`** (순수 HTTP 통신, 가장 빠름)로 처리하고,
    * JS 렌더링이 필수인 곳만 **`DynamicFetcher`** (내부적으로 Playwright 활용)로 라우팅하는 식의 **하이브리드 세션 관리**가 한 Spider 파일 안에서 가능해집니다.
    * Scrapy(Python 1위 스크래핑 도구)처럼 Concurrent Crawler(다중 비동기 수집)를 지원하면서도, Playwright를 흡수하여 제어합니다.

### ④ 요소 탐색 및 AI 결합 (Adaptive Parsing)
* **Playwright**: CSS Selector나 XPath, 텍스트 매칭에만 의존하며, 대상 사이트의 DOM이 조금만 변경되어도 크롤러가 망가집니다.
* **Scrapling**: "스마트 유사도 알고리즘"이 탑재되어 웹사이트의 일부분이 변경되더라도 유사한 엘리먼트를 동적으로 재탐색합니다. 더불어 MCP(Model Context Protocol) Server가 아예 내장되어 있어서, Cursor나 Claude 같은 AI와 화면/데이터 구조를 연동하는 것에 특화되어 있습니다.

---

## 3. 요약 및 시사점

**Playwright를 단독으로 쓰는 것보다 Scrapling을 활용하는 것이 더 좋습니다.** 
그 이유는 Scrapling이 Playwright와 경쟁하는 툴이 아니라, **Playwright를 백엔드로 감싸고(Wrapping) 그 위에 엄청난 양의 크롤링 편의 기능(우회, 프록시, 재시도 로직, 스파이더 프레임워크 등)을 얹은 상위 호환 패키지**이기 때문입니다.

만약 크롤링 봇 스택을 Python으로 가져가실 계획이 있다면, 기존처럼 `Scrapy + Playwright 확장형`을 직접 구축하는 수고를 덜어줄 훌륭한 대안입니다.
