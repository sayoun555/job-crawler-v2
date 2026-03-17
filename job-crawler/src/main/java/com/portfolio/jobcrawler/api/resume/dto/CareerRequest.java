package com.portfolio.jobcrawler.api.resume.dto;

public record CareerRequest(
        String companyName,
        String department,
        String position,
        String rank,
        String startDate,
        String endDate,
        boolean currentlyWorking,
        String jobDescription,
        String salary
) {
}
