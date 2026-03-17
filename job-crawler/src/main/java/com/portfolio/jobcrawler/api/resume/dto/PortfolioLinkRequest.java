package com.portfolio.jobcrawler.api.resume.dto;

public record PortfolioLinkRequest(
        String linkType,
        String url,
        String description
) {
}
