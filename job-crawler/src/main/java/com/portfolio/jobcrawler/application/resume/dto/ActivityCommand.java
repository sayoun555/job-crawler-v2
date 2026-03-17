package com.portfolio.jobcrawler.application.resume.dto;

public record ActivityCommand(
        String activityType,
        String activityName,
        String organization,
        String description,
        String startDate,
        String endDate
) {
}
