# Step 9: 디스코드(Discord) 알림 연동

## 9.1 알림 시스템 전체 구조

```
┌──────────────────────┐
│  크롤러 스케줄링 실행    │
│  새 공고 수집 완료      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  사용자별 희망 직무     │    각 사용자의 JobPreference와 
│  매칭 필터링           │    새 공고를 교차 비교
└──────────┬───────────┘
           │  매칭된 공고만
           ▼
┌──────────────────────┐
│  AI 적합률 분석 완료    │    공고별 적합률 + 프로젝트 매칭
│  자소서/포폴 사전 생성   │    결과 사전 저장
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  디스코드 Webhook 발송  │    사용자별 개인 Webhook URL
│  (사용자별 개별 알림)    │    으로 알림 발송
└──────────────────────┘
```

---

## 9.2 사용자별 Webhook URL 등록

### 설정 페이지 UI
```
┌────────────────────────────────────┐
│         디스코드 알림 설정            │
│                                    │
│  Webhook URL: [________________]   │
│                                    │
│  알림 ON/OFF: [🟢 ON]              │
│                                    │
│  ※ 디스코드 서버 > 채널 설정 >        │
│    연동 > Webhook에서 URL을          │
│    복사하여 붙여넣으세요              │
│                                    │
│           [저장]  [테스트 발송]       │
└────────────────────────────────────┘
```

### 등록 플로우
1. 사용자가 자신의 디스코드 서버에서 Webhook 생성
2. 웹앱 설정 페이지에서 URL 등록
3. [테스트 발송] 으로 연결 확인
4. 알림 ON/OFF 토글로 전체 알림 제어 가능

---

## 9.3 희망 직무 기반 필터링

### 필터링 로직
```java
public List<Notification> filterAndNotify(List<JobPosting> newPostings) {
    List<User> users = userRepository.findAllNotificationEnabled();
    
    for (User user : users) {
        List<JobPreference> prefs = preferenceRepository.findByUser(user);
        
        if (prefs.isEmpty() || prefs.stream().noneMatch(JobPreference::isEnabled)) {
            continue; // 희망 직무 OFF인 사용자 스킵
        }
        
        List<JobPosting> matched = newPostings.stream()
            .filter(post -> matchesPreference(post, prefs))
            .toList();
        
        if (!matched.isEmpty()) {
            sendDiscordNotification(user, matched);
        }
    }
}
```

### 필터 조건
- 공고의 직무 카테고리가 사용자의 희망 직무 목록에 포함되는지 확인
- **사이트별 직무 카테고리 코드** 기준 매칭
- 사용자가 희망 직무를 **OFF**로 설정한 경우 알림 발송하지 않음

---

## 9.4 디스코드 알림 메시지 형식

### Embed 메시지 구조
```json
{
  "embeds": [
    {
      "title": "🆕 새 채용 공고 매칭!",
      "color": 3447003,
      "fields": [
        {
          "name": "🏢 회사",
          "value": "카카오",
          "inline": true
        },
        {
          "name": "💼 포지션",
          "value": "백엔드 개발자",
          "inline": true
        },
        {
          "name": "📊 적합률",
          "value": "85%",
          "inline": true
        },
        {
          "name": "📋 요구 기술",
          "value": "Java, Spring Boot, JPA, Redis",
          "inline": false
        },
        {
          "name": "📍 근무지",
          "value": "서울 강남구",
          "inline": true
        },
        {
          "name": "💰 연봉",
          "value": "3,500~5,000만원",
          "inline": true
        },
        {
          "name": "⏰ 마감일",
          "value": "2026-03-25 (D-15)",
          "inline": true
        },
        {
          "name": "📝 지원 방식",
          "value": "즉시지원 ✅",
          "inline": true
        }
      ],
      "footer": {
        "text": "이끼잡 | 자소서 사전 생성 완료"
      },
      "timestamp": "2026-03-10T09:00:00+09:00"
    }
  ],
  "components": [
    {
      "type": 1,
      "components": [
        {
          "type": 2,
          "style": 5,
          "label": "📄 검토 & 지원하기",
          "url": "https://job.eekky.com/preview/123"
        },
        {
          "type": 2,
          "style": 5,
          "label": "🔗 원본 공고",
          "url": "https://saramin.co.kr/..."
        }
      ]
    }
  ]
}
```

---

## 9.5 딥링크 (원클릭 지원 진입)

### 딥링크 플로우
```
디스코드 알림의 "검토 & 지원하기" 링크 클릭
        ↓
https://job.eekky.com/preview/{jobPostingId}
        ↓
JWT 토큰 유효 → 바로 Preview/Edit 페이지 표시
        ↓ (토큰 만료 시)
로그인 페이지 → 인증 → 원래 URL로 리다이렉트
        ↓
AI 자소서/포폴이 이미 준비된 상태에서 검토 시작
```

### 핵심 포인트
- 새 공고 매칭 시 AI가 **자소서/포폴을 미리 생성해둔 상태**
- 링크 클릭 시 로딩 없이 **바로 검토/수정** 가능
- 모바일에서도 반응형 웹앱으로 동일 경험 제공

---

## 9.6 알림 발송 주기 및 제한

| 항목 | 설정 |
|------|------|
| **신규 공고 알림** | 크롤링 완료 시 즉시 발송 (최대 1시간 간격) |
| **일일 요약** | 매일 20:00 "오늘 새 공고 N건" 요약 (설정 선택) |
| **지원 결과 알림** | 지원 성공/실패 시 즉시 발송 |
| **Rate Limit** | Discord Webhook limit (30회/분) 준수 |
| **묶음 발송** | 동시에 5건 이상 매칭 시 하나의 메시지로 묶어서 발송 |

#### API 설계
```
# Webhook 관리
PUT    /api/v1/settings/discord-webhook      Webhook URL 등록/수정
POST   /api/v1/settings/discord-webhook/test  테스트 알림 발송
DELETE /api/v1/settings/discord-webhook       Webhook 삭제

# 알림 설정
PATCH  /api/v1/settings/notification          알림 ON/OFF 토글
GET    /api/v1/settings/notification          알림 설정 조회
```
