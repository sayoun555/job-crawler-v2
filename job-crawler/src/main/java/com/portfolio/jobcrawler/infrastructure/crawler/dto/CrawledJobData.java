package com.portfolio.jobcrawler.infrastructure.crawler.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 크롤러가 수집한 원시 데이터 (인프라 DTO).
 * 도메인 엔티티와 직접 결합하지 않는다.
 */
@Getter
@Builder
public class CrawledJobData {
    private String title;
    private String company;
    private String companyLogoUrl;
    private String location;
    private String url;
    private String description;
    private String sourceSite;
    private String applicationMethod;
    private String education;
    private String career;
    private String salary;
    private String jobCategory;
    private String deadline;
    private String techStack;
    private String requirements;
    private String companyImages;
}
