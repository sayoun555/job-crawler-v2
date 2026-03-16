package com.portfolio.jobcrawler.infrastructure.crawler.core;

import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisDuplicateChecker implements DuplicateChecker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JobPostingRepository jobPostingRepository;

    private static final String CRAWLED_PREFIX = "crawled:job:";
    private static final Duration CRAWLED_TTL = Duration.ofDays(30);

    @Override
    public boolean isDuplicate(String url, String sourceSite) {
        if (url == null) return true;

        String redisKey = CRAWLED_PREFIX + sourceSite + ":" + url.hashCode();

        // Redis 캐시 히트
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return true;
        }

        // DB 체크
        if (jobPostingRepository.existsByUrl(url)) {
            // 다음에 Redis에서 바로 걸리도록 캐시
            redisTemplate.opsForValue().set(redisKey, "1", CRAWLED_TTL);
            return true;
        }

        return false;
    }
}
