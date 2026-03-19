package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.infrastructure.crawler.CrawledJobDataConverter;
import com.portfolio.jobcrawler.infrastructure.crawler.JobScraper;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {

    private final List<JobScraper> scrapers;
    private final JobPostingRepository jobPostingRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CrawledJobDataConverter converter;

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
        invalidateStatsCache(total);
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
        invalidateStatsCache(total);
        return total;
    }

    private int saveNewPostingsInBatch(List<CrawledJobData> dataList) {
        List<JobPosting> newPostings = new ArrayList<>();

        for (CrawledJobData data : dataList) {
            if (data.getUrl() == null) continue;

            String redisKey = CRAWLED_PREFIX + data.getSourceSite() + ":" + data.getUrl().hashCode();

            if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) continue;

            if (jobPostingRepository.existsByUrl(data.getUrl())) {
                redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
                continue;
            }

            newPostings.add(converter.toJobPosting(data));
            redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
        }

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

    private void invalidateStatsCache(int savedCount) {
        if (savedCount > 0) {
            redisTemplate.delete(STATS_CACHE_KEY);
        }
    }
}
