package com.portfolio.jobcrawler.domain.bookmark.repository;

import com.portfolio.jobcrawler.domain.bookmark.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Bookmark> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    @Query("SELECT b.jobPosting.id FROM Bookmark b WHERE b.user.id = :userId AND b.jobPosting.id IN :jobPostingIds")
    List<Long> findBookmarkedJobIds(@Param("userId") Long userId, @Param("jobPostingIds") List<Long> jobPostingIds);

    @Transactional
    @Modifying
    void deleteByJobPostingId(Long jobPostingId);

    @Transactional
    @Modifying
    void deleteByJobPostingIdIn(List<Long> jobPostingIds);

    long countByUserId(Long userId);
}
