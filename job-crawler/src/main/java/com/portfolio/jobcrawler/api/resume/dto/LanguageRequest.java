package com.portfolio.jobcrawler.api.resume.dto;

public record LanguageRequest(
        String languageName,
        String examName,
        String score,
        String grade,
        String examDate
) {
}
