package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeCertification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeCertificationRepository extends JpaRepository<ResumeCertification, Long> {
}
