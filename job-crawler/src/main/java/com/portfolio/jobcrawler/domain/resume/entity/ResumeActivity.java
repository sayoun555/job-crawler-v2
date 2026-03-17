package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.resume.vo.ActivityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeActivity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    private String activityName;

    private String organization;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String startDate;

    private String endDate;

    private int sortOrder;

    @Builder
    private ResumeActivity(Resume resume, ActivityType activityType, String activityName,
                           String organization, String description, String startDate,
                           String endDate, int sortOrder) {
        this.resume = resume;
        this.activityType = activityType;
        this.activityName = activityName;
        this.organization = organization;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sortOrder = sortOrder;
    }

    public void update(ActivityType activityType, String activityName, String organization,
                       String description, String startDate, String endDate) {
        this.activityType = activityType;
        this.activityName = activityName;
        this.organization = organization;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
