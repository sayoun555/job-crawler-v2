package com.portfolio.jobcrawler.api.project;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.application.project.ProjectService;
import com.portfolio.jobcrawler.api.project.dto.ProjectRequest;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import com.portfolio.jobcrawler.infrastructure.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "프로젝트", description = "프로젝트 CRUD, GitHub 분석")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final AiAutomationService aiAutomationService;
    private final com.portfolio.jobcrawler.infrastructure.ai.AiTextGenerator aiTextGenerator;

    @Operation(summary = "내 프로젝트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Project>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProjects((Long) auth.getPrincipal())));
    }

    @Operation(summary = "프로젝트 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> get(Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProject((Long) auth.getPrincipal(), id)));
    }

    @Operation(summary = "프로젝트 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<Project>> create(Authentication auth, @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.createProject((Long) auth.getPrincipal(),
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(p, "프로젝트 등록 완료"));
    }

    @Operation(summary = "프로젝트 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Project>> update(Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id,
            @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.updateProject((Long) auth.getPrincipal(), id,
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.ok(ApiResponse.ok(p, "프로젝트 수정 완료"));
    }

    @Operation(summary = "프로젝트 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id) {
        projectService.deleteProject((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ===== 프로젝트 이미지 업로드 (Step 4.3) =====

    @Operation(summary = "프로젝트 이미지 업로드")
    @PostMapping("/{id}/images")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id,
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

    @Operation(summary = "GitHub URL AI 분석")
    @PostMapping("/ai-analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiAnalyze(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "GitHub 저장소 URL",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"githubUrl\": \"https://github.com/user/repo\"}")))
            @RequestBody Map<String, String> body) {
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

    // ===== 프로젝트별 포트폴리오 관리 =====

    @Operation(summary = "프로젝트 포트폴리오 조회")
    @GetMapping("/{id}/portfolio")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPortfolio(
            Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id) {
        Project project = projectService.getMyProject((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "projectId", project.getId(),
                "projectName", project.getName(),
                "content", project.getAiPortfolioContent() != null ? project.getAiPortfolioContent() : "")));
    }

    @Operation(summary = "프로젝트 포트폴리오 수동 수정/저장")
    @PutMapping("/{id}/portfolio")
    public ResponseEntity<ApiResponse<Map<String, String>>> updatePortfolio(
            Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Project project = projectService.updatePortfolio((Long) auth.getPrincipal(), id, body.get("content"));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("content", project.getAiPortfolioContent()), "포트폴리오 저장 완료"));
    }

    @Operation(summary = "프로젝트 다이어그램 프롬프트만 생성")
    @PostMapping("/{id}/diagram-prompt")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateDiagramPrompt(
            Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id) {
        Project project = projectService.getMyProject((Long) auth.getPrincipal(), id);

        // 프로젝트 데이터로 다이어그램 프롬프트 생성 요청
        StringBuilder projectData = new StringBuilder();
        projectData.append("프로젝트명: ").append(project.getName()).append("\n");
        if (project.getDescription() != null) projectData.append("설명: ").append(project.getDescription()).append("\n");
        if (project.getTechStack() != null) projectData.append("기술스택: ").append(project.getTechStack()).append("\n");
        if (project.getAiSummary() != null) projectData.append("AI분석: ").append(project.getAiSummary()).append("\n");

        String prompt = "아래 프로젝트 데이터를 보고 두 개의 다이어그램 프롬프트를 영어로 작성하라. JSON 형식으로만 응답.\n"
                + "{\"architectureDiagramPrompt\": \"...\", \"featureDiagramPrompt\": \"...\"}\n\n"
                + "architectureDiagramPrompt: 시스템 아키텍처 다이어그램. 레이어, 컴포넌트+기술명, 데이터 흐름, 인프라 포함. 3~5문장.\n"
                + "featureDiagramPrompt: 주요 기능/사용자 흐름도. 사용자 기능, 관리자 기능, 사용자 여정, 백그라운드 프로세스 포함. 3~5문장.\n\n"
                + "[프로젝트 데이터]\n" + projectData;

        try {
            var result = aiTextGenerator.generate(
                    com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest.builder()
                            .type(com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest.GenerationType.PROJECT_SUMMARY)
                            .matchedProjects(projectData.toString())
                            .jobDescription(prompt)
                            .build());
            if (!result.isSuccess()) {
                return ResponseEntity.badRequest().body(ApiResponse.errorTyped("AI_ERR", "생성 실패: " + result.getErrorMessage()));
            }
            String text = result.getGeneratedText().trim();
            // JSON 추출
            if (text.contains("```json")) {
                text = text.substring(text.indexOf("```json") + 7);
                text = text.substring(0, text.indexOf("```")).trim();
            } else if (text.contains("```")) {
                text = text.substring(text.indexOf("```") + 3);
                text = text.substring(0, text.indexOf("```")).trim();
            }
            if (text.startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Map.class);
                return ResponseEntity.ok(ApiResponse.ok(parsed));
            }
            return ResponseEntity.ok(ApiResponse.ok(Map.of("raw", result.getGeneratedText())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.errorTyped("AI_ERR", "다이어그램 프롬프트 생성 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "포트폴리오 에디터 이미지 업로드")
    @PostMapping("/{id}/portfolio/images")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadPortfolioImage(
            Authentication auth, @Parameter(description = "프로젝트 ID") @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            projectService.getMyProject((Long) auth.getPrincipal(), id);
            String imageUrl = fileStorageService.storePortfolioImage(file, id);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("imageUrl", imageUrl)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.errorTyped("IMG_ERR", "이미지 업로드 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "AI 분석 결과를 프로젝트로 저장")
    @PostMapping("/ai-analyze/save")
    public ResponseEntity<ApiResponse<Project>> saveAiAnalyzedProject(
            Authentication auth, @Valid @RequestBody ProjectRequest req) {
        Project p = projectService.createProject((Long) auth.getPrincipal(),
                req.getName(), req.getDescription(), req.getGithubUrl(), req.getNotionUrl(),
                req.getTechStack(), req.getAiSummary());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(p, "AI 분석 프로젝트 저장 완료"));
    }
}
