package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.resume.vo.GraduationStatus;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_educations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeEducation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Enumerated(EnumType.STRING)
    private SchoolType schoolType;

    private String schoolName;

    private String major;

    private String subMajor;

    private String startDate;

    private String endDate;

    @Enumerated(EnumType.STRING)
    private GraduationStatus graduationStatus;

    private String gpa;

    private String gpaScale;

    private int sortOrder;

    @Builder
    private ResumeEducation(Resume resume, SchoolType schoolType, String schoolName,
                            String major, String subMajor, String startDate, String endDate,
                            GraduationStatus graduationStatus, String gpa, String gpaScale,
                            int sortOrder) {
        this.resume = resume;
        this.schoolType = schoolType;
        this.schoolName = schoolName;
        this.major = major;
        this.subMajor = subMajor;
        this.startDate = startDate;
        this.endDate = endDate;
        this.graduationStatus = graduationStatus;
        this.gpa = gpa;
        this.gpaScale = gpaScale;
        this.sortOrder = sortOrder;
    }

    public void update(SchoolType schoolType, String schoolName, String major, String subMajor,
                       String startDate, String endDate, GraduationStatus graduationStatus,
                       String gpa, String gpaScale, int sortOrder) {
        this.schoolType = schoolType;
        this.schoolName = schoolName;
        this.major = major;
        this.subMajor = subMajor;
        this.startDate = startDate;
        this.endDate = endDate;
        this.graduationStatus = graduationStatus;
        this.gpa = gpa;
        this.gpaScale = gpaScale;
        this.sortOrder = sortOrder;
    }
}
