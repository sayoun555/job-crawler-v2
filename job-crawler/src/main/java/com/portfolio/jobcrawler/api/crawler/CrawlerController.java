package com.portfolio.jobcrawler.api.crawler;

import com.portfolio.jobcrawler.application.crawler.CrawlerScheduler;
import com.portfolio.jobcrawler.application.crawler.CrawlerService;
import com.portfolio.jobcrawler.application.jobposting.JobPostingService;
import com.portfolio.jobcrawler.application.notification.NotificationService;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "크롤러 (관리자)", description = "크롤링 실행, 스케줄 관리")
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
    private final com.portfolio.jobcrawler.infrastructure.crawler.core.PlaywrightScrapingEngine scrapingEngine;

    @Operation(summary = "크롤링 취소")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelCrawling() {
        scrapingEngine.cancelCrawling();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("cancelled", true), "크롤링 취소 요청 완료"));
    }

    @Operation(summary = "전체 사이트 크롤링 실행")
    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlAll(
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "직무 카테고리") @RequestParam(required = false) String jobCategory,
            @Parameter(description = "최대 크롤링 페이지 수") @RequestParam(required = false, defaultValue = "50") int maxPages,
            @Parameter(description = "기업형태 (public=공기업)") @RequestParam(required = false) String companyType) {
        int saved = crawlerService.crawlAll(keyword, jobCategory, maxPages, companyType);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("keyword", keyword != null ? keyword : "전체", "savedCount", saved), "크롤링 완료"));
    }

    @Operation(summary = "선택 사이트 크롤링 실행")
    @PostMapping("/crawl/sites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlBySites(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "크롤링 대상 사이트 및 옵션",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"sites\": [\"SARAMIN\", \"JOBPLANET\"], \"keyword\": \"백엔드\", \"jobCategory\": \"개발\", \"maxPages\": 50}")))
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> sites = (List<String>) body.get("sites");
        String keyword = (String) body.get("keyword");
        String jobCategory = (String) body.get("jobCategory");
        int maxPages = body.get("maxPages") != null ? Integer.parseInt(body.get("maxPages").toString()) : 50;
        String companyType = (String) body.get("companyType");

        int saved = crawlerService.crawlBySites(sites, keyword, jobCategory, maxPages, companyType);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("sites", sites, "keyword", keyword != null ? keyword : "전체", "savedCount", saved),
                sites.size() + "개 사이트 크롤링 완료"));
    }

    @Operation(summary = "단일 사이트 크롤링 실행")
    @PostMapping("/crawl/{site}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlBySite(
            @Parameter(description = "사이트 이름 (saramin, jobplanet 등)") @PathVariable String site,
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "직무 카테고리") @RequestParam(required = false) String jobCategory,
            @Parameter(description = "최대 크롤링 페이지 수") @RequestParam(required = false, defaultValue = "50") int maxPages,
            @Parameter(description = "기업형태 (public=공기업)") @RequestParam(required = false) String companyType) {
        int saved = crawlerService.crawlBySite(site, keyword, jobCategory, maxPages, companyType);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("site", site, "keyword", keyword != null ? keyword : "전체", "savedCount", saved), site + " 크롤링 완료"));
    }

    @Operation(summary = "크롤링 스케줄 조회")
    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchedule() {
        return ResponseEntity.ok(ApiResponse.ok(crawlerScheduler.getCurrentSchedule(), "스케줄 조회"));
    }

    @Operation(summary = "크롤링 스케줄 변경")
    @PutMapping("/schedule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSchedule(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "크롤링 스케줄 설정",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"schedule1\": \"0 0 9 * * MON-FRI\", \"schedule2\": \"0 0 18 * * MON-FRI\", \"maxPages\": 50}")))
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

    @Operation(summary = "자동 크롤링 활성화/비활성화 토글")
    @PatchMapping("/schedule/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSchedule() {
        boolean newState = crawlerScheduler.toggleEnabled();
        return ResponseEntity.ok(ApiResponse.ok(
                crawlerScheduler.getCurrentSchedule(),
                "자동 크롤링 " + (newState ? "활성화" : "비활성화")));
    }

    @Operation(summary = "채용 공고 단건 삭제")
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@Parameter(description = "공고 ID") @PathVariable Long id) {
        jobPostingService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "공고 삭제 완료"));
    }

    @Operation(summary = "채용 공고 다건 삭제")
    @DeleteMapping("/jobs")
    public ResponseEntity<ApiResponse<Void>> deleteJobs(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "삭제할 공고 ID 목록",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"ids\": [1, 2, 3]}")))
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            jobPostingService.deleteJobs(ids);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, ids != null ? ids.size() + "개 공고 삭제 완료" : "삭제 대상 없음"));
    }

    @Operation(summary = "전체 채용 공고 삭제")
    @DeleteMapping("/jobs/all")
    public ResponseEntity<ApiResponse<Void>> deleteAllJobs() {
        jobPostingService.deleteAllJobs();
        return ResponseEntity.ok(ApiResponse.ok(null, "전체 공고 삭제 완료"));
    }

    @Operation(summary = "사이트별 채용 공고 삭제")
    @DeleteMapping("/jobs/site/{site}")
    public ResponseEntity<ApiResponse<Void>> deleteJobsBySite(@Parameter(description = "사이트 이름") @PathVariable String site) {
        int deleted = jobPostingService.deleteJobsBySite(site);
        return ResponseEntity.ok(ApiResponse.ok(null, site + " 공고 " + deleted + "개 삭제 완료"));
    }

    @Operation(summary = "내용 비어있는 공고 일괄 삭제")
    @DeleteMapping("/jobs/empty")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteEmptyPostings() {
        int deleted = jobPostingService.deleteEmptyPostings();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("deletedCount", deleted), "빈 공고 " + deleted + "개 삭제 완료"));
    }

    @Operation(summary = "디스코드 알림 발송 테스트")
    @PostMapping("/notify/test")
    public ResponseEntity<ApiResponse<Void>> testNotification() {
        notificationService.notifyNewJobPostings();
        return ResponseEntity.ok(ApiResponse.ok(null, "알림 발송 테스트 완료"));
    }

    @Operation(summary = "만료 공고 URL 검증 및 마감 처리")
    @PostMapping("/validate-urls")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateUrls() {
        int closed = postingUrlValidator.closeStaleNoDeadlinePostings();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("closedCount", closed), "URL 검증 완료 - " + closed + "건 만료 처리"));
    }

    @Operation(summary = "마감된 공고 목록 조회")
    @GetMapping("/jobs/closed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClosedJobs(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
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
