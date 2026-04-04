package com.portfolio.jobcrawler.application.bookmark;

import com.portfolio.jobcrawler.domain.bookmark.entity.Bookmark;
import com.portfolio.jobcrawler.domain.bookmark.repository.BookmarkRepository;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Bookmark addBookmark(Long userId, Long jobPostingId) {
        if (bookmarkRepository.existsByUserIdAndJobPostingId(userId, jobPostingId)) {
            throw new CustomException(ErrorCode.ALREADY_BOOKMARKED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));

        return bookmarkRepository.save(new Bookmark(user, jobPosting));
    }

    @Override
    @Transactional
    public void removeBookmark(Long userId, Long jobPostingId) {
        Bookmark bookmark = bookmarkRepository.findByUserIdAndJobPostingId(userId, jobPostingId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));

        if (!bookmark.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        bookmarkRepository.delete(bookmark);
    }

    @Override
    public Page<Bookmark> getUserBookmarks(Long userId, Pageable pageable) {
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public boolean isBookmarked(Long userId, Long jobPostingId) {
        return bookmarkRepository.existsByUserIdAndJobPostingId(userId, jobPostingId);
    }

    @Override
    public List<Long> getBookmarkedJobIds(Long userId, List<Long> jobPostingIds) {
        if (jobPostingIds.isEmpty()) return List.of();
        return bookmarkRepository.findBookmarkedJobIds(userId, jobPostingIds);
    }
}
