package com.portfolio.jobcrawler.application.resume.dto;

public record CareerCommand(
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
