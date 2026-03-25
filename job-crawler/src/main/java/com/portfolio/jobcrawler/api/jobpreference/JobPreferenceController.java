package com.portfolio.jobcrawler.api.jobpreference;

import com.portfolio.jobcrawler.application.jobpreference.JobPreferenceService;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
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

@Tag(name = "알림 설정", description = "공고 알림 키워드/사이트 설정")
@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class JobPreferenceController {

    private final JobPreferenceService jobPreferenceService;

    @Operation(summary = "알림 설정 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPreference>>> list(
            Authentication auth, @Parameter(description = "사이트 필터 (SARAMIN, JOBPLANET 등)") @RequestParam(required = false) String site) {
        Long userId = (Long) auth.getPrincipal();
        if (site != null && !site.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    jobPreferenceService.getMyPreferences(userId, SourceSite.valueOf(site.toUpperCase()))));
        }
        return ResponseEntity.ok(ApiResponse.ok(jobPreferenceService.getMyPreferences(userId)));
    }

    @Operation(summary = "알림 설정 추가")
    @PostMapping
    public ResponseEntity<ApiResponse<JobPreference>> add(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "알림 설정 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"site\": \"SARAMIN\", \"categoryCode\": \"백엔드개발자\", \"categoryName\": \"백엔드개발자\"}")))
            @RequestBody Map<String, String> body) {
        JobPreference pref = jobPreferenceService.addPreference(
                (Long) auth.getPrincipal(),
                SourceSite.valueOf(body.get("site").toUpperCase()),
                body.get("categoryCode"), body.get("categoryName"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pref));
    }

    @Operation(summary = "알림 설정 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(Authentication auth, @Parameter(description = "알림 설정 ID") @PathVariable Long id) {
        jobPreferenceService.removePreference((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "알림 설정 활성화/비활성화 토글")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggle(
            Authentication auth, @Parameter(description = "알림 설정 ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "활성화 여부",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"enabled\": true}")))
            @RequestBody Map<String, Boolean> body) {
        jobPreferenceService.togglePreference((Long) auth.getPrincipal(), id,
                body.getOrDefault("enabled", true));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "특정 사이트 알림 전체 비활성화")
    @PostMapping("/disable-all")
    public ResponseEntity<ApiResponse<Void>> disableAll(
            Authentication auth, @Parameter(description = "사이트 (SARAMIN, JOBPLANET 등)") @RequestParam String site) {
        jobPreferenceService.disableAllBySite((Long) auth.getPrincipal(),
                SourceSite.valueOf(site.toUpperCase()));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
