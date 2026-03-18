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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {

    private final List<JobScraper> scrapers;
    private final JobPostingRepository jobPostingRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATS_CACHE_KEY = "cache:job:stats";

    private static final String CRAWLED_PREFIX = "crawled:job:";
    private static final Duration CRAWLED_TTL = Duration.ofDays(30);
    private static final int BATCH_SIZE = 50;

    @Override
    public int crawlAll(String keyword, String jobCategory, int maxPages) {
        int total = 0;
        for (JobScraper scraper : scrapers) {
            try {
                log.info("[{}] 크롤링 시작 - keyword: {}, category: {}, maxPages: {}", scraper.getSiteName(), keyword, jobCategory, maxPages);
                List<CrawledJobData> data = scraper.scrapeJobs(keyword, jobCategory, maxPages);
                log.info("[{}] {} 건 수집", scraper.getSiteName(), data.size());
                total += saveNewPostingsInBatch(data);
            } catch (Exception e) {
                log.error("[{}] 크롤링 실패: {}", scraper.getSiteName(), e.getMessage());
            }
        }
        log.info("전체 크롤링 완료 - 신규 저장: {} 건", total);
        if (total > 0) {
            redisTemplate.delete(STATS_CACHE_KEY);
        }
        return total;
    }

    @Override
    public int crawlBySite(String siteName, String keyword, String jobCategory, int maxPages) {
        return scrapers.stream()
                .filter(s -> s.getSiteName().equalsIgnoreCase(siteName))
                .findFirst()
                .map(scraper -> {
                    List<CrawledJobData> data = scraper.scrapeJobs(keyword, jobCategory, maxPages);
                    return saveNewPostingsInBatch(data);
                }).orElse(0);
    }

    @Override
    public int crawlBySites(List<String> siteNames, String keyword, String jobCategory, int maxPages) {
        int total = 0;
        for (JobScraper scraper : scrapers) {
            boolean matched = siteNames.stream()
                    .anyMatch(name -> name.equalsIgnoreCase(scraper.getSiteName()));
            if (!matched) continue;

            try {
                log.info("[{}] 크롤링 시작 - keyword: {}, category: {}, maxPages: {}", scraper.getSiteName(), keyword, jobCategory, maxPages);
                List<CrawledJobData> data = scraper.scrapeJobs(keyword, jobCategory, maxPages);
                log.info("[{}] {} 건 수집", scraper.getSiteName(), data.size());
                total += saveNewPostingsInBatch(data);
            } catch (Exception e) {
                log.error("[{}] 크롤링 실패: {}", scraper.getSiteName(), e.getMessage());
            }
        }
        log.info("선택 사이트 크롤링 완료 - 신규 저장: {} 건", total);
        if (total > 0) {
            redisTemplate.delete(STATS_CACHE_KEY);
        }
        return total;
    }

    /**
     * 중복 사전 필터링 후 배치 저장.
     * Redis 캐시 → DB existsByUrl 순서로 중복 체크하고,
     * 신규 공고만 모아서 saveAll()로 한 번에 저장한다.
     */
    private int saveNewPostingsInBatch(List<CrawledJobData> dataList) {
        List<JobPosting> newPostings = new ArrayList<>();

        for (CrawledJobData data : dataList) {
            if (data.getUrl() == null) continue;

            String redisKey = CRAWLED_PREFIX + data.getSourceSite() + ":" + data.getUrl().hashCode();

            // Redis 캐시 히트 → 이미 처리된 URL
            if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) continue;

            // DB 중복 체크
            if (jobPostingRepository.existsByUrl(data.getUrl())) {
                redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
                continue;
            }

            JobPosting posting = toEntity(data);
            newPostings.add(posting);
            redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
        }

        // 배치 단위로 저장 (메모리 부담 분산)
        int saved = 0;
        for (int i = 0; i < newPostings.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, newPostings.size());
            List<JobPosting> batch = newPostings.subList(i, end);
            jobPostingRepository.saveAll(batch);
            saved += batch.size();
            log.info("배치 저장 완료: {}/{} 건", saved, newPostings.size());
        }

        return saved;
    }

    private JobPosting toEntity(CrawledJobData data) {
        return JobPosting.builder()
                .title(data.getTitle()).company(data.getCompany())
                .companyLogoUrl(data.getCompanyLogoUrl()).location(data.getLocation())
                .url(data.getUrl()).description(data.getDescription())
                .source(parseSource(data.getSourceSite()))
                .applicationMethod(parseMethod(data.getApplicationMethod()))
                .education(data.getEducation()).career(data.getCareer())
                .salary(data.getSalary()).jobCategory(data.getJobCategory())
                .deadline(parseDeadline(data.getDeadline()))
                .techStack(TechStack.of(data.getTechStack())).requirements(data.getRequirements())
                .companyImages(data.getCompanyImages())
                .build();
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

        if (trimmed.contains("채용시") || trimmed.contains("상시"))
            return null;

        try {
            String clean = trimmed.replaceAll("[~\\s]", "").replaceAll("\\(.*\\)", "");

            if (clean.matches("\\d{4}\\.\\d{2}\\.\\d{2}"))
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            if (clean.matches("\\d{2}\\.\\d{2}")) {
                LocalDate date = LocalDate.parse(LocalDate.now().getYear() + "." + clean,
                        DateTimeFormatter.ofPattern("yyyy.MM.dd"));
                if (date.isBefore(LocalDate.now().minusMonths(6)))
                    date = date.plusYears(1);
                return date;
            }

            if (clean.matches("\\d{2}/\\d{2}"))
                return LocalDate.parse(LocalDate.now().getYear() + "/" + clean,
                        DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        } catch (Exception e) {
            /* ignore */
        }
        return null;
    }
}
