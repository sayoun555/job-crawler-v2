package com.portfolio.jobcrawler.domain.jobapply.repository;

import com.portfolio.jobcrawler.domain.jobapply.entity.JobApplication;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    Page<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<JobApplication> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    List<JobApplication> findByUserIdAndStatus(Long userId, ApplicationStatus status);

    List<JobApplication> findByStatus(ApplicationStatus status);

    void deleteByJobPostingId(Long jobPostingId);

    void deleteByJobPostingIdIn(java.util.Collection<Long> jobPostingIds);
}
