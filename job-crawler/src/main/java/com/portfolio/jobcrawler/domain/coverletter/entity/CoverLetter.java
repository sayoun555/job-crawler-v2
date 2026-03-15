package com.portfolio.jobcrawler.domain.coverletter.entity;

import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cover_letters", indexes = {
        @Index(name = "idx_cl_company", columnList = "company"),
        @Index(name = "idx_cl_period", columnList = "period")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    private String position;
    private String period;
    private String companyType;
    private String careerType;
    private String school;
    private String major;
    private String gpa;

    @Column(columnDefinition = "TEXT")
    private String specs;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private int scrapCount;

    @Column(nullable = false, unique = true, length = 500)
    private String sourceUrl;

    @Builder
    public CoverLetter(String company, String position, String period, String companyType,
                        String careerType, String school, String major, String gpa,
                        String specs, String content, int scrapCount, String sourceUrl) {
        this.company = company;
        this.position = position;
        this.period = period;
        this.companyType = companyType;
        this.careerType = careerType;
        this.school = school;
        this.major = major;
        this.gpa = gpa;
        this.specs = specs;
        this.content = content;
        this.scrapCount = scrapCount;
        this.sourceUrl = sourceUrl;
    }
}
