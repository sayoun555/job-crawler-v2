# Step 5: 자소서/포트폴리오 자동화 및 멀티 템플릿 시스템

## 5.1 자동화 플로우 전체 설계

```
┌─────────────────────────────────────────────────────────┐
│                 공고 크롤링 완료 트리거                     │
└────────────────────────┬────────────────────────────────┘
                         ▼
              ┌─────────────────────┐
              │  1. 기업/직무 분석     │  OpenClaw API 호출
              │  (Pre-analysis)      │  기업 정보 + 직무 요구사항 인사이트
              └──────────┬──────────┘
                         ▼
              ┌─────────────────────┐
              │  2. 프로젝트 매칭      │  요구 기술 ↔ 내 프로젝트
              │  (상위 2~3개 선별)     │
              └──────────┬──────────┘
                         ▼
              ┌─────────────────────┐
              │  3. AI 텍스트 생성     │  자소서 + 포트폴리오 본문
              │  (OpenClaw API)      │  ※ 마크다운 절대 금지
              └──────────┬──────────┘
                         ▼
              ┌─────────────────────┐
              │  4. 사전 저장          │  Application(PENDING) 저장
              │  + 디스코드 알림       │  "새 공고 매칭됨" 알림 발송
              └─────────────────────┘
```

---

## 5.2 이력서(Resume) 고정 시스템

### 설계 원칙
- 사용자의 기본 이력서(학력, 경력, 자격증)는 **한 번 저장 후 모든 지원에 공통 재사용**
- AI가 건드리는 영역은 오직 **자기소개서(Cover Letter)**와 **포트폴리오 텍스트**만

### 구분
| 구분 | 관리 주체 | 변경 빈도 |
|------|----------|----------|
| **이력서 (Resume)** | 사용자 직접 관리 | 거의 변경 없음 (프로필 페이지) |
| **자기소개서 (Cover Letter)** | AI 자동 생성 | 공고마다 새로 생성 |
| **포트폴리오 텍스트** | AI 자동 생성 | 공고마다 새로 생성 |

---

## 5.3 사전 기업/직무 분석 (Pre-analysis)

### OpenClaw API 호출 시 전달 데이터
```json
{
  "companyInfo": {
    "name": "카카오",
    "industry": "IT/인터넷",
    "size": "대기업",
    "culture": "잡플래닛 리뷰 요약 (있을 경우)"
  },
  "jobRequirements": {
    "title": "백엔드 개발자",
    "techStack": ["Java", "Spring Boot", "JPA", "Redis"],
    "experience": "신입~3년",
    "education": "대졸(4년)",
    "preferredSkills": ["MSA 경험", "AWS", "CI/CD"]
  },
  "userProfile": {
    "skills": ["Java", "Spring Boot", "JPA", "Redis", "PostgreSQL"],
    "career": "신입",
    "education": "대졸(4년)"
  }
}
```

### 반환 인사이트
```json
{
  "companyAnalysis": "카카오는 기술 문화가 강한 대기업으로...",
  "fitScore": 85,
  "fitAnalysis": "주요 요구 기술 4/4 보유, 우대사항 1/3 일치",
  "recommendedFocus": ["Spring Boot 심화 경험 강조", "Redis 캐싱 전략 어필"],
  "matchedProjects": [
    { "projectId": 1, "matchScore": 92, "relevantSkills": ["Spring Boot", "JPA", "Redis"] },
    { "projectId": 3, "matchScore": 78, "relevantSkills": ["Java", "PostgreSQL"] }
  ]
}
```

---

## 5.4 AI 자소서/포트폴리오 자동 작성 규칙

### 생성 규칙 (절대 원칙)
1. **마크다운 문법 절대 금지**: `*`, `#`, `-`, `**`, `` ` `` 등 마크다운 기호 일절 사용 불가
2. **순수 텍스트 또는 HTML/Plain 폼만 허용**
3. AI 프롬프트에 명시적으로 "절대 마크다운을 사용하지 마세요" 주입

### AI 프롬프트 구조
```
[시스템 프롬프트]
당신은 한국 취업 시장에 특화된 자기소개서 작성 전문가입니다.
다음 규칙을 반드시 지켜주세요:
1. 절대 마크다운 문법(*, #, -, **, ` 등)을 사용하지 마세요.
2. 순수 텍스트로만 작성하세요.
3. 문단 구분은 줄바꿈만 사용하세요.

[사용자 프롬프트]
아래 기업/공고 정보와 내 프로필 기반으로 자기소개서를 작성해주세요.

기업 분석: {companyAnalysis}
공고 요구사항: {jobRequirements}
내 프로필: {userProfile}
매칭된 프로젝트: {matchedProjects}
추천 강조 포인트: {recommendedFocus}
```

### 생성 결과물
| 결과물 | 형식 | 길이 |
|--------|------|------|
| 자기소개서 (Cover Letter) | Plain Text | 지원 동기, 역량, 입사 후 포부 등 3~5 항목 |
| 포트폴리오 텍스트 | Plain Text / HTML | 매칭 프로젝트 기반 기술 역량 설명 |

---

## 5.5 멀티 템플릿 시스템

### 템플릿 엔티티
```java
@Entity
public class Template {
    @Id @GeneratedValue
    private Long id;
    
    @ManyToOne
    private User user;
    
    private String name;              // "기본 자소서 템플릿", "IT대기업용" 등
    
    @Enumerated(EnumType.STRING)
    private TemplateType type;        // COVER_LETTER, PORTFOLIO
    
    @Column(columnDefinition = "TEXT")
    private String content;           // 플레이스홀더 포함 본문
    
    private boolean isDefault;        // 기본 템플릿 여부
}
```

### 플레이스홀더 변수
| 변수 | 설명 |
|------|------|
| `{{company_name}}` | 회사명 |
| `{{job_title}}` | 공고 제목/직무명 |
| `{{motivation}}` | AI 생성 지원 동기 |
| `{{competency}}` | AI 생성 역량/경험 |
| `{{project_summary}}` | AI 생성 프로젝트 요약 |
| `{{aspiration}}` | AI 생성 입사 후 포부 |
| `{{tech_stack}}` | 매칭된 기술 스택 |

### 사용 플로우
```
1. 사용자가 미리 여러 개의 템플릿을 만들어 저장
2. 공고별로 자동 지원 시 적합한 템플릿 선택 (또는 기본 템플릿 자동 적용)
3. AI 생성 텍스트가 플레이스홀더에 주입되어 최종 문서 완성
4. Preview 페이지에서 확인/수정 후 최종 지원
```

#### API 설계
```
GET    /api/v1/templates                  템플릿 목록 조회
GET    /api/v1/templates/{id}             템플릿 상세 조회
POST   /api/v1/templates                  템플릿 등록
PUT    /api/v1/templates/{id}             템플릿 수정
DELETE /api/v1/templates/{id}             템플릿 삭제
PATCH  /api/v1/templates/{id}/default     기본 템플릿 설정
```
