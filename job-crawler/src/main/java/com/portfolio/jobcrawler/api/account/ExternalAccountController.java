package com.portfolio.jobcrawler.api.account;

import com.portfolio.jobcrawler.application.account.ExternalAccountService;
import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "외부 계정", description = "채용 사이트 계정 연동, 세션 관리")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class ExternalAccountController {

    private final ExternalAccountService externalAccountService;

    @Operation(summary = "내 외부 계정 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExternalAccount>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                externalAccountService.getMyAccounts((Long) auth.getPrincipal())));
    }

    @Operation(summary = "외부 계정 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<ExternalAccount>> register(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "외부 계정 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"site\": \"SARAMIN\", \"accountId\": \"user@email.com\", \"password\": \"pw1234\"}")))
            @RequestBody Map<String, String> body) {
        ExternalAccount account = externalAccountService.registerAccount(
                (Long) auth.getPrincipal(),
                SourceSite.valueOf(body.get("site").toUpperCase()),
                body.get("accountId"), body.get("password"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(account, "계정 등록 완료"));
    }

    @Operation(summary = "외부 계정 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExternalAccount>> update(
            Authentication auth, @Parameter(description = "계정 ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "계정 수정 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"accountId\": \"user@email.com\", \"password\": \"newPw1234\"}")))
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                externalAccountService.updateAccount(
                        (Long) auth.getPrincipal(), id,
                        body.get("accountId"), body.get("password"))));
    }

    @Operation(summary = "외부 계정 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @Parameter(description = "계정 ID") @PathVariable Long id) {
        externalAccountService.deleteAccount((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 일회용 로그인: ID/PW로 서버가 대리 로그인 후 쿠키만 저장한다.
     * 비밀번호는 서버 메모리에서 즉시 폐기되며 DB에 저장하지 않는다.
     */
    @Operation(summary = "일회용 대리 로그인 (비밀번호 즉시 폐기)")
    @PostMapping("/onetime-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> onetimeLogin(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일회용 로그인 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"site\": \"SARAMIN\", \"loginId\": \"user@email.com\", \"password\": \"pw1234\"}")))
            @RequestBody Map<String, String> body) {
        SourceSite site = SourceSite.valueOf(body.get("site").toUpperCase());
        boolean success = externalAccountService.onetimeLogin(
                (Long) auth.getPrincipal(), site,
                body.get("loginId"), body.get("password"));

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("success", success,
                       "message", success ? site.name() + " 연동 완료!" : "로그인 실패 - ID/PW를 확인하세요")));
    }

    /**
     * 브라우저 확장에서 캡처한 쿠키로 세션을 등록한다.
     * 확장 프로그램이 유저 브라우저에서 직접 로그인 후 쿠키를 서버로 전송.
     */
    @Operation(summary = "브라우저 확장 쿠키로 세션 등록")
    @PostMapping("/cookie-session")
    public ResponseEntity<ApiResponse<ExternalAccount>> registerCookieSession(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "쿠키 세션 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"site\": \"SARAMIN\", \"cookies\": \"session_id=abc123; token=xyz\"}")))
            @RequestBody Map<String, String> body) {
        SourceSite site = SourceSite.valueOf(body.get("site").toUpperCase());
        String cookies = body.get("cookies");
        ExternalAccount account = externalAccountService.registerCookieSession(
                (Long) auth.getPrincipal(), site, cookies);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(account, site.name() + " 쿠키 세션 등록 완료"));
    }

    /**
     * Playwright 팝업 브라우저를 띄워 소셜 로그인 후 쿠키를 자동 추출·저장한다.
     * 프론트엔드에서 "사람인 연동" 버튼 클릭 시 호출.
     */
    @Operation(summary = "Playwright 팝업 브라우저로 소셜 로그인 연동")
    @PostMapping("/login-popup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginPopup(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "팝업 로그인 사이트 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"site\": \"SARAMIN\"}")))
            @RequestBody Map<String, String> body) {
        SourceSite site = SourceSite.valueOf(body.get("site").toUpperCase());
        boolean success = externalAccountService.openLoginPopup(
                (Long) auth.getPrincipal(), site);

        String siteName = site == SourceSite.SARAMIN ? "사람인" : "잡플래닛";
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("success", success,
                       "message", success ? siteName + " 연동 완료!" : siteName + " 로그인 실패 또는 타임아웃")));
    }
}
