package com.portfolio.jobcrawler.api.ai;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.domain.aianalysis.entity.AiAnalysisResult;
import com.portfolio.jobcrawler.domain.aianalysis.repository.AiAnalysisResultRepository;
import com.portfolio.jobcrawler.domain.aianalysis.vo.AnalysisType;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "AI 분석", description = "적합률, 자소서/포트폴리오 생성, 기업 분석, 프로젝트 분석")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAutomationService aiAutomationService;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final com.portfolio.jobcrawler.application.ai.AiTaskQueue aiTaskQueue;

    /** 적합률 분석 */
    @Operation(summary = "채용 공고 적합률 분석")
    @PostMapping("/match-score/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> matchScore(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "캐시 무시 강제 재분석") @RequestParam(required = false, defaultValue = "false") boolean force) {
        Long userId = (Long) auth.getPrincipal();
        int score = ((com.portfolio.jobcrawler.application.ai.AiAutomationServiceImpl) aiAutomationService)
                .analyzeMatchScore(userId, jobId, force);
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(userId, jobId, AnalysisType.MATCH_SCORE)
                .ifPresent(r -> { if (r.getResultText() != null) result.put("reason", r.getResultText()); });
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 유저별 적합률 일괄 조회 (공고 ID 목록) */
    @Operation(summary = "공고 목록의 유저별 적합률 일괄 조회")
    @PostMapping("/match-scores/batch")
    public ResponseEntity<ApiResponse<Map<Long, Integer>>> batchMatchScores(
            Authentication auth,
            @RequestBody List<Long> jobIds) {
        Long userId = (Long) auth.getPrincipal();
        List<AiAnalysisResult> results = aiAnalysisResultRepository
                .findByUserIdAndJobPostingIdInAndType(userId, jobIds, AnalysisType.MATCH_SCORE);
        Map<Long, Integer> scoreMap = new HashMap<>();
        for (AiAnalysisResult r : results) {
            if (r.getScore() != null) {
                scoreMap.put(r.getJobPostingId(), r.getScore());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(scoreMap));
    }

    /** 프로젝트 자동 매칭 */
    @Operation(summary = "채용 공고에 적합한 프로젝트 자동 매칭")
    @GetMapping("/match-projects/{jobId}")
    public ResponseEntity<ApiResponse<List<Project>>> matchProjects(
            Authentication auth, @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.ok(
                aiAutomationService.matchProjects((Long) auth.getPrincipal(), jobId)));
    }

    /** 자소서 자동 생성 */
    @Operation(summary = "AI 자소서 자동 생성")
    @PostMapping("/cover-letter/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateCoverLetter(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId) {
        String text = aiAutomationService.generateCoverLetter((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("coverLetter", text)));
    }

    /** 포트폴리오 자동 생성 */
    @Operation(summary = "AI 포트폴리오 자동 생성")
    @PostMapping("/portfolio/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePortfolio(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId) {
        String text = aiAutomationService.generatePortfolio((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("portfolio", text)));
    }

    /** 기업 분석 (공고 기준 공유) */
    @Operation(summary = "AI 기업 분석 (공고 기준)")
    @GetMapping("/company-analysis/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> analyzeCompany(
            Authentication auth, @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        String analysis = aiAutomationService.analyzeCompany((Long) auth.getPrincipal(), jobId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("analysis", analysis)));
    }

    /** 저장된 AI 분석 결과 조회 (적합률: 유저별, 기업분석: 공유) */
    @Operation(summary = "저장된 AI 분석 결과 조회")
    @GetMapping("/results/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSavedResults(
            Authentication auth, @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> result = new HashMap<>();

        Optional<AiAnalysisResult> matchScore = aiAnalysisResultRepository
                .findByUserIdAndJobPostingIdAndType(userId, jobId, AnalysisType.MATCH_SCORE);
        matchScore.ifPresent(r -> {
            result.put("matchScore", r.getScore());
            if (r.getResultText() != null) result.put("matchScoreReason", r.getResultText());
        });

        Optional<AiAnalysisResult> companyAnalysis = aiAnalysisResultRepository
                .findByJobPostingIdAndType(jobId, AnalysisType.COMPANY_ANALYSIS);
        companyAnalysis.ifPresent(r -> result.put("companyAnalysis", r.getResultText()));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 합격 자소서 패턴 분석 */
    @Operation(summary = "합격 자소서 패턴 AI 분석")
    @PostMapping("/cover-letter-analysis/{coverLetterId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeCoverLetterPattern(
            Authentication auth, @Parameter(description = "합격 자소서 ID") @PathVariable Long coverLetterId) {
        String result = aiAutomationService.analyzeCoverLetterPattern(coverLetterId);

        // JSON 파싱 시도
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonStr = result.trim();
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
                parsed.put("raw", result);
                return ResponseEntity.ok(ApiResponse.ok(parsed));
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.ok(Map.of("raw", result)));
    }

    /** 프로젝트 AI 정리 */
    @Operation(summary = "프로젝트 AI 요약 정리")
    @PostMapping("/summarize-project/{projectId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> summarizeProject(
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId) {
        String summary = aiAutomationService.summarizeProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("summary", summary)));
    }

    /** 비동기 자소서 생성 (즉시 taskId 반환 + 완료 시 WebSocket 알림) */
    @Operation(summary = "비동기 AI 자소서 생성 (taskId 즉시 반환)")
    @PostMapping("/async/cover-letter/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> asyncCoverLetter(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId,
            @Parameter(description = "프로젝트 ID 목록 (콤마 구분)") @RequestParam(required = false) String projectIds) {
        Long userId = (Long) auth.getPrincipal();
        String taskId = aiTaskQueue.enqueue("COVER_LETTER", userId);
        List<Long> pidList = parseProjectIds(projectIds);

        new Thread(() -> {
            try {
                // 기업 분석 선행 (없으면 자동 생성, 있으면 캐시)
                try { aiAutomationService.analyzeCompany(userId, jobId); } catch (Exception ignored) {}
                String text = aiAutomationService.generateCoverLetter(userId, jobId, templateId, pidList);
                aiTaskQueue.complete(taskId, userId, text);
            } catch (Exception e) {
                aiTaskQueue.fail(taskId, userId, e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    /** 비동기 포트폴리오 생성 */
    @Operation(summary = "비동기 AI 포트폴리오 생성 (taskId 즉시 반환)")
    @PostMapping("/async/portfolio/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> asyncPortfolio(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId,
            @Parameter(description = "프로젝트 ID 목록 (콤마 구분)") @RequestParam(required = false) String projectIds) {
        Long userId = (Long) auth.getPrincipal();
        String taskId = aiTaskQueue.enqueue("PORTFOLIO", userId);
        List<Long> pidList = parseProjectIds(projectIds);

        new Thread(() -> {
            try {
                // 기업 분석 선행 (없으면 자동 생성, 있으면 캐시)
                try { aiAutomationService.analyzeCompany(userId, jobId); } catch (Exception ignored) {}
                String text = aiAutomationService.generatePortfolio(userId, jobId, templateId, pidList);
                aiTaskQueue.complete(taskId, userId, text);
            } catch (Exception e) {
                aiTaskQueue.fail(taskId, userId, e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    private List<Long> parseProjectIds(String projectIds) {
        if (projectIds == null || projectIds.isBlank()) return List.of();
        return java.util.Arrays.stream(projectIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
    }

    /** 프로젝트 단위 범용 포트폴리오 생성 (비동기) */
    @Operation(summary = "프로젝트별 AI 포트폴리오 생성 (범용, 비동기)")
    @PostMapping("/async/portfolio/project/{projectId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> asyncProjectPortfolio(
            Authentication auth,
            @Parameter(description = "프로젝트 ID") @PathVariable Long projectId) {
        Long userId = (Long) auth.getPrincipal();
        String taskId = aiTaskQueue.enqueue("PROJECT_PORTFOLIO", userId);

        new Thread(() -> {
            try {
                String content = aiAutomationService.generateProjectPortfolio(userId, projectId);
                aiTaskQueue.complete(taskId, userId, content);
            } catch (Exception e) {
                aiTaskQueue.fail(taskId, userId, e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    /** AI 태스크 상태 폴링 (WebSocket 미연결 시 fallback) */
    @Operation(summary = "비동기 AI 태스크 상태 조회")
    @GetMapping("/async/status/{taskId}")
    public ResponseEntity<ApiResponse<Map<Object, Object>>> getTaskStatus(
            @Parameter(description = "태스크 ID") @PathVariable String taskId) {
        Map<Object, Object> task = aiTaskQueue.getTask(taskId);
        if (task.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "NOT_FOUND")));
        }
        return ResponseEntity.ok(ApiResponse.ok(task));
    }
}
