package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeSkill extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    private String skillName;

    private int sortOrder;

    @Builder
    private ResumeSkill(Resume resume, String skillName, int sortOrder) {
        this.resume = resume;
        this.skillName = skillName;
        this.sortOrder = sortOrder;
    }
}
