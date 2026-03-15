package com.portfolio.jobcrawler.api.coverletter;

import com.portfolio.jobcrawler.application.coverletter.CoverLetterService;
import com.portfolio.jobcrawler.domain.coverletter.entity.CoverLetter;
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
@RequestMapping("/api/v1/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CoverLetter>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String school,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(coverLetterService.search(keyword, school, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CoverLetter>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(coverLetterService.getById(id)));
    }

    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawl(
            @RequestParam(required = false, defaultValue = "5") int maxPages) {
        int saved = coverLetterService.crawlAndSave(maxPages);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("savedCount", saved, "maxPages", maxPages), "자소서 크롤링 완료"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("total", coverLetterService.count())));
    }

    @DeleteMapping("/all")
    public ResponseEntity<ApiResponse<Void>> deleteAll() {
        coverLetterService.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok(null, "전체 자소서 삭제 완료"));
    }
}
