package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_careers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeCareer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    private String companyName;

    private String department;

    private String position;

    @Column(name = "job_rank")
    private String rank;

    private String startDate;

    private String endDate;

    private boolean currentlyWorking;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    private String salary;

    private int sortOrder;

    @Builder
    private ResumeCareer(Resume resume, String companyName, String department, String position,
                         String rank, String startDate, String endDate, boolean currentlyWorking,
                         String jobDescription, String salary, int sortOrder) {
        this.resume = resume;
        this.companyName = companyName;
        this.department = department;
        this.position = position;
        this.rank = rank;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentlyWorking = currentlyWorking;
        this.jobDescription = jobDescription;
        this.salary = salary;
        this.sortOrder = sortOrder;
    }

    public void update(String companyName, String department, String position, String rank,
                       String startDate, String endDate, boolean currentlyWorking,
                       String jobDescription, String salary, int sortOrder) {
        this.companyName = companyName;
        this.department = department;
        this.position = position;
        this.rank = rank;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentlyWorking = currentlyWorking;
        this.jobDescription = jobDescription;
        this.salary = salary;
        this.sortOrder = sortOrder;
    }
}
