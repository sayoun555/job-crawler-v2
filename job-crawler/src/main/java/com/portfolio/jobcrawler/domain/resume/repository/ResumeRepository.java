package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByUserId(Long userId);
}
