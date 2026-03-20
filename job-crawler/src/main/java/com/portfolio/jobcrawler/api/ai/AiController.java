package com.portfolio.jobcrawler.api.ai;

import com.portfolio.jobcrawler.application.ai.AiAutomationService;
import com.portfolio.jobcrawler.domain.aianalysis.entity.AiAnalysisResult;
import com.portfolio.jobcrawler.domain.aianalysis.repository.AiAnalysisResultRepository;
import com.portfolio.jobcrawler.domain.aianalysis.vo.AnalysisType;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAutomationService aiAutomationService;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final com.portfolio.jobcrawler.application.ai.AiTaskQueue aiTaskQueue;

    /** 적합률 분석 */
    @PostMapping("/match-score/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> matchScore(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        Long userId = (Long) auth.getPrincipal();
        int score = ((com.portfolio.jobcrawler.application.ai.AiAutomationServiceImpl) aiAutomationService)
                .analyzeMatchScore(userId, jobId, force);
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(userId, jobId, AnalysisType.MATCH_SCORE)
                .ifPresent(r -> { if (r.getResultText() != null) result.put("reason", r.getResultText()); });
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 프로젝트 자동 매칭 */
    @GetMapping("/match-projects/{jobId}")
    public ResponseEntity<ApiResponse<List<Project>>> matchProjects(
            Authentication auth, @PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.ok(
                aiAutomationService.matchProjects((Long) auth.getPrincipal(), jobId)));
    }

    /** 자소서 자동 생성 */
    @PostMapping("/cover-letter/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateCoverLetter(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false) Long templateId) {
        String text = aiAutomationService.generateCoverLetter((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("coverLetter", text)));
    }

    /** 포트폴리오 자동 생성 */
    @PostMapping("/portfolio/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePortfolio(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false) Long templateId) {
        String text = aiAutomationService.generatePortfolio((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("portfolio", text)));
    }

    /** 기업 분석 (공고 기준 공유) */
    @GetMapping("/company-analysis/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> analyzeCompany(
            Authentication auth, @PathVariable Long jobId) {
        String analysis = aiAutomationService.analyzeCompany((Long) auth.getPrincipal(), jobId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("analysis", analysis)));
    }

    /** 저장된 AI 분석 결과 조회 (적합률: 유저별, 기업분석: 공유) */
    @GetMapping("/results/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSavedResults(
            Authentication auth, @PathVariable Long jobId) {
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
    @PostMapping("/cover-letter-analysis/{coverLetterId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeCoverLetterPattern(
            Authentication auth, @PathVariable Long coverLetterId) {
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
    @PostMapping("/summarize-project/{projectId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> summarizeProject(
            @PathVariable Long projectId) {
        String summary = aiAutomationService.summarizeProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("summary", summary)));
    }

    /** 비동기 자소서 생성 (즉시 taskId 반환 + 완료 시 WebSocket 알림) */
    @PostMapping("/async/cover-letter/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> asyncCoverLetter(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false) Long templateId) {
        Long userId = (Long) auth.getPrincipal();
        String taskId = aiTaskQueue.enqueue("COVER_LETTER", userId);

        new Thread(() -> {
            try {
                String text = aiAutomationService.generateCoverLetter(userId, jobId, templateId);
                aiTaskQueue.complete(taskId, userId, text);
            } catch (Exception e) {
                aiTaskQueue.fail(taskId, userId, e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    /** 비동기 포트폴리오 생성 */
    @PostMapping("/async/portfolio/{jobId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> asyncPortfolio(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false) Long templateId) {
        Long userId = (Long) auth.getPrincipal();
        String taskId = aiTaskQueue.enqueue("PORTFOLIO", userId);

        new Thread(() -> {
            try {
                String text = aiAutomationService.generatePortfolio(userId, jobId, templateId);
                aiTaskQueue.complete(taskId, userId, text);
            } catch (Exception e) {
                aiTaskQueue.fail(taskId, userId, e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    /** AI 태스크 상태 폴링 (WebSocket 미연결 시 fallback) */
    @GetMapping("/async/status/{taskId}")
    public ResponseEntity<ApiResponse<Map<Object, Object>>> getTaskStatus(
            @PathVariable String taskId) {
        Map<Object, Object> task = aiTaskQueue.getTask(taskId);
        if (task.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "NOT_FOUND")));
        }
        return ResponseEntity.ok(ApiResponse.ok(task));
    }
}
