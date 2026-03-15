package com.portfolio.jobcrawler.api.account;

import com.portfolio.jobcrawler.application.account.ExternalAccountService;
import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class ExternalAccountController {

    private final ExternalAccountService externalAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExternalAccount>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                externalAccountService.getMyAccounts((Long) auth.getPrincipal())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExternalAccount>> register(
            Authentication auth, @RequestBody Map<String, String> body) {
        ExternalAccount account = externalAccountService.registerAccount(
                (Long) auth.getPrincipal(),
                SourceSite.valueOf(body.get("site").toUpperCase()),
                body.get("accountId"), body.get("password"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(account, "계정 등록 완료"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExternalAccount>> update(
            Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                externalAccountService.updateAccount(
                        (Long) auth.getPrincipal(), id,
                        body.get("accountId"), body.get("password"))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Long id) {
        externalAccountService.deleteAccount((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * Playwright 팝업 브라우저를 띄워 소셜 로그인 후 쿠키를 자동 추출·저장한다.
     * 프론트엔드에서 "사람인 연동" 버튼 클릭 시 호출.
     */
    @PostMapping("/login-popup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginPopup(
            Authentication auth, @RequestBody Map<String, String> body) {
        SourceSite site = SourceSite.valueOf(body.get("site").toUpperCase());
        boolean success = externalAccountService.openLoginPopup(
                (Long) auth.getPrincipal(), site);

        String siteName = site == SourceSite.SARAMIN ? "사람인" : "잡플래닛";
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("success", success,
                       "message", success ? siteName + " 연동 완료!" : siteName + " 로그인 실패 또는 타임아웃")));
    }
}
