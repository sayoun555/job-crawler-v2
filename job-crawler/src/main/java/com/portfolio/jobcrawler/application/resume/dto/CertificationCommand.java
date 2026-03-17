package com.portfolio.jobcrawler.application.resume.dto;

public record CertificationCommand(
        String certName,
        String issuingOrganization,
        String acquiredDate
) {
}
