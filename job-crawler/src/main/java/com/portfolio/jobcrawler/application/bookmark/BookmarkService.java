package com.portfolio.jobcrawler.application.bookmark;

import com.portfolio.jobcrawler.domain.bookmark.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookmarkService {

    Bookmark addBookmark(Long userId, Long jobPostingId);

    void removeBookmark(Long userId, Long jobPostingId);

    Page<Bookmark> getUserBookmarks(Long userId, Pageable pageable);

    boolean isBookmarked(Long userId, Long jobPostingId);

    List<Long> getBookmarkedJobIds(Long userId, List<Long> jobPostingIds);
}
