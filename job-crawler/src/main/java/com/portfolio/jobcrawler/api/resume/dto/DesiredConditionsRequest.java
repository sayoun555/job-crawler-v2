package com.portfolio.jobcrawler.api.resume.dto;

public record DesiredConditionsRequest(
        String desiredSalary,
        String desiredEmploymentType,
        String desiredLocation,
        String militaryStatus,
        String disabilityStatus,
        String veteranStatus
) {
}
