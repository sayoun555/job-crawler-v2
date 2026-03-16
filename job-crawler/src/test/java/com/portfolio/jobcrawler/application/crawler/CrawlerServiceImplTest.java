package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.infrastructure.crawler.JobScraper;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledJobData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlerServiceImplTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private JobScraper mockScraper;

    @Captor
    private ArgumentCaptor<List<JobPosting>> batchCaptor;

    private CrawlerServiceImpl crawlerService;

    @BeforeEach
    void setUp() {
        crawlerService = new CrawlerServiceImpl(
                List.of(mockScraper), jobPostingRepository, redisTemplate);
    }

    @Test
    @DisplayName("신규 공고는 saveAll 배치로 저장된다")
    void savesNewPostingsInBatch() {
        // given
        List<CrawledJobData> crawledData = List.of(
                buildCrawledData("https://example.com/1", "공고1"),
                buildCrawledData("https://example.com/2", "공고2"),
                buildCrawledData("https://example.com/3", "공고3")
        );

        given(mockScraper.getSiteName()).willReturn("TEST");
        given(mockScraper.scrapeJobs(any(), any(), anyInt())).willReturn(crawledData);
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(jobPostingRepository.existsByUrl(anyString())).willReturn(false);
        given(jobPostingRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        int saved = crawlerService.crawlAll(null, null, 10);

        // then
        assertThat(saved).isEqualTo(3);
        verify(jobPostingRepository).saveAll(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(3);
        // 개별 save() 호출은 없어야 한다
        verify(jobPostingRepository, never()).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Redis에 이미 있는 URL은 건너뛴다")
    void skipsRedisHit() {
        // given
        String cachedUrl = "https://example.com/cached";
        String newUrl = "https://example.com/new";
        String cachedRedisKey = "crawled:job:SARAMIN:" + cachedUrl.hashCode();
        String newRedisKey = "crawled:job:SARAMIN:" + newUrl.hashCode();

        List<CrawledJobData> crawledData = List.of(
                buildCrawledData(cachedUrl, "캐시된 공고"),
                buildCrawledData(newUrl, "신규 공고")
        );

        given(mockScraper.getSiteName()).willReturn("TEST");
        given(mockScraper.scrapeJobs(any(), any(), anyInt())).willReturn(crawledData);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        given(redisTemplate.hasKey(cachedRedisKey)).willReturn(true);
        given(redisTemplate.hasKey(newRedisKey)).willReturn(false);
        given(jobPostingRepository.existsByUrl(newUrl)).willReturn(false);
        given(jobPostingRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        int saved = crawlerService.crawlAll(null, null, 10);

        // then
        assertThat(saved).isEqualTo(1);
        verify(jobPostingRepository, never()).existsByUrl(cachedUrl);
    }

    @Test
    @DisplayName("DB에 이미 있는 URL은 Redis에 캐시하고 건너뛴다")
    void skipsDbHitAndCachesInRedis() {
        // given
        String existingUrl = "https://example.com/existing";
        String redisKey = "crawled:job:SARAMIN:" + existingUrl.hashCode();

        List<CrawledJobData> crawledData = List.of(
                buildCrawledData(existingUrl, "기존 공고")
        );

        given(mockScraper.getSiteName()).willReturn("TEST");
        given(mockScraper.scrapeJobs(any(), any(), anyInt())).willReturn(crawledData);
        given(redisTemplate.hasKey(redisKey)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(jobPostingRepository.existsByUrl(existingUrl)).willReturn(true);

        // when
        int saved = crawlerService.crawlAll(null, null, 10);

        // then
        assertThat(saved).isEqualTo(0);
        verify(valueOperations).set(eq(redisKey), eq("1"), any(Duration.class));
        verify(jobPostingRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("URL이 null인 데이터는 무시한다")
    void skipsNullUrl() {
        // given
        List<CrawledJobData> crawledData = List.of(
                buildCrawledData(null, "URL 없는 공고"),
                buildCrawledData("https://example.com/valid", "정상 공고")
        );

        given(mockScraper.getSiteName()).willReturn("TEST");
        given(mockScraper.scrapeJobs(any(), any(), anyInt())).willReturn(crawledData);
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(jobPostingRepository.existsByUrl("https://example.com/valid")).willReturn(false);
        given(jobPostingRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        int saved = crawlerService.crawlAll(null, null, 10);

        // then
        assertThat(saved).isEqualTo(1);
    }

    @Test
    @DisplayName("크롤러 실패 시 다른 크롤러는 계속 동작한다")
    void continuesOnScraperFailure() {
        // given
        JobScraper failingScraper = mock(JobScraper.class);
        JobScraper workingScraper = mock(JobScraper.class);

        given(failingScraper.getSiteName()).willReturn("FAIL");
        given(failingScraper.scrapeJobs(any(), any(), anyInt())).willThrow(new RuntimeException("크롤링 에러"));

        given(workingScraper.getSiteName()).willReturn("WORK");
        given(workingScraper.scrapeJobs(any(), any(), anyInt()))
                .willReturn(List.of(buildCrawledData("https://ok.com/1", "성공 공고")));

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(jobPostingRepository.existsByUrl(anyString())).willReturn(false);
        given(jobPostingRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        CrawlerServiceImpl service = new CrawlerServiceImpl(
                List.of(failingScraper, workingScraper), jobPostingRepository, redisTemplate);

        // when
        int saved = service.crawlAll(null, null, 10);

        // then
        assertThat(saved).isEqualTo(1);
    }

    private CrawledJobData buildCrawledData(String url, String title) {
        return CrawledJobData.builder()
                .title(title).company("테스트 회사").companyLogoUrl("")
                .location("서울").url(url).description("설명")
                .sourceSite("SARAMIN").applicationMethod("UNKNOWN")
                .education("대졸").career("신입").salary("")
                .deadline("").techStack("Java,Spring")
                .jobCategory("서버/백엔드").requirements("")
                .companyImages("")
                .build();
    }
}
