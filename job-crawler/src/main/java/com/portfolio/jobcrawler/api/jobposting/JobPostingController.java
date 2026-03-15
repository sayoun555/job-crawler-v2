package com.portfolio.jobcrawler.api.jobposting;

import com.portfolio.jobcrawler.application.jobposting.JobPostingService;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;  // 인터페이스 의존

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobPosting>>> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobCategory,
            @RequestParam(required = false) String career,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String applicationMethod,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SourceSite site = null;
        if (source != null && !source.isBlank()) {
            try { site = SourceSite.valueOf(source.toUpperCase()); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.ok(
                jobPostingService.searchJobs(site, keyword, jobCategory, career, education, location, applicationMethod, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobPosting>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getJobPosting(id)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getStats()));
    }
}
