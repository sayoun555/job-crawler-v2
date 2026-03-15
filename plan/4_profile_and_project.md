# Step 4: 내 프로필 및 프로젝트 관리 페이지

## 4.1 기본 프로필 관리

### 기본 이력서 (UserProfile)
사용자가 한 번 입력하면 **모든 지원에 공통 재사용**되는 기본 이력서 정보.

#### 관리 항목
| 항목 | 설명 | 타입 |
|------|------|------|
| **학력** | 학교명, 전공, 학위, 졸업일, 학점 | List\<Education\> |
| **경력** | 회사명, 직무, 기간, 담당 업무 | List\<Career\> |
| **자격증** | 자격증명, 발급기관, 취득일 | List\<Certification\> |
| **기술 스택** | 보유 기술 (언어/프레임워크/도구) + 숙련도 | List\<Skill\> |
| **강점/자기소개** | 한 줄 소개, 핵심 강점 리스트 | String |
| **링크** | GitHub, 블로그, LinkedIn 등 | Map\<String, String\> |

#### API 설계
```
GET    /api/v1/profile              사용자 프로필 조회
PUT    /api/v1/profile              프로필 전체 수정
PATCH  /api/v1/profile/skills       기술 스택 수정
PATCH  /api/v1/profile/education    학력 수정
PATCH  /api/v1/profile/career       경력 수정
```

---

## 4.2 희망 직무 설정

### 핵심 설계 원칙
**사이트마다 직무 카테고리가 다르므로**, 각 사이트의 실제 분류 체계를 그대로 가져와서 **사이트별로 복수 선택(멀티)** 가능하게 구현.

### 데이터 구조
```java
@Entity
public class JobPreference {
    @Id @GeneratedValue
    private Long id;
    
    @ManyToOne
    private User user;
    
    @Enumerated(EnumType.STRING)
    private SourceSite site;           // SARAMIN, JOBPLANET
    
    private String categoryCode;       // 사이트 내 직무 코드
    private String categoryName;       // "백엔드/서버개발"
    
    private boolean enabled = true;    // OFF 토글
}
```

### 기능 상세
- **사이트별 직무 카테고리 자동 수집**: 크롤러가 사람인/잡플래닛의 직무 분류 체계를 주기적으로 업데이트
- **멀티 선택**: 사용자가 사이트별로 여러 직무를 선택 가능
- **OFF 토글**: 희망 직무를 완전히 비활성화 → 알림/사전생성 중단
- **디스코드 알림 연동**: 설정된 직무와 매칭되는 공고만 알림 발송

#### API 설계
```
GET    /api/v1/preferences                        희망 직무 전체 조회
GET    /api/v1/preferences/{site}                  사이트별 희망 직무 조회
PUT    /api/v1/preferences/{site}                  사이트별 희망 직무 설정
PATCH  /api/v1/preferences/{site}/toggle           ON/OFF 토글
GET    /api/v1/preferences/categories/{site}       사이트별 카테고리 목록 조회
```

---

## 4.3 내 프로젝트 관리 (위시켓 스타일)

### 프로젝트 등록 정보
| 항목 | 필수 | 설명 |
|------|------|------|
| **프로젝트명** | ✅ | 프로젝트 제목 |
| **설명** | ✅ | 프로젝트 상세 설명 (Plain Text, **마크다운 금지**) |
| **GitHub 주소** | ❌ | 소스 코드 링크 |
| **노션 링크** | ❌ | 프로젝트 문서 링크 |
| **기술 스택** | ✅ | 사용 기술 태그 (멀티 선택) |
| **이미지** | ❌ | 프로젝트 스크린샷/아키텍처 다이어그램 |
| **역할** | ❌ | 프로젝트 내 담당 역할 |
| **기간** | ❌ | 프로젝트 수행 기간 |
| **인원** | ❌ | 팀 규모 |
| **주요 성과** | ❌ | 정량적/정성적 성과 |

#### API 설계
```
GET    /api/v1/projects                  프로젝트 목록 조회
GET    /api/v1/projects/{id}             프로젝트 상세 조회
POST   /api/v1/projects                  프로젝트 등록
PUT    /api/v1/projects/{id}             프로젝트 수정
DELETE /api/v1/projects/{id}             프로젝트 삭제
POST   /api/v1/projects/{id}/images      이미지 업로드
```

---

## 4.4 AI 프로젝트 정리 탭

### 동작 시나리오
```
1. 사용자가 GitHub 레포 URL 입력
2. 시스템이 레포 클론 (또는 GitHub API로 파일 목록 조회)
3. AI가 README.md, 주요 소스 파일, 패키지 구조를 분석
4. AI가 프로젝트 개요/기술 스택/아키텍처/주요 기능을 자동 정리
5. 사용자가 정리된 결과를 확인/수정
6. 결과에 템플릿 적용 및 이미지 삽입
7. "저장" → 새로운 프로젝트 항목으로 등록
8. 이후 AI 자소서/포폴 생성 시 읽을 수 있는 데이터로 활용
```

### 분석 대상 파일
| 우선순위 | 파일/디렉토리 | 분석 내용 |
|---------|-------------|----------|
| 1 | `README.md` | 프로젝트 개요, 설치 방법 |
| 2 | `build.gradle` / `pom.xml` / `package.json` | 기술 스택, 의존성 |
| 3 | `src/` 구조 | 아키텍처 패턴 (MVC, DDD 등) |
| 4 | 주요 클래스/컴포넌트 | 핵심 기능, 비즈니스 로직 |
| 5 | `.md` 문서들 | 추가 문서화된 내용 |

#### API 설계
```
POST   /api/v1/projects/ai-analyze       GitHub URL로 AI 분석 요청
GET    /api/v1/projects/ai-analyze/{taskId}/status   분석 상태 확인
GET    /api/v1/projects/ai-analyze/{taskId}/result    분석 결과 조회
POST   /api/v1/projects/ai-analyze/{taskId}/save      분석 결과를 프로젝트로 저장
```
