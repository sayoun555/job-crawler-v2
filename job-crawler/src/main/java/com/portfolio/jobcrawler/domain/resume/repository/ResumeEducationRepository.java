package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeEducation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeEducationRepository extends JpaRepository<ResumeEducation, Long> {
}
