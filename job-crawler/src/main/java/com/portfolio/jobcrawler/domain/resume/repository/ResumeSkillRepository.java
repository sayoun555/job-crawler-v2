package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.ResumeSkill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeSkillRepository extends JpaRepository<ResumeSkill, Long> {
}
