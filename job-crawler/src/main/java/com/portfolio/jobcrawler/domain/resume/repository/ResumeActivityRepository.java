package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeActivityRepository extends JpaRepository<ResumeActivity, Long> {
}
