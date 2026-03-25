package com.portfolio.jobcrawler.api.jobapply;

import com.portfolio.jobcrawler.application.jobapply.JobApplyService;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "입사 지원", description = "지원 준비, 커스텀 자소서, 제출, 재시도")
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class JobApplyController {

    private final JobApplyService jobApplyService;

    /** 지원 서류 준비 (AI 자동 생성 포함) */
    @Operation(summary = "지원 서류 준비 (AI 자동 생성 포함)")
    @PostMapping("/prepare/{jobId}")
    public ResponseEntity<ApiResponse<JobApplication>> prepare(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId) {
        JobApplication app = jobApplyService.prepareApplication((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(app, "지원서 준비 완료"));
    }

    /** 커스텀 자소서/포트폴리오로 지원 서류 준비 (문항별 AI 생성) */
    @Operation(summary = "커스텀 자소서/포트폴리오로 지원 서류 준비")
    @PostMapping("/prepare-custom/{jobId}")
    public ResponseEntity<ApiResponse<JobApplication>> prepareCustom(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "커스텀 자소서/포트폴리오 문항 정보",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"sections\": [{\"title\": \"지원동기\", \"content\": \"\"}], \"additionalRequest\": \"성실함 강조\", \"portfolioSections\": [{\"title\": \"프로젝트 소개\", \"content\": \"\"}], \"portfolioAdditionalRequest\": \"\"}")))
            @RequestBody Map<String, Object> body) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        String sectionsJson = toJsonOrEmpty(mapper, body.get("sections"));
        String additionalRequest = (String) body.getOrDefault("additionalRequest", "");

        String portfolioSectionsJson = toJsonOrEmpty(mapper, body.get("portfolioSections"));
        String portfolioAdditionalRequest = (String) body.getOrDefault("portfolioAdditionalRequest", "");

        JobApplication app = jobApplyService.prepareCustomApplication(
                (Long) auth.getPrincipal(), jobId,
                sectionsJson, additionalRequest,
                portfolioSectionsJson, portfolioAdditionalRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(app, "커스텀 지원서 준비 완료"));
    }

    private String toJsonOrEmpty(com.fasterxml.jackson.databind.ObjectMapper mapper, Object value) {
        if (value == null) return "[]";
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 최종 지원 */
    @Operation(summary = "최종 지원 제출")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<JobApplication>> submit(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.submitApplication((Long) auth.getPrincipal(), id), "지원 완료"));
    }

    /** 수동 지원 완료 표시 */
    @Operation(summary = "수동 지원 완료 표시")
    @PatchMapping("/{id}/manual-apply")
    public ResponseEntity<ApiResponse<JobApplication>> manualApply(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.markAsManuallyApplied((Long) auth.getPrincipal(), id)));
    }

    /** 재시도 */
    @Operation(summary = "지원 재시도")
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<JobApplication>> retry(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.retryApplication((Long) auth.getPrincipal(), id)));
    }

    /** 내 지원 이력 */
    @Operation(summary = "내 지원 이력 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobApplication>>> myApplications(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.getMyApplications((Long) auth.getPrincipal(), pageable)));
    }

    /** 상세 조회 */
    @Operation(summary = "지원서 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplication>> get(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.getApplication((Long) auth.getPrincipal(), id)));
    }

    /** 서류 수정 */
    @Operation(summary = "지원 서류 (자소서/포트폴리오) 수정")
    @PutMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<JobApplication>> updateDocs(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "자소서/포트폴리오 내용",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"coverLetter\": \"자기소개서 내용...\", \"portfolio\": \"포트폴리오 내용...\"}")))
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.updateDocuments((Long) auth.getPrincipal(), id,
                        body.get("coverLetter"), body.get("portfolio"))));
    }

    /** 매칭 프로젝트 선택 저장 (재생성 없이) */
    @Operation(summary = "매칭 프로젝트 선택 저장")
    @PatchMapping("/{id}/matched-projects")
    public ResponseEntity<ApiResponse<JobApplication>> updateMatchedProjects(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String projectIds = body.getOrDefault("projectIds", "");
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.updateMatchedProjects((Long) auth.getPrincipal(), id, projectIds)));
    }

    /** 프로젝트 다시 선택 → AI 재생성 */
    @Operation(summary = "프로젝트 재선택 후 AI 서류 재생성")
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ApiResponse<JobApplication>> regenerate(
            Authentication auth, @Parameter(description = "지원서 ID") @PathVariable Long id,
            @Parameter(description = "프로젝트 ID 목록 (콤마 구분)") @RequestParam String projectIds,
            @Parameter(description = "템플릿 ID (선택)") @RequestParam(required = false) Long templateId) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.regenerateWithProjects((Long) auth.getPrincipal(), id, projectIds, templateId)));
    }
}
