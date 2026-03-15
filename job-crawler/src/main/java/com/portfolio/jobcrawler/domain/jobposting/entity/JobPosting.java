package com.portfolio.jobcrawler.domain.jobposting.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationMethod;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobposting.vo.TechStack;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "job_postings", indexes = {
        @Index(name = "idx_job_source", columnList = "source"),
        @Index(name = "idx_job_deadline", columnList = "deadline"),
        @Index(name = "idx_job_company", columnList = "company")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class JobPosting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(length = 500)
    private String companyLogoUrl;

    private String location;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceSite source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationMethod applicationMethod;

    private String education;
    private String career;
    private String salary;
    private String jobCategory;
    private LocalDate deadline;

    @Embedded
    private TechStack techStack;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String companyImages;

    private Integer aiMatchScore;

    private boolean closed = false;

    @Builder
    public JobPosting(String title, String company, String companyLogoUrl, String location,
            String url, String description, SourceSite source,
            ApplicationMethod applicationMethod, String education,
            String career, String salary, String jobCategory, LocalDate deadline,
            TechStack techStack, String requirements, String companyImages) {
        this.title = title;
        this.company = company;
        this.companyLogoUrl = companyLogoUrl;
        this.location = location;
        this.url = url;
        this.description = description;
        this.source = source;
        this.applicationMethod = applicationMethod;
        this.education = education;
        this.career = career;
        this.salary = salary;
        this.jobCategory = jobCategory;
        this.deadline = deadline;
        this.techStack = techStack;
        this.requirements = requirements;
        this.companyImages = companyImages;
    }

    // === 도메인 비즈니스 로직 ===

    public void updateAiMatchScore(int score) {
        this.aiMatchScore = score;
    }

    public void markAsClosed() {
        this.closed = true;
    }

    public boolean isExpired() {
        return this.deadline != null && this.deadline.isBefore(LocalDate.now());
    }

    public boolean isDirectApply() {
        return this.applicationMethod == ApplicationMethod.DIRECT_APPLY;
    }

    public void updateDetails(String description, TechStack techStack, String requirements,
            ApplicationMethod applicationMethod, String companyImages) {
        this.description = description;
        this.techStack = techStack;
        this.requirements = requirements;
        this.applicationMethod = applicationMethod;
        this.companyImages = companyImages;
    }
}
