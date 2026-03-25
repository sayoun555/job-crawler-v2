package com.portfolio.jobcrawler.api.test;

import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Step 10: AI 테스트 페이지 (기능 구현 검증 체크리스트).
 * 프론트에서 체크박스 형태로 각 기능 검증 항목을 표시하고,
 * 사용자가 체크하면 상태를 저장한다.
 */
@Tag(name = "테스트", description = "기능 검증용 테스트 API")
@RestController
@RequestMapping("/api/v1/test-checklist")
public class TestChecklistController {

    // 메모리 기반 저장 (프로토타입용, 운영 시 Redis/DB 전환)
    private final Map<String, Boolean> checklistState = new LinkedHashMap<>();

    @Operation(summary = "테스트 체크리스트 전체 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChecklistItem>>> getChecklist() {
        List<ChecklistItem> items = buildChecklist();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @Operation(summary = "체크리스트 항목 토글")
    @PatchMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleItem(
            @Parameter(description = "체크리스트 항목 ID") @PathVariable String itemId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "체크 상태",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"checked\": true}")))
            @RequestBody Map<String, Boolean> body) {
        boolean checked = body.getOrDefault("checked", false);
        checklistState.put(itemId, checked);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(itemId, checked)));
    }

    @Operation(summary = "체크리스트 상태 조회")
    @GetMapping("/state")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getState() {
        return ResponseEntity.ok(ApiResponse.ok(checklistState));
    }

    private List<ChecklistItem> buildChecklist() {
        List<ChecklistItem> items = new ArrayList<>();

        // 10.1 인증 및 사용자 관리
        items.add(item("auth-signup", "10.1", "이메일 + 비밀번호 회원가입 성공"));
        items.add(item("auth-duplicate", "10.1", "중복 이메일 가입 시 에러 반환"));
        items.add(item("auth-jwt", "10.1", "로그인 시 JWT 토큰 정상 발급"));
        items.add(item("auth-jwt-expire", "10.1", "JWT 토큰 만료 시간 24시간 확인"));
        items.add(item("auth-401", "10.1", "만료된 토큰으로 API 호출 시 401 반환"));
        items.add(item("profile-save", "10.1", "기본 프로필 저장 성공"));
        items.add(item("profile-update", "10.1", "프로필 수정 후 정상 반영 확인"));
        items.add(item("ext-account-saramin", "10.1", "사람인 계정 등록 (비밀번호 암호화)"));
        items.add(item("ext-account-jp", "10.1", "잡플래닛 계정 등록"));

        // 10.2 크롤러 엔진
        items.add(item("crawl-saramin", "10.2", "사람인 키워드 공고 크롤링 성공"));
        items.add(item("crawl-saramin-detail", "10.2", "사람인 상세 페이지 파싱"));
        items.add(item("crawl-saramin-method", "10.2", "사람인 지원 방식 정확 파싱"));
        items.add(item("crawl-jp", "10.2", "잡플래닛 채용 공고 크롤링 성공"));
        items.add(item("crawl-jp-detail", "10.2", "잡플래닛 상세 페이지 파싱"));
        items.add(item("crawl-antibot", "10.2", "Anti-Bot 우회 동작 확인"));

        // 10.3 데이터 파이프라인
        items.add(item("pipeline-full-crawl", "10.3", "전체 크롤링 버튼 동작"));
        items.add(item("pipeline-schedule", "10.3", "평일 자동 크롤링 트리거 확인"));
        items.add(item("pipeline-match-score", "10.3", "AI 적합률 분석 실행"));
        items.add(item("pipeline-project-match", "10.3", "프로젝트 자동 매칭 동작"));

        // 10.4 프로젝트 관리
        items.add(item("project-crud", "10.4", "프로젝트 등록/수정/삭제"));
        items.add(item("project-image", "10.4", "프로젝트 이미지 업로드"));
        items.add(item("project-ai-github", "10.4", "GitHub URL AI 분석"));
        items.add(item("project-ai-save", "10.4", "AI 분석 결과 프로젝트 저장"));

        // 10.5 자소서/포트폴리오
        items.add(item("ai-coverletter", "10.5", "자소서 자동 생성 (마크다운 없이)"));
        items.add(item("ai-portfolio", "10.5", "포트폴리오 자동 생성"));
        items.add(item("template-crud", "10.5", "템플릿 생성/수정/삭제"));
        items.add(item("template-apply", "10.5", "템플릿 플레이스홀더 주입"));

        // 10.6 프론트엔드
        items.add(item("fe-card-layout", "10.6", "카드형 레이아웃 정상 표시"));
        items.add(item("fe-list-toggle", "10.6", "리스트형 전환 토글"));
        items.add(item("fe-tab-site", "10.6", "사이트별 탭 분리"));
        items.add(item("fe-search", "10.6", "검색 기능 동작"));
        items.add(item("fe-filter", "10.6", "고급 필터링/정렬 동작"));
        items.add(item("fe-responsive", "10.6", "반응형 레이아웃 정상"));

        // 10.7 Auto-Apply
        items.add(item("apply-session", "10.7", "사람인/잡플래닛 대리 로그인 + 세션 Redis 저장"));
        items.add(item("apply-preview", "10.7", "Preview/Edit 2분할 레이아웃"));
        items.add(item("apply-robot", "10.7", "Playwright 로봇 폼 제출 성공"));
        items.add(item("apply-file-attach", "10.7", "파일 첨부 (setInputFiles)"));
        items.add(item("apply-verify-1", "10.7", "1단계 즉시 DOM 검증"));
        items.add(item("apply-verify-2", "10.7", "2단계 사후 교차검증"));
        items.add(item("apply-retry", "10.7", "실패 시 재시도 버튼"));

        // 10.8 디스코드 알림
        items.add(item("discord-webhook", "10.8", "Webhook URL 등록"));
        items.add(item("discord-test", "10.8", "테스트 알림 발송"));
        items.add(item("discord-filter", "10.8", "희망 직무 필터링 알림"));
        items.add(item("discord-deeplink", "10.8", "딥링크 클릭 → Preview 이동"));

        // 10.9 보안
        items.add(item("sec-jwt-guard", "10.9", "JWT 없이 보호된 API 접근 시 401"));
        items.add(item("sec-aes256", "10.9", "외부 계정 AES-256 암호화 확인"));
        items.add(item("sec-cors", "10.9", "CORS 올바른 origin 설정"));

        // 상태 반영
        for (ChecklistItem ci : items) {
            ci.checked = checklistState.getOrDefault(ci.id, false);
        }
        return items;
    }

    private ChecklistItem item(String id, String section, String label) {
        return new ChecklistItem(id, section, label, checklistState.getOrDefault(id, false));
    }

    @Getter
    @NoArgsConstructor
    static class ChecklistItem {
        private String id;
        private String section;
        private String label;
        private boolean checked;

        ChecklistItem(String id, String section, String label, boolean checked) {
            this.id = id;
            this.section = section;
            this.label = label;
            this.checked = checked;
        }
    }
}
