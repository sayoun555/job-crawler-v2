package com.portfolio.jobcrawler.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectRequest {
    @NotBlank(message = "프로젝트명은 필수입니다.")
    private String name;
    private String description;
    private String githubUrl;
    private String notionUrl;
    private String techStack;
    private String aiSummary;
}
