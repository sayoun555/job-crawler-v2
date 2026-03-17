package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeCareer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeCareerRepository extends JpaRepository<ResumeCareer, Long> {
}
