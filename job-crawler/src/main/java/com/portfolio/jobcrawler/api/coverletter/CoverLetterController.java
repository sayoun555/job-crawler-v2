package com.portfolio.jobcrawler.api.coverletter;

import com.portfolio.jobcrawler.application.coverletter.CoverLetterService;
import com.portfolio.jobcrawler.domain.coverletter.entity.CoverLetter;
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

@Tag(name = "합격 자소서", description = "합격 자소서 크롤링, 목록, 패턴 분석")
@RestController
@RequestMapping("/api/v1/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    @Operation(summary = "합격 자소서 목록 조회 (검색)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CoverLetter>>> list(
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "학교 필터") @RequestParam(required = false) String school,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(coverLetterService.search(keyword, school, pageable)));
    }

    @Operation(summary = "합격 자소서 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CoverLetter>> get(@Parameter(description = "자소서 ID") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(coverLetterService.getById(id)));
    }

    @Operation(summary = "합격 자소서 크롤링 실행")
    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawl(
            @Parameter(description = "최대 크롤링 페이지 수") @RequestParam(required = false, defaultValue = "5") int maxPages) {
        int saved = coverLetterService.crawlAndSave(maxPages);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("savedCount", saved, "maxPages", maxPages), "자소서 크롤링 완료"));
    }

    @Operation(summary = "합격 자소서 통계 조회")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("total", coverLetterService.count())));
    }

    @Operation(summary = "전체 합격 자소서 삭제")
    @DeleteMapping("/all")
    public ResponseEntity<ApiResponse<Void>> deleteAll() {
        coverLetterService.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok(null, "전체 자소서 삭제 완료"));
    }
}
