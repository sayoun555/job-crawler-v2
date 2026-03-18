package com.portfolio.jobcrawler.api.template;

import com.portfolio.jobcrawler.application.template.TemplateService;
import com.portfolio.jobcrawler.api.template.dto.TemplateRequest;
import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /** 시스템 프리셋 조회 (모든 유저) */
    @GetMapping("/presets")
    public ResponseEntity<ApiResponse<List<Template>>> presets() {
        return ResponseEntity.ok(ApiResponse.ok(templateService.getSystemPresets()));
    }

    /** AI로 프리셋 갱신 (관리자 전용) */
    @PostMapping("/presets/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshPresets(Authentication auth) {
        int count = templateService.refreshPresetsWithAi();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updatedCount", count), count + "개 프리셋 갱신 완료"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Template>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.getMyTemplates((Long) auth.getPrincipal())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Template>> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.getMyTemplate((Long) auth.getPrincipal(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Template>> create(Authentication auth, @Valid @RequestBody TemplateRequest req) {
        Template t = templateService.createTemplate((Long) auth.getPrincipal(),
                req.getName(), req.getType(), req.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(t, "템플릿 등록 완료"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Template>> update(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody TemplateRequest req) {
        Template t = templateService.updateTemplate((Long) auth.getPrincipal(), id, req.getName(), req.getContent());
        return ResponseEntity.ok(ApiResponse.ok(t, "템플릿 수정 완료"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Long id) {
        templateService.deleteTemplate((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<Void>> setDefault(Authentication auth, @PathVariable Long id) {
        templateService.setDefault((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
