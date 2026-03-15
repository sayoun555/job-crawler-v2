package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationMethod;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobposting.vo.TechStack;
import com.portfolio.jobcrawler.infrastructure.crawler.JobScraper;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {

    private final List<JobScraper> scrapers; // Strategy Pattern - 모든 크롤러 자동 주입
    private final JobPostingRepository jobPostingRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CRAWLED_PREFIX = "crawled:job:";
    private static final Duration CRAWLED_TTL = Duration.ofDays(30);

    @Override
    @Transactional
    public int crawlAll(String keyword, String jobCategory, int maxPages) {
        AtomicInteger total = new AtomicInteger(0);
        for (JobScraper scraper : scrapers) {
            try {
                log.info("[{}] 크롤링 시작 - keyword: {}, category: {}, maxPages: {}", scraper.getSiteName(), keyword, jobCategory, maxPages);
                List<CrawledJobData> data = scraper.scrapeJobs(keyword, jobCategory, maxPages);
                log.info("[{}] {} 건 수집", scraper.getSiteName(), data.size());
                data.forEach(d -> total.addAndGet(saveIfNew(d)));
            } catch (Exception e) {
                log.error("[{}] 크롤링 실패: {}", scraper.getSiteName(), e.getMessage());
            }
        }
        log.info("전체 크롤링 완료 - 신규 저장: {} 건", total.get());
        return total.get();
    }

    @Override
    @Transactional
    public int crawlBySite(String siteName, String keyword, String jobCategory, int maxPages) {
        return scrapers.stream()
                .filter(s -> s.getSiteName().equalsIgnoreCase(siteName))
                .findFirst()
                .map(scraper -> {
                    List<CrawledJobData> data = scraper.scrapeJobs(keyword, jobCategory, maxPages);
                    int count = 0;
                    for (CrawledJobData d : data)
                        count += saveIfNew(d);
                    return count;
                }).orElse(0);
    }

    private int saveIfNew(CrawledJobData data) {
        if (data.getUrl() == null)
            return 0;

        String redisKey = CRAWLED_PREFIX + data.getSourceSite() + ":" + data.getUrl().hashCode();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)))
            return 0;
        if (jobPostingRepository.existsByUrl(data.getUrl())) {
            redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
            return 0;
        }

        JobPosting posting = JobPosting.builder()
                .title(data.getTitle()).company(data.getCompany())
                .companyLogoUrl(data.getCompanyLogoUrl()).location(data.getLocation())
                .url(data.getUrl()).description(data.getDescription())
                .source(parseSource(data.getSourceSite()))
                .applicationMethod(parseMethod(data.getApplicationMethod()))
                .education(data.getEducation()).career(data.getCareer())
                .salary(data.getSalary()).jobCategory(data.getJobCategory()).deadline(parseDeadline(data.getDeadline()))
                .techStack(TechStack.of(data.getTechStack())).requirements(data.getRequirements())
                .companyImages(data.getCompanyImages())
                .build();

        jobPostingRepository.save(posting);
        redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
        return 1;
    }

    private SourceSite parseSource(String s) {
        try {
            return SourceSite.valueOf(s);
        } catch (Exception e) {
            return SourceSite.SARAMIN;
        }
    }

    private ApplicationMethod parseMethod(String m) {
        try {
            return ApplicationMethod.valueOf(m);
        } catch (Exception e) {
            return ApplicationMethod.UNKNOWN;
        }
    }

    private LocalDate parseDeadline(String d) {
        if (d == null || d.isBlank())
            return null;

        String trimmed = d.trim();

        // 상시채용, 채용시 → 마감일 없음
        if (trimmed.contains("채용시") || trimmed.contains("상시"))
            return null;

        try {
            String clean = trimmed.replaceAll("[~\\s]", "").replaceAll("\\(.*\\)", "");

            // YYYY.MM.DD 형식
            if (clean.matches("\\d{4}\\.\\d{2}\\.\\d{2}"))
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            // MM.DD 형식 (사람인 기본)
            if (clean.matches("\\d{2}\\.\\d{2}")) {
                LocalDate date = LocalDate.parse(LocalDate.now().getYear() + "." + clean,
                        DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                // 6개월 이상 과거면 내년으로 간주
                if (date.isBefore(LocalDate.now().minusMonths(6)))
                    date = date.plusYears(1);
                return date;
            }

            // MM/DD 형식
            if (clean.matches("\\d{2}/\\d{2}"))
                return LocalDate.parse(LocalDate.now().getYear() + "/" + clean,
                        DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        } catch (Exception e) {
            /* ignore */
        }
        return null;
    }
}
