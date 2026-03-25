package com.portfolio.jobcrawler.api.auth;

import com.portfolio.jobcrawler.application.auth.AuthService;
import com.portfolio.jobcrawler.application.auth.dto.LoginCommand;
import com.portfolio.jobcrawler.application.auth.dto.SignupCommand;
import com.portfolio.jobcrawler.application.auth.dto.TokenResult;
import com.portfolio.jobcrawler.api.auth.dto.LoginRequest;
import com.portfolio.jobcrawler.api.auth.dto.SignupRequest;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 갱신")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService; // 인터페이스 의존

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResult>> signup(@Valid @RequestBody SignupRequest req) {
        TokenResult result = authService
                .signup(new SignupCommand(req.getEmail(), req.getPassword(), req.getNickname()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, "회원가입 성공"));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResult>> login(@Valid @RequestBody LoginRequest req) {
        TokenResult result = authService.login(new LoginCommand(req.getEmail(), req.getPassword()));
        return ResponseEntity.ok(ApiResponse.ok(result, "로그인 성공"));
    }

    @Operation(summary = "토큰 갱신 (리프레시 토큰)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResult>> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.ok(null, "리프레시 토큰이 필요합니다."));
        }
        TokenResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(result, "토큰 갱신 성공"));
    }
}
