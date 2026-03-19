package com.portfolio.jobcrawler.infrastructure.crawler;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationMethod;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobposting.vo.TechStack;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class CrawledJobDataConverter {

    public JobPosting toJobPosting(CrawledJobData data) {
        return JobPosting.builder()
                .title(data.getTitle()).company(data.getCompany())
                .companyLogoUrl(data.getCompanyLogoUrl()).location(data.getLocation())
                .url(data.getUrl()).description(data.getDescription())
                .source(toSourceSite(data.getSourceSite()))
                .applicationMethod(toApplicationMethod(data.getApplicationMethod()))
                .education(data.getEducation()).career(data.getCareer())
                .salary(data.getSalary()).jobCategory(data.getJobCategory())
                .deadline(toDeadline(data.getDeadline()))
                .techStack(TechStack.of(data.getTechStack())).requirements(data.getRequirements())
                .companyImages(data.getCompanyImages())
                .build();
    }

    private SourceSite toSourceSite(String value) {
        try {
            return SourceSite.valueOf(value);
        } catch (Exception e) {
            return SourceSite.SARAMIN;
        }
    }

    private ApplicationMethod toApplicationMethod(String value) {
        try {
            return ApplicationMethod.valueOf(value);
        } catch (Exception e) {
            return ApplicationMethod.UNKNOWN;
        }
    }

    LocalDate toDeadline(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String trimmed = raw.trim();
        if (trimmed.contains("채용시") || trimmed.contains("상시") || trimmed.contains("마감")) return null;

        try {
            String clean = trimmed
                    .replaceAll("D-\\d+", "")
                    .replaceAll("오늘.*", "")
                    .replaceAll("[~\\s]", "")
                    .replaceAll("\\(.*?\\)", "")
                    .replaceAll("[^0-9./\\-]", "")
                    .trim();

            if (clean.matches("\\d{4}[.\\-]\\d{2}[.\\-]\\d{2}")) {
                clean = clean.replace("-", ".").replace("/", ".");
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            }

            if (clean.matches("\\d{2}[./]\\d{2}")) {
                clean = clean.replace("/", ".");
                LocalDate date = LocalDate.parse(LocalDate.now().getYear() + "." + clean,
                        DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                if (date.isBefore(LocalDate.now().minusMonths(6)))
                    date = date.plusYears(1);
                return date;
            }

            if (clean.matches("\\d{8}")) {
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (Exception e) {
            // 파싱 불가능한 날짜 형식은 무시
        }
        return null;
    }
}
