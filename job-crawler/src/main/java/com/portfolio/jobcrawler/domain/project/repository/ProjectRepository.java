package com.portfolio.jobcrawler.domain.project.repository;

import com.portfolio.jobcrawler.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);
}
