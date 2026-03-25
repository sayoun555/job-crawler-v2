package com.portfolio.jobcrawler.application.project;

import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.domain.project.repository.ProjectRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Override
    public List<Project> getMyProjects(Long userId) {
        return projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Project getMyProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        if (!project.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return project;
    }

    @Override
    @Transactional
    public Project createProject(Long userId, String name, String description,
            String githubUrl, String notionUrl, String techStack, String aiSummary) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Project project = Project.builder()
                .user(user).name(name).description(description)
                .githubUrl(githubUrl).notionUrl(notionUrl).techStack(techStack)
                .build();
        if (aiSummary != null) project.updateAiSummary(aiSummary);
        return projectRepository.save(project);
    }

    @Override
    @Transactional
    public Project updateProject(Long userId, Long projectId, String name, String description,
            String githubUrl, String notionUrl, String techStack, String aiSummary) {
        Project project = getMyProject(userId, projectId);
        project.update(name, description, githubUrl, notionUrl, techStack, aiSummary);
        return project;
    }

    @Override
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = getMyProject(userId, projectId);
        projectRepository.delete(project);
    }

    @Override
    @Transactional
    public Project updatePortfolio(Long userId, Long projectId, String content) {
        Project project = getMyProject(userId, projectId);
        project.updatePortfolioContent(content);
        return project;
    }
}
