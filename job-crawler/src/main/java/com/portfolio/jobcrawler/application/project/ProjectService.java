package com.portfolio.jobcrawler.application.project;

import com.portfolio.jobcrawler.domain.project.entity.Project;

import java.util.List;

/**
 * 프로젝트 Application Service 인터페이스.
 */
public interface ProjectService {
    List<Project> getMyProjects(Long userId);

    Project getMyProject(Long userId, Long projectId);

    Project createProject(Long userId, String name, String description,
            String githubUrl, String notionUrl, String techStack);

    Project updateProject(Long userId, Long projectId, String name, String description,
            String githubUrl, String notionUrl, String techStack);

    void deleteProject(Long userId, Long projectId);
}
