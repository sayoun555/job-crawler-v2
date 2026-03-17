package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeLanguageRepository extends JpaRepository<ResumeLanguage, Long> {
}
