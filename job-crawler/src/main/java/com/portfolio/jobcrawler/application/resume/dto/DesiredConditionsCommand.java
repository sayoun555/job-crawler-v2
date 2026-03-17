package com.portfolio.jobcrawler.application.resume.dto;

public record DesiredConditionsCommand(
        String desiredSalary,
        String desiredEmploymentType,
        String desiredLocation,
        String militaryStatus,
        String disabilityStatus,
        String veteranStatus
) {
}
