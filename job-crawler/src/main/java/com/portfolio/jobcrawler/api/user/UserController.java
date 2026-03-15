package com.portfolio.jobcrawler.api.user;

import com.portfolio.jobcrawler.application.user.UserService;
import com.portfolio.jobcrawler.api.user.dto.ProfileUpdateRequest;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService; // 인터페이스 의존

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<User>> getMe(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser((Long) auth.getPrincipal())));
    }

    @PatchMapping("/users/me/nickname")
    public ResponseEntity<ApiResponse<User>> updateNickname(
            Authentication auth, @RequestBody Map<String, String> body) {
        User user = userService.updateNickname((Long) auth.getPrincipal(), body.get("nickname"));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> getProfile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile((Long) auth.getPrincipal())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(
            Authentication auth, @RequestBody ProfileUpdateRequest req) {
        UserProfile profile = userService.updateProfile(
                (Long) auth.getPrincipal(),
                req.getEducation(), req.getCareer(), req.getCertifications(),
                req.getTechStack(), req.getStrengths());
        return ResponseEntity.ok(ApiResponse.ok(profile, "프로필 수정 완료"));
    }

    @PutMapping("/settings/discord-webhook")
    public ResponseEntity<ApiResponse<Void>> updateWebhook(
            Authentication auth, @RequestBody Map<String, String> body) {
        userService.updateDiscordWebhook((Long) auth.getPrincipal(), body.get("webhookUrl"));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/settings/notification")
    public ResponseEntity<ApiResponse<Void>> toggleNotification(
            Authentication auth, @RequestBody Map<String, Boolean> body) {
        userService.toggleNotification((Long) auth.getPrincipal(), body.getOrDefault("enabled", true));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
