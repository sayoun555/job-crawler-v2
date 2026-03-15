# Step 3: 데이터 파이프라인 및 저장소 구축

## 3.1 데이터 정제 파이프라인

```
크롤링 원시 데이터 (JobPostingDto)
        │
        ▼
┌───────────────────────┐
│  1. 데이터 정규화       │  텍스트 클렌징, 공백/특수문자 정리
│  2. 메타 데이터 파싱     │  지원 방식, 학력, 경력, 연봉 구조화
│  3. 기술 스택 태깅       │  키워드 추출 → 표준 기술 스택 매핑
│  4. 중복 체크 (Redis)    │  URL 해시 기반 dedup
└───────────┬───────────┘
            ▼
┌───────────────────────┐
│  5. AI 적합률 분석       │  사용자 프로필 vs 공고 요구사항 비교
│  6. 프로젝트 자동 매칭    │  요구 스킬 ↔ 내 프로젝트 기술 스택 매칭
│  7. 자소서/포폴 사전 생성  │  즉시 검토 가능하도록 미리 생성
└───────────┬───────────┘
            ▼
    DB 저장 (JobPosting Entity)
```

---

## 3.2 메타 데이터 필수 정제 항목

### 지원 방식 (ApplicationMethod) 파싱 — 최우선
```java
public enum ApplicationMethod {
    DIRECT_APPLY,   // 사람인/잡플래닛 내 즉시 지원
    HOMEPAGE,       // 회사 채용 홈페이지로 이동
    EMAIL,          // 이메일 접수
    UNKNOWN         // 파싱 실패 시 안전 기본값
}
```

**파싱 로직**:
- 사람인: "입사지원" 버튼 → `DIRECT_APPLY`, "홈페이지 지원" 문구 → `HOMEPAGE`
- 잡플래닛: "지원하기" 버튼 유무 분석, 외부 링크 여부 확인
- **이 필드는 Auto-Apply(Step 8)의 핵심 분기점**이므로 정확도가 매우 중요

### 기타 정제 항목
| 항목 | 정규화 방법 | 예시 |
|------|-----------|------|
| **학력** | Enum 매핑 | "대졸(4년)" → `BACHELOR`, "초대졸" → `ASSOCIATE` |
| **경력** | 숫자 범위 추출 | "경력 3~5년" → `minCareer=3, maxCareer=5` |
| **연봉** | 숫자 추출 + 단위 분리 | "3,000~4,000만원" → `minSalary=3000, maxSalary=4000` |
| **기술 스택** | 키워드 추출 → 표준명 매핑 | "SpringBoot" / "스프링부트" → `SPRING_BOOT` |
| **마감일** | ISO 8601 변환 | "~03/20(수)" → `2026-03-20` |
| **근무 지역** | 시/도 + 구/군 분리 | "서울 강남구" → `city=서울, district=강남구` |

---

## 3.3 AI 적합률 분석 + 프로젝트 자동 매칭

### 적합률 산출 프로세스
```
1. 공고 요구 기술 스택 추출 (예: Java, Spring Boot, JPA, Redis)
2. 사용자 프로필의 보유 기술 스택과 비교
3. 일치율(%) 기본 산출 + AI 가중치 적용
4. OpenClaw API에 {공고 정보 + 사용자 프로필} 전달 → 종합 적합률 반환
5. 결과를 JobPosting.aiMatchScore에 저장
```

### 프로젝트 자동 매칭 로직
```
1. 공고의 요구 기술 스택 키워드 목록 추출
2. 사용자의 등록된 프로젝트들의 기술 스택과 매칭 스코어 계산
3. 매칭 스코어 상위 2~3개 프로젝트 자동 선별
4. 선별된 프로젝트를 기반으로 AI가 자소서/포트폴리오 초안 자동 생성
5. 사전 생성된 결과를 Application 테이블에 PENDING 상태로 저장
```

> **포인트**: 공고가 크롤링되면 팝업 없이 **즉시** 분석 + 매칭 + 사전생성이 완료됨. 사용자는 알림 받고 Preview 페이지에서 바로 검토만 하면 됨.

---

## 3.4 초기 크롤링 버튼 (일회용)

### 동작 시나리오
1. 사용자가 처음 가입 후 "전체 크롤링" 버튼 클릭
2. **마감되지 않은 활성 공고만** 사용자의 희망 직무 기준으로 전부 수집
3. Anti-Bot 전략 최대 적용 (랜덤 딜레이 5~15초, UA 매 요청 변경)
4. 진행 상황 실시간 표시 (WebSocket/SSE): `"127/350건 수집 중..."`
5. 완료 후 자동으로 스케줄링 모드 전환

### API 설계
```
POST /api/v1/crawler/initial-crawl
Request Body: { "userId": 1 }
Response: { "taskId": "uuid", "status": "STARTED" }

GET /api/v1/crawler/status/{taskId}
Response: { "status": "IN_PROGRESS", "progress": 127, "total": 350 }
```

---

## 3.5 스케줄링 (자동 수집)

### 스케줄링 전략
| 시간대 | 주기 | 대상 | 사유 |
|--------|------|------|------|
| **평일 09:00** | 매일 | 전체 사이트 | 기업 공고 등록 피크 시간 |
| **평일 14:00** | 매일 | 전체 사이트 | 오후 공고 업데이트 반영 |
| **매주 일요일 03:00** | 주 1회 | 전체 사이트 | 마감 공고 상태 업데이트 |

### 구현 방식
```java
@Component
public class CrawlerScheduler {
    
    @Scheduled(cron = "0 0 9 * * MON-FRI")    // 평일 오전 9시
    public void morningCrawl() {
        crawlerService.crawlAllSites();
    }
    
    @Scheduled(cron = "0 0 14 * * MON-FRI")   // 평일 오후 2시
    public void afternoonCrawl() {
        crawlerService.crawlAllSites();
    }
    
    @Scheduled(cron = "0 0 3 * * SUN")         // 매주 일요일 새벽 3시
    public void weeklyCleanup() {
        crawlerService.updateExpiredPostings();
    }
}
```

- Spring `@Scheduled` 기본 사용
- 크롤링 양이 많아지면 Quartz Scheduler로 마이그레이션 가능
- 각 사용자의 희망 직무 기반 선별적 크롤링
