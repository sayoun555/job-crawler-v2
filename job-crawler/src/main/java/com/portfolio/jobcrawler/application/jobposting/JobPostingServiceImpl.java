package com.portfolio.jobcrawler.application.jobposting;

import com.portfolio.jobcrawler.domain.jobapply.repository.JobApplicationRepository;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationMethod;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingServiceImpl implements JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final com.portfolio.jobcrawler.domain.aianalysis.repository.AiAnalysisResultRepository aiAnalysisResultRepository;
    private final com.portfolio.jobcrawler.domain.notification.repository.NotificationHistoryRepository notificationHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STATS_CACHE_KEY = "cache:job:stats";
    private static final String STATS_LOCK_KEY = "lock:job:stats";
    private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    @Override
    public Page<JobPosting> searchJobs(SourceSite source, String keyword, String jobCategory,
                                       String career, String education, String location, String applicationMethod,
                                       Pageable pageable) {
        ApplicationMethod method = null;
        if (applicationMethod != null && !applicationMethod.isBlank()) {
            try { method = ApplicationMethod.valueOf(applicationMethod); } catch (Exception ignored) {}
        }
        return jobPostingRepository.searchJobs(source, keyword, jobCategory, career, education, location, method, pageable);
    }

    @Override
    public JobPosting getJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));
    }

    @Override
    public Map<String, Long> getStats() {
        // Redis 캐시 확인 (JSON 문자열로 저장/복원)
        try {
            Object cached = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
            if (cached instanceof String json) {
                log.debug("[캐시 히트] 공고 통계");
                return objectMapper.readValue(json, new TypeReference<Map<String, Long>>() {});
            }
        } catch (Exception e) {
            log.debug("[캐시 미스] 역직렬화 실패, DB 조회: {}", e.getMessage());
        }

        // Cache Stampede 방지: 분산 락으로 동시 DB 조회 차단
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(STATS_LOCK_KEY, "1", LOCK_TTL);
        if (Boolean.FALSE.equals(locked)) {
            // 다른 스레드가 이미 DB 조회 중 → 짧게 대기 후 캐시 재확인
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            try {
                Object retry = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
                if (retry instanceof String json2) {
                    return objectMapper.readValue(json2, new TypeReference<Map<String, Long>>() {});
                }
            } catch (Exception ignored) {}
        }

        // DB 조회 후 캐시 저장
        long saramin = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.SARAMIN);
        long jobplanet = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBPLANET);
        long linkareer = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.LINKAREER);
        long jobkorea = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBKOREA);

        Map<String, Long> stats = new HashMap<>();
        stats.put("saramin", saramin);
        stats.put("jobplanet", jobplanet);
        stats.put("linkareer", linkareer);
        stats.put("jobkorea", jobkorea);
        stats.put("total", saramin + jobplanet + linkareer + jobkorea);

        try {
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(STATS_CACHE_KEY, json, STATS_CACHE_TTL);
            log.debug("[캐시 저장] 공고 통계 (5분 TTL)");
        } catch (Exception e) {
            log.warn("[캐시 저장 실패] {}", e.getMessage());
        }
        return stats;
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        aiAnalysisResultRepository.deleteByJobPostingId(id);
        notificationHistoryRepository.deleteByJobPostingId(id);
        jobApplicationRepository.deleteByJobPostingId(id);
        jobPostingRepository.deleteById(id);
        evictJobCaches();
    }

    @Override
    @Transactional
    public void deleteJobs(java.util.List<Long> ids) {
        aiAnalysisResultRepository.deleteByJobPostingIdIn(ids);
        notificationHistoryRepository.deleteByJobPostingIdIn(ids);
        jobApplicationRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllByIdInBatch(ids);
        evictJobCaches();
    }

    @Override
    @Transactional
    public void deleteAllJobs() {
        aiAnalysisResultRepository.deleteAllInBatch();
        notificationHistoryRepository.deleteAllInBatch();
        jobApplicationRepository.deleteAllInBatch();
        jobPostingRepository.deleteAllInBatch();
        clearCrawledCache();
        evictJobCaches();
    }

    @Override
    @Transactional
    public int deleteJobsBySite(String site) {
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        java.util.List<JobPosting> jobs = jobPostingRepository.findBySource(sourceSite);
        if (jobs.isEmpty()) return 0;
        java.util.List<Long> ids = jobs.stream().map(JobPosting::getId).toList();
        aiAnalysisResultRepository.deleteByJobPostingIdIn(ids);
        notificationHistoryRepository.deleteByJobPostingIdIn(ids);
        jobApplicationRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllInBatch(jobs);
        evictJobCaches();
        return ids.size();
    }

    /** 공고 변경 시 관련 캐시 무효화 */
    public void evictJobCaches() {
        redisTemplate.delete(STATS_CACHE_KEY);
        log.debug("[캐시 무효화] 공고 통계 캐시 삭제");
    }

    private void clearCrawledCache() {
        try {
            Set<String> keys = redisTemplate.keys("crawled:job:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Redis 크롤링 캐시 {} 건 삭제", keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 삭제 실패: {}", e.getMessage());
        }
    }
}
