package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_portfolio_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumePortfolioLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    private String linkType;

    private String url;

    private String description;

    @Builder
    private ResumePortfolioLink(Resume resume, String linkType, String url, String description) {
        this.resume = resume;
        this.linkType = linkType;
        this.url = url;
        this.description = description;
    }
}
