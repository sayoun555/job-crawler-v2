package com.portfolio.jobcrawler.api.resume.dto;

public record CertificationRequest(
        String certName,
        String issuingOrganization,
        String acquiredDate
) {
}
