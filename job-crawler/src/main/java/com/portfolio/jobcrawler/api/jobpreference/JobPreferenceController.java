package com.portfolio.jobcrawler.api.jobpreference;

import com.portfolio.jobcrawler.application.jobpreference.JobPreferenceService;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class JobPreferenceController {

    private final JobPreferenceService jobPreferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPreference>>> list(
            Authentication auth, @RequestParam(required = false) String site) {
        Long userId = (Long) auth.getPrincipal();
        if (site != null && !site.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    jobPreferenceService.getMyPreferences(userId, SourceSite.valueOf(site.toUpperCase()))));
        }
        return ResponseEntity.ok(ApiResponse.ok(jobPreferenceService.getMyPreferences(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobPreference>> add(
            Authentication auth, @RequestBody Map<String, String> body) {
        JobPreference pref = jobPreferenceService.addPreference(
                (Long) auth.getPrincipal(),
                SourceSite.valueOf(body.get("site").toUpperCase()),
                body.get("categoryCode"), body.get("categoryName"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pref));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(Authentication auth, @PathVariable Long id) {
        jobPreferenceService.removePreference((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggle(
            Authentication auth, @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        jobPreferenceService.togglePreference((Long) auth.getPrincipal(), id,
                body.getOrDefault("enabled", true));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/disable-all")
    public ResponseEntity<ApiResponse<Void>> disableAll(
            Authentication auth, @RequestParam String site) {
        jobPreferenceService.disableAllBySite((Long) auth.getPrincipal(),
                SourceSite.valueOf(site.toUpperCase()));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
