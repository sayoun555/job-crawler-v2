package com.portfolio.jobcrawler.application.resume.dto;

public record ResumeBasicInfoCommand(
        String name,
        String phone,
        String email,
        String gender,
        String birthDate,
        String address
) {
}
