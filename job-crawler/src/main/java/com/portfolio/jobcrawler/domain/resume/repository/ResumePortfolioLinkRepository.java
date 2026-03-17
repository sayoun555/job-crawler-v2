package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumePortfolioLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumePortfolioLinkRepository extends JpaRepository<ResumePortfolioLink, Long> {
}
