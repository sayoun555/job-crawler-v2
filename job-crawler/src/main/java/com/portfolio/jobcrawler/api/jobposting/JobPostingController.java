package com.portfolio.jobcrawler.api.jobposting;

import com.portfolio.jobcrawler.application.jobposting.JobPostingService;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "채용 공고", description = "공고 목록, 검색, 상세, 통계")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;  // 인터페이스 의존

    @Operation(summary = "채용 공고 목록 조회 (검색/필터)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobPosting>>> list(
            @Parameter(description = "출처 사이트 (SARAMIN, JOBPLANET 등)") @RequestParam(required = false) String source,
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "직무 카테고리") @RequestParam(required = false) String jobCategory,
            @Parameter(description = "경력 조건") @RequestParam(required = false) String career,
            @Parameter(description = "학력 조건") @RequestParam(required = false) String education,
            @Parameter(description = "근무 지역") @RequestParam(required = false) String location,
            @Parameter(description = "지원 방식") @RequestParam(required = false) String applicationMethod,
            @Parameter(description = "태그 검색 (techStack)") @RequestParam(required = false) String tag,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SourceSite site = null;
        if (source != null && !source.isBlank()) {
            try { site = SourceSite.valueOf(source.toUpperCase()); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.ok(
                jobPostingService.searchJobs(site, keyword, jobCategory, career, education, location, applicationMethod, tag, pageable)));
    }

    @Operation(summary = "채용 공고 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobPosting>> get(
            @Parameter(description = "채용 공고 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getJobPosting(id)));
    }

    @Operation(summary = "채용 공고 통계 조회")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getStats()));
    }

    @Operation(summary = "채용 공고 상세 통계 (경력/학력/지역별 분포)")
    @GetMapping("/stats/detailed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detailedStats() {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getDetailedStats()));
    }
}
