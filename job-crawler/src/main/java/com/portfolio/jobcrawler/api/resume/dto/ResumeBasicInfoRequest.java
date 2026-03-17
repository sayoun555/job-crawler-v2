package com.portfolio.jobcrawler.api.resume.dto;

public record ResumeBasicInfoRequest(
        String name,
        String phone,
        String email,
        String gender,
        String birthDate,
        String address
) {
}
