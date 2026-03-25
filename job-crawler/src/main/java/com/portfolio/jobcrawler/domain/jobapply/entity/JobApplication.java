package com.portfolio.jobcrawler.domain.jobapply.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationStatus;
import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "job_posting_id" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class JobApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Column(columnDefinition = "TEXT")
    private String portfolioContent;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(columnDefinition = "TEXT")
    private String matchedProjectIds;

    /** 커스텀 자소서 문항별 JSON: [{"title":"지원동기","rule":"500자","content":"..."}] */
    @Column(columnDefinition = "TEXT")
    private String coverLetterSections;

    /** 커스텀 포트폴리오 문항별 JSON: [{"title":"프로젝트 개요","rule":"...","content":"..."}] */
    @Column(columnDefinition = "TEXT")
    private String portfolioSections;

    private LocalDateTime appliedAt;

    @Column(columnDefinition = "TEXT")
    private String failReason;

    @Builder
    public JobApplication(User user, JobPosting jobPosting, String coverLetter,
            String portfolioContent, Template template, String matchedProjectIds) {
        this.user = user;
        this.jobPosting = jobPosting;
        this.status = ApplicationStatus.PENDING;
        this.coverLetter = coverLetter;
        this.portfolioContent = portfolioContent;
        this.template = template;
        this.matchedProjectIds = matchedProjectIds;
    }

    // === 도메인 비즈니스 로직 ===
    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void markAsApplied() {
        this.status = ApplicationStatus.APPLIED;
        this.appliedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = ApplicationStatus.FAILED;
        this.failReason = reason;
    }

    public void markAsManuallyApplied() {
        this.status = ApplicationStatus.MANUALLY_MARKED;
        this.appliedAt = LocalDateTime.now();
    }

    public void verify() {
        this.status = ApplicationStatus.VERIFIED;
    }

    public boolean canRetry() {
        return this.status == ApplicationStatus.FAILED;
    }

    public void retry() {
        if (!canRetry()) {
            throw new IllegalStateException("실패한 지원만 재시도할 수 있습니다.");
        }
        this.status = ApplicationStatus.PENDING;
        this.failReason = null;
    }

    public void updateDocuments(String coverLetter, String portfolioContent) {
        this.coverLetter = coverLetter;
        this.portfolioContent = portfolioContent;
    }

    public void updateCoverLetterSections(String sectionsJson) {
        this.coverLetterSections = sectionsJson;
    }

    public void updatePortfolioSections(String sectionsJson) {
        this.portfolioSections = sectionsJson;
    }

    public boolean hasPortfolioSections() {
        return portfolioSections != null && !portfolioSections.isBlank();
    }

    public void changeTemplate(Template template) {
        this.template = template;
    }

    public void updateMatchedProjects(String matchedProjectIds) {
        this.matchedProjectIds = matchedProjectIds;
    }

    public boolean hasCoverLetterSections() {
        return coverLetterSections != null && !coverLetterSections.isBlank();
    }
}
