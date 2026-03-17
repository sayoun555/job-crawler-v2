package com.portfolio.jobcrawler.application.resume.dto;

public record PortfolioLinkCommand(
        String linkType,
        String url,
        String description
) {
}
