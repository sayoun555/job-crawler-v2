package com.portfolio.jobcrawler.infrastructure.crawler.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrawledCoverLetterData {
    private String company;
    private String position;
    private String period;
    private String companyType;
    private String careerType;
    private String school;
    private String major;
    private String gpa;
    private String specs;
    private String content;
    private int scrapCount;
    private String sourceUrl;
}
