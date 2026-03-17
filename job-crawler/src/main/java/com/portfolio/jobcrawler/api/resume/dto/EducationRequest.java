package com.portfolio.jobcrawler.api.resume.dto;

public record EducationRequest(
        String schoolType,
        String schoolName,
        String major,
        String subMajor,
        String startDate,
        String endDate,
        String graduationStatus,
        String gpa,
        String gpaScale
) {
}
