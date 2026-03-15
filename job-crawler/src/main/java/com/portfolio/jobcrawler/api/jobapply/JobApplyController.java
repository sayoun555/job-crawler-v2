package com.portfolio.jobcrawler.api.jobapply;

import com.portfolio.jobcrawler.application.jobapply.JobApplyService;
import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class JobApplyController {

    private final JobApplyService jobApplyService;

    /** 지원 서류 준비 (AI 자동 생성 포함) */
    @PostMapping("/prepare/{jobId}")
    public ResponseEntity<ApiResponse<JobApplication>> prepare(
            Authentication auth, @PathVariable Long jobId,
            @RequestParam(required = false) Long templateId) {
        JobApplication app = jobApplyService.prepareApplication((Long) auth.getPrincipal(), jobId, templateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(app, "지원서 준비 완료"));
    }

    /** 최종 지원 */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<JobApplication>> submit(
            Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.submitApplication((Long) auth.getPrincipal(), id), "지원 완료"));
    }

    /** 수동 지원 완료 표시 */
    @PatchMapping("/{id}/manual-apply")
    public ResponseEntity<ApiResponse<JobApplication>> manualApply(
            Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.markAsManuallyApplied((Long) auth.getPrincipal(), id)));
    }

    /** 재시도 */
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<JobApplication>> retry(
            Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.retryApplication((Long) auth.getPrincipal(), id)));
    }

    /** 내 지원 이력 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobApplication>>> myApplications(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.getMyApplications((Long) auth.getPrincipal(), pageable)));
    }

    /** 상세 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplication>> get(
            Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.getApplication((Long) auth.getPrincipal(), id)));
    }

    /** 서류 수정 */
    @PutMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<JobApplication>> updateDocs(
            Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.updateDocuments((Long) auth.getPrincipal(), id,
                        body.get("coverLetter"), body.get("portfolio"))));
    }

    /** 프로젝트 다시 선택 → AI 재생성 */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ApiResponse<JobApplication>> regenerate(
            Authentication auth, @PathVariable Long id,
            @RequestParam String projectIds, @RequestParam(required = false) Long templateId) {
        return ResponseEntity.ok(ApiResponse.ok(
                jobApplyService.regenerateWithProjects((Long) auth.getPrincipal(), id, projectIds, templateId)));
    }
}
