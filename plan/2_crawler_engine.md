# Step 2: 핵심 크롤러 엔진 개발

## 2.1 아키텍처 개요

```
┌─────────────────────────────────────────────────────┐
│              CrawlerApplicationService              │
│        (크롤링 오케스트레이션 / 스케줄링 진입점)         │
└────────────────────┬────────────────────────────────┘
                     │ interface: JobScraper
          ┌──────────┴──────────┐
          ▼                     ▼
  ┌───────────────┐     ┌────────────────┐
  │ SaraminScraper │     │ JobPlanetScraper│
  └───────┬───────┘     └───────┬────────┘
          │                     │
          ▼                     ▼
  ┌─────────────────────────────────────┐
  │          PlaywrightManager          │
  │   (Browser Pool / Session 관리)      │
  └─────────────────────────────────────┘
```

---

## 2.2 PlaywrightManager (브라우저 풀 관리)

### 핵심 기능
- **브라우저 인스턴스 풀**: 동시 크롤링 시 브라우저 재사용 (무분별한 생성/파괴 방지)
- **컨텍스트 격리**: 사이트별 독립적인 BrowserContext (쿠키/세션 분리)
- **리소스 관리**: `@PreDestroy`로 애플리케이션 종료 시 브라우저 안전 종료

### Anti-Bot 전략
| 전략 | 구현 방법 |
|------|----------|
| **User-Agent 동적 변경** | 실제 브라우저 UA 목록 중 랜덤 선택 (Chrome/Firefox/Edge 최신 버전) |
| **랜덤 딜레이** | 페이지 이동/클릭 사이 2~8초 랜덤 대기 (사람 패턴 모사) |
| **Stealth 모드** | `navigator.webdriver` 프로퍼티 제거, WebGL/Canvas 핑거프린트 위조 |
| **Headless 우회** | `--disable-blink-features=AutomationControlled` 등 플래그 설정 |
| **요청 간격 조절** | 같은 도메인에 대한 요청 간 최소 3초 간격 유지 |
| **IP 로테이션** | (고도화) 프록시 서버 연동 가능 구조 설계 |

### 구현 포인트
```java
@Component
public class PlaywrightManager {
    private Playwright playwright;
    private Browser browser;
    
    @PostConstruct
    public void init() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                    "--disable-blink-features=AutomationControlled",
                    "--no-sandbox"
                ))
        );
    }
    
    public BrowserContext createStealthContext() {
        // UA 랜덤 선택, 뷰포트 랜덤 설정
        // navigator.webdriver 제거 스크립트 자동 주입
    }
    
    public void randomDelay(int minMs, int maxMs) {
        // 사람 행동 패턴 모사 랜덤 딜레이
    }
}
```

---

## 2.3 사이트별 크롤러 구현

### SaraminScraper

#### 수집 대상 페이지
1. **검색 결과 목록 페이지**: `/zf_user/search/recruit` (키워드/직무별 필터링)
2. **공고 상세 페이지**: `/zf_user/jobs/relay/view`

#### 수집 데이터 필드
| 필드 | 파싱 방법 | 비고 |
|------|----------|------|
| 제목 (title) | CSS Selector `.job_tit a` | 공고 제목 |
| 회사명 (company) | `.corp_name a` | 기업명 |
| 근무지역 (location) | `.job_condition span:nth-child(1)` | 근무 지역 |
| 경력 조건 (career) | `.job_condition span:nth-child(2)` | 신입/경력/무관 |
| 학력 조건 (education) | `.job_condition span:nth-child(3)` | 대학(4년)/초대졸 등 |
| 연봉 정보 (salary) | `.job_condition span:nth-child(4)` | 회사 내규, 면접 후 결정 등 |
| 기술 스택 (techStack) | 상세 페이지 파싱 | 태그/키워드 추출 |
| **지원 방식 (applicationMethod)** | 상세 페이지 버튼 텍스트 분석 | ⭐ 즉시지원/홈페이지지원/이메일 구분 필수 |
| 마감일 (deadline) | `.job_date .date` | D-day 또는 날짜 |
| 공고 URL (url) | `href` 속성 | 원본 링크 |
| 회사 이미지 (companyImages) | 상세 페이지 이미지 태그 | 기업 로고/사진 |

#### 크롤링 플로우
```
1. 검색 결과 페이지 접근 (키워드/직무 필터 적용)
2. 공고 목록 파싱 (제목, 회사명, 간단 조건)
3. 페이지네이션 처리 (다음 페이지 반복)
4. 각 공고 상세 페이지 접근 → 상세 데이터 파싱
5. "지원 방식" 메타 데이터 파싱 (즉시지원 버튼 유무 확인)
6. Redis 중복 체크 (URL 기준) → 신규 공고만 DB 저장
```

---

### JobPlanetScraper

#### 수집 대상 페이지
1. **채용 공고 목록**: `/job` (필터링 가능)
2. **공고 상세**: `/companies/{id}/job/{job_id}`

#### 수집 데이터 필드
| 필드 | 파싱 방법 | 비고 |
|------|----------|------|
| 제목 (title) | 목록/상세 페이지 셀렉터 | 공고 제목 |
| 회사명 (company) | 회사 정보 영역 | 기업명 |
| 기업 평점 (rating) | 잡플래닛 고유 데이터 | ⭐ 잡플래닛 특화 |
| 기업 리뷰 요약 | 기업 페이지 | ⭐ 잡플래닛 특화 |
| 근무지역 (location) | 상세 페이지 | 지역 정보 |
| 기술 스택 (techStack) | 태그 영역 | 기술 키워드 |
| **지원 방식 (applicationMethod)** | 지원 버튼 분석 | 즉시지원/홈페이지 구분 |
| 연봉 정보 (salary) | 상세 페이지 | 잡플래닛 예상 연봉 포함 가능 |
| 공고 URL (url) | 링크 추출 | 원본 링크 |

#### 크롤링 플로우
```
1. 채용 공고 목록 페이지 접근
2. 무한 스크롤 또는 페이지네이션 처리
3. 공고 목록 파싱 (제목, 회사명, 기본 정보)
4. 각 공고 상세 페이지 접근 → 상세 데이터 파싱
5. 기업 리뷰/평점 정보 추가 수집 (잡플래닛 특화)
6. Redis 중복 체크 → 신규 공고만 DB 저장
```

---

## 2.4 중복 방지 및 데이터 정합성

### Redis 기반 중복 체크
```
Key 패턴: crawled:job:{source}:{hash(url)}
Value: 1
TTL: 30일 (마감 후 자동 삭제)
```

### 데이터 정합성 체크리스트
- [ ] 같은 공고가 다른 키워드로 중복 수집되지 않도록 URL 기반 dedup
- [ ] 마감된 공고는 `status=CLOSED`로 업데이트 (삭제하지 않음)
- [ ] 공고 내용 변경 시 `updatedAt` 갱신 + 변경 이력 로깅

---

## 2.5 에러 핸들링 및 모니터링

| 에러 유형 | 처리 방식 |
|----------|----------|
| 차단 감지 (403/CAPTCHA) | 5분 대기 후 재시도 (최대 3회), 실패 시 디스코드 경고 발송 |
| DOM 구조 변경 | 셀렉터 매칭 실패 로그 → 관리자 알림 |
| 네트워크 타임아웃 | 30초 타임아웃, 3회 재시도 |
| 브라우저 크래시 | PlaywrightManager에서 자동 재생성 |
