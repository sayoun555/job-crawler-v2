package com.portfolio.jobcrawler.api.user;

import com.portfolio.jobcrawler.application.user.UserService;
import com.portfolio.jobcrawler.api.user.dto.ProfileUpdateRequest;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "사용자", description = "사용자 정보, 프로필, 설정")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService; // 인터페이스 의존

    @Operation(summary = "내 정보 조회")
    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<User>> getMe(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser((Long) auth.getPrincipal())));
    }

    @Operation(summary = "닉네임 변경")
    @PatchMapping("/users/me/nickname")
    public ResponseEntity<ApiResponse<User>> updateNickname(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "닉네임 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"nickname\": \"새닉네임\"}")))
            @RequestBody Map<String, String> body) {
        User user = userService.updateNickname((Long) auth.getPrincipal(), body.get("nickname"));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @Operation(summary = "프로필 조회")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> getProfile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile((Long) auth.getPrincipal())));
    }

    @Operation(summary = "프로필 수정")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(
            Authentication auth, @RequestBody ProfileUpdateRequest req) {
        UserProfile profile = userService.updateProfile(
                (Long) auth.getPrincipal(),
                req.getEducation(), req.getCareer(), req.getCertifications(),
                req.getTechStack(), req.getStrengths());
        return ResponseEntity.ok(ApiResponse.ok(profile, "프로필 수정 완료"));
    }

    @Operation(summary = "디스코드 웹훅 URL 설정")
    @PutMapping("/settings/discord-webhook")
    public ResponseEntity<ApiResponse<Void>> updateWebhook(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "디스코드 웹훅 URL",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"webhookUrl\": \"https://discord.com/api/webhooks/...\"}")))
            @RequestBody Map<String, String> body) {
        userService.updateDiscordWebhook((Long) auth.getPrincipal(), body.get("webhookUrl"));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "알림 활성화/비활성화 토글")
    @PatchMapping("/settings/notification")
    public ResponseEntity<ApiResponse<Void>> toggleNotification(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "알림 활성화 여부",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"enabled\": true}")))
            @RequestBody Map<String, Boolean> body) {
        userService.toggleNotification((Long) auth.getPrincipal(), body.getOrDefault("enabled", true));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "알림 수신 시간대 설정")
    @PutMapping("/settings/notification-hours")
    public ResponseEntity<ApiResponse<Void>> updateNotificationHours(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "알림 수신 시간대",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"hours\": \"09:00-18:00\"}")))
            @RequestBody Map<String, String> body) {
        userService.updateNotificationHours((Long) auth.getPrincipal(), body.get("hours"));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
