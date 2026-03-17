package com.portfolio.jobcrawler.api.resume.dto;

public record ActivityRequest(
        String activityType,
        String activityName,
        String organization,
        String description,
        String startDate,
        String endDate
) {
}
