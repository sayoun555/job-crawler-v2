package com.portfolio.jobcrawler.api.project;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.application.project.ProjectService;
import com.portfolio.jobcrawler.api.project.dto.ProjectRequest;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import com.portfolio.jobcrawler.infrastructure.storage.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final AiAutomationService aiAutomationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Project>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProjects((Long) auth.getPrincipal())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProject((Long) auth.getPrincipal(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Project>> create(Authentication auth, @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.createProject((Long) auth.getPrincipal(),
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(p, "프로젝트 등록 완료"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> update(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.updateProject((Long) auth.getPrincipal(), id,
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.ok(ApiResponse.ok(p, "프로젝트 수정 완료"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Long id) {
        projectService.deleteProject((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ===== 프로젝트 이미지 업로드 (Step 4.3) =====

    @PostMapping("/{id}/images")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            Authentication auth, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            Project project = projectService.getMyProject((Long) auth.getPrincipal(), id);
            String imageUrl = fileStorageService.storeProjectImage(file, project.getId());
            project.addImage(imageUrl);
            projectService.updateProject((Long) auth.getPrincipal(), id,
                    project.getName(), project.getDescription(), project.getGithubUrl(),
                    project.getNotionUrl(), project.getTechStack(), project.getAiSummary());
            return ResponseEntity.ok(ApiResponse.ok(Map.of("imageUrl", imageUrl), "이미지 업로드 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.errorTyped("IMG_ERR", "이미지 업로드 실패: " + e.getMessage()));
        }
    }

    // ===== AI GitHub 프로젝트 분석 (Step 4.4) =====

    @PostMapping("/ai-analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiAnalyze(
            Authentication auth, @RequestBody Map<String, String> body) {
        String githubUrl = body.get("githubUrl");
        if (githubUrl == null || githubUrl.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.errorTyped("INVALID", "GitHub URL이 필요합니다."));
        }
        String summary = aiAutomationService.analyzeGitHubProject(githubUrl);

        // JSON 파싱 시도 → 성공하면 구조화된 데이터 반환, 실패하면 원본 텍스트 반환
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // JSON 블록 추출 (```json ... ``` 또는 { ... })
            String jsonStr = summary;
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            jsonStr = jsonStr.trim();
            if (jsonStr.startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(jsonStr, Map.class);
                parsed.put("summary", summary);
                return ResponseEntity.ok(ApiResponse.ok(parsed));
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.ok(Map.of("summary", summary)));
    }

    @PostMapping("/ai-analyze/save")
    public ResponseEntity<ApiResponse<Project>> saveAiAnalyzedProject(
            Authentication auth, @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.createProject((Long) auth.getPrincipal(),
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(p, "AI 분석 프로젝트 저장 완료"));
    }
}
