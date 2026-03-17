package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_languages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeLanguage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    private String languageName;

    private String examName;

    private String score;

    private String grade;

    private String examDate;

    private int sortOrder;

    @Builder
    private ResumeLanguage(Resume resume, String languageName, String examName, String score,
                           String grade, String examDate, int sortOrder) {
        this.resume = resume;
        this.languageName = languageName;
        this.examName = examName;
        this.score = score;
        this.grade = grade;
        this.examDate = examDate;
        this.sortOrder = sortOrder;
    }

    public void update(String languageName, String examName, String score,
                       String grade, String examDate) {
        this.languageName = languageName;
        this.examName = examName;
        this.score = score;
        this.grade = grade;
        this.examDate = examDate;
    }
}
