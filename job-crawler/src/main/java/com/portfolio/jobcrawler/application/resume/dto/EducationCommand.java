package com.portfolio.jobcrawler.application.resume.dto;

public record EducationCommand(
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
