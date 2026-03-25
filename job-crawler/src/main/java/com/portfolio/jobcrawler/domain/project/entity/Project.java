package com.portfolio.jobcrawler.domain.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String githubUrl;

    @Column(length = 500)
    private String notionUrl;

    @Column(columnDefinition = "TEXT")
    private String techStack;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @Column(columnDefinition = "TEXT")
    private String aiPortfolioContent;

    @ElementCollection
    @CollectionTable(name = "project_images", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "image_url", length = 1000)
    private List<String> imageUrls = new ArrayList<>();

    @Builder
    public Project(User user, String name, String description, String githubUrl,
            String notionUrl, String techStack) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.githubUrl = githubUrl;
        this.notionUrl = notionUrl;
        this.techStack = techStack;
    }

    // === 도메인 비즈니스 로직 ===

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void update(String name, String description, String githubUrl,
            String notionUrl, String techStack, String aiSummary) {
        this.name = name;
        this.description = description;
        this.githubUrl = githubUrl;
        this.notionUrl = notionUrl;
        this.techStack = techStack;
        this.aiSummary = aiSummary;
    }

    public void updateAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public void updatePortfolioContent(String content) {
        this.aiPortfolioContent = content;
    }

    public void addImage(String imageUrl) {
        this.imageUrls.add(imageUrl);
    }

    public void removeImage(String imageUrl) {
        this.imageUrls.remove(imageUrl);
    }
}
