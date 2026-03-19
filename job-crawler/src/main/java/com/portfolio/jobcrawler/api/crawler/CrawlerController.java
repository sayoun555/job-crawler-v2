package com.portfolio.jobcrawler.api.crawler;

import com.portfolio.jobcrawler.application.crawler.CrawlerScheduler;
import com.portfolio.jobcrawler.application.crawler.CrawlerService;
import com.portfolio.jobcrawler.application.jobposting.JobPostingService;
import com.portfolio.jobcrawler.application.notification.NotificationService;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;
    private final CrawlerScheduler crawlerScheduler;
    private final JobPostingService jobPostingService;
    private final NotificationService notificationService;
    private final com.portfolio.jobcrawler.application.crawler.PostingUrlValidator postingUrlValidator;
    private final JobPostingRepository jobPostingRepository;

    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobCategory,
            @RequestParam(required = false, defaultValue = "50") int maxPages) {
        int saved = crawlerService.crawlAll(keyword, jobCategory, maxPages);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("keyword", keyword != null ? keyword : "전체", "savedCount", saved), "크롤링 완료"));
    }

    @PostMapping("/crawl/sites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlBySites(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> sites = (List<String>) body.get("sites");
        String keyword = (String) body.get("keyword");
        String jobCategory = (String) body.get("jobCategory");
        int maxPages = body.get("maxPages") != null ? Integer.parseInt(body.get("maxPages").toString()) : 50;

        int saved = crawlerService.crawlBySites(sites, keyword, jobCategory, maxPages);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("sites", sites, "keyword", keyword != null ? keyword : "전체", "savedCount", saved),
                sites.size() + "개 사이트 크롤링 완료"));
    }

    @PostMapping("/crawl/{site}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlBySite(
            @PathVariable String site,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobCategory,
            @RequestParam(required = false, defaultValue = "50") int maxPages) {
        int saved = crawlerService.crawlBySite(site, keyword, jobCategory, maxPages);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("site", site, "keyword", keyword != null ? keyword : "전체", "savedCount", saved), site + " 크롤링 완료"));
    }

    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchedule() {
        return ResponseEntity.ok(ApiResponse.ok(crawlerScheduler.getCurrentSchedule(), "스케줄 조회"));
    }

    @PutMapping("/schedule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSchedule(
            @RequestBody Map<String, Object> body) {
        
        Integer maxPages = null;
        if (body.get("maxPages") != null) {
            maxPages = Integer.valueOf(body.get("maxPages").toString());
        }
        
        crawlerScheduler.updateSchedule(
            (String) body.get("schedule1"), 
            (String) body.get("schedule2"), 
            maxPages
        );
        return ResponseEntity.ok(ApiResponse.ok(crawlerScheduler.getCurrentSchedule(), "스케줄 변경 완료"));
    }

    @PatchMapping("/schedule/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSchedule() {
        boolean newState = crawlerScheduler.toggleEnabled();
        return ResponseEntity.ok(ApiResponse.ok(
                crawlerScheduler.getCurrentSchedule(),
                "자동 크롤링 " + (newState ? "활성화" : "비활성화")));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        jobPostingService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "공고 삭제 완료"));
    }

    @DeleteMapping("/jobs")
    public ResponseEntity<ApiResponse<Void>> deleteJobs(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            jobPostingService.deleteJobs(ids);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, ids != null ? ids.size() + "개 공고 삭제 완료" : "삭제 대상 없음"));
    }

    @DeleteMapping("/jobs/all")
    public ResponseEntity<ApiResponse<Void>> deleteAllJobs() {
        jobPostingService.deleteAllJobs();
        return ResponseEntity.ok(ApiResponse.ok(null, "전체 공고 삭제 완료"));
    }

    @DeleteMapping("/jobs/site/{site}")
    public ResponseEntity<ApiResponse<Void>> deleteJobsBySite(@PathVariable String site) {
        int deleted = jobPostingService.deleteJobsBySite(site);
        return ResponseEntity.ok(ApiResponse.ok(null, site + " 공고 " + deleted + "개 삭제 완료"));
    }

    @PostMapping("/notify/test")
    public ResponseEntity<ApiResponse<Void>> testNotification() {
        notificationService.notifyNewJobPostings();
        return ResponseEntity.ok(ApiResponse.ok(null, "알림 발송 테스트 완료"));
    }

    @PostMapping("/validate-urls")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateUrls() {
        int closed = postingUrlValidator.closeStaleNoDeadlinePostings();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("closedCount", closed), "URL 검증 완료 - " + closed + "건 만료 처리"));
    }

    @GetMapping("/jobs/closed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClosedJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var closedJobs = jobPostingRepository.findByClosedTrue(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt")));
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "content", closedJobs.getContent(),
                "totalElements", closedJobs.getTotalElements(),
                "totalPages", closedJobs.getTotalPages(),
                "currentPage", page
        )));
    }

}
