package com.portfolio.jobcrawler.infrastructure.crawler.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 크롤러가 수집한 원시 데이터 (인프라 DTO).
 * 도메인 엔티티와 직접 결합하지 않는다.
 * 상세 페이지 보강 시 Setter를 통해 값을 채운다.
 */
@Getter
@Setter
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
