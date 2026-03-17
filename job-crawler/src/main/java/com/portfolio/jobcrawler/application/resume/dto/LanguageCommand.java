package com.portfolio.jobcrawler.application.resume.dto;

public record LanguageCommand(
        String languageName,
        String examName,
        String score,
        String grade,
        String examDate
) {
}
