# AI 비동기 작업 알림: WebSocket + 폴링 Fallback 설계

## 문제

AI 자소서/포트폴리오 생성은 30초~1분 이상 걸리는 비동기 작업이다. 사용자에게 완료 시점을 알려줘야 하는데, WebSocket만 쓰면 아래 상황에서 알림을 놓친다:

- 네트워크 불안정으로 WebSocket 연결 끊김
- 모바일 브라우저 탭 비활성화 시 연결 해제
- 프록시/방화벽이 WebSocket 차단
- 서버 재시작 시 모든 연결 소실

## 해결

WebSocket(STOMP)을 주 채널로 사용하되, 3초 간격 폴링을 fallback으로 함께 둔다.

### 구조

```
[AI 작업 완료]
    ↓
[Redis에 결과 저장 + WebSocket /topic/ai/{userId} 발송]
    ↓
[프론트엔드]
    ├─ WebSocket 수신 → 즉시 UI 반영 + 폴링 중지
    └─ 폴링 (3초 간격) → GET /ai/async/status/{taskId} → 완료 시 UI 반영 + 폴링 중지
```

### 새로고침 복구

진행 중인 태스크를 localStorage에 저장한다. 새로고침 후에도 복구되어 폴링을 재시작한다. 10분 이상 된 태스크는 자동 만료 처리한다.

### 구현 위치

- 백엔드: `AiTaskQueue.java` — Redis 저장 + WebSocket 발송
- 프론트: `use-ai-task-queue.ts` — WebSocket 수신 + 폴링 fallback + localStorage 복구

## 기술 선택 이유

| 방식 | 장점 | 단점 |
|---|---|---|
| WebSocket만 | 실시간, 서버 부하 낮음 | 연결 끊기면 알림 유실 |
| 폴링만 | 안정적, 구현 단순 | 3초 지연, 불필요한 요청 반복 |
| WebSocket + 폴링 | 실시간 + 안정성 둘 다 | 구현 복잡도 약간 증가 |

WebSocket이 정상이면 폴링 요청은 발생하지 않으므로 서버 부하 추가는 거의 없다. WebSocket이 끊겼을 때만 폴링이 동작한다.

## 결과

- 정상 시: WebSocket으로 즉시 알림 (지연 0초)
- 연결 끊김 시: 폴링으로 최대 3초 이내 감지
- 새로고침: localStorage에서 복구 후 폴링 재시작
- 10분 이상 방치: 자동 만료 처리
