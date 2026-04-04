package com.portfolio.jobcrawler.api.bookmark;

import com.portfolio.jobcrawler.application.bookmark.BookmarkService;
import com.portfolio.jobcrawler.domain.bookmark.entity.Bookmark;
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

import java.util.List;
import java.util.Map;

@Tag(name = "관심 공고", description = "북마크 추가/삭제/조회")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "관심 공고 추가")
    @PostMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Bookmark>> addBookmark(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        Long userId = (Long) auth.getPrincipal();
        Bookmark bookmark = bookmarkService.addBookmark(userId, jobId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bookmark, "관심 공고 추가 완료"));
    }

    @Operation(summary = "관심 공고 삭제")
    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        Long userId = (Long) auth.getPrincipal();
        bookmarkService.removeBookmark(userId, jobId);
        return ResponseEntity.ok(ApiResponse.ok(null, "관심 공고 삭제 완료"));
    }

    @Operation(summary = "내 관심 공고 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Bookmark>>> listBookmarks(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(bookmarkService.getUserBookmarks(userId, pageable)));
    }

    @Operation(summary = "공고 북마크 여부 확인")
    @GetMapping("/{jobId}/exists")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isBookmarked(
            Authentication auth,
            @Parameter(description = "채용 공고 ID") @PathVariable Long jobId) {
        Long userId = (Long) auth.getPrincipal();
        boolean bookmarked = bookmarkService.isBookmarked(userId, jobId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("bookmarked", bookmarked)));
    }

    @Operation(summary = "여러 공고 북마크 여부 일괄 확인")
    @PostMapping("/batch-check")
    public ResponseEntity<ApiResponse<List<Long>>> batchCheck(
            Authentication auth,
            @RequestBody List<Long> jobIds) {
        Long userId = (Long) auth.getPrincipal();
        List<Long> bookmarkedIds = bookmarkService.getBookmarkedJobIds(userId, jobIds);
        return ResponseEntity.ok(ApiResponse.ok(bookmarkedIds));
    }
}
