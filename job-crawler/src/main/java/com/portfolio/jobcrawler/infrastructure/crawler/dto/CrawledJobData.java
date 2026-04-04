package com.portfolio.jobcrawler.infrastructure.crawler.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 크롤러가 수집한 원시 데이터 (인프라 DTO).
 * 도메인 엔티티와 직접 결합하지 않는다.
 * 상세 페이지 보강 시 enrichXxx() 메서드를 통해 값을 채운다.
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

    public void enrichBasicInfo(String title, String company, String location) {
        if (isNotBlank(title)) this.title = title;
        if (isNotBlank(company)) this.company = company;
        if (isNotBlank(location)) this.location = location;
    }

    public void enrichJobDetail(String description, String requirements, String companyImages) {
        if (isNotBlank(description)) this.description = description;
        if (isNotBlank(requirements)) this.requirements = requirements;
        if (isNotBlank(companyImages)) this.companyImages = companyImages;
    }

    public void enrichConditions(String career, String salary, String deadline, String education) {
        if (isNotBlank(career)) this.career = career;
        if (isNotBlank(salary)) this.salary = salary;
        if (isNotBlank(deadline)) this.deadline = deadline;
        if (isNotBlank(education)) this.education = education;
    }

    public void enrichClassification(String jobCategory, String techStack, String applicationMethod) {
        if (isNotBlank(jobCategory)) this.jobCategory = jobCategory;
        if (isNotBlank(techStack)) this.techStack = techStack;
        if (isNotBlank(applicationMethod)) this.applicationMethod = applicationMethod;
    }

    public boolean hasContent() {
        return isNotBlank(description) || isNotBlank(requirements);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
