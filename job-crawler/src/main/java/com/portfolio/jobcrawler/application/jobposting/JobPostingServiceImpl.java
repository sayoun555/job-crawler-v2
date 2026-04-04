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
    private final com.portfolio.jobcrawler.domain.bookmark.repository.BookmarkRepository bookmarkRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STATS_CACHE_KEY = "cache:job:stats";
    private static final String STATS_LOCK_KEY = "lock:job:stats";
    private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private static final String LIST_CACHE_PREFIX = "cache:job:list:";
    private static final String DETAIL_CACHE_PREFIX = "cache:job:detail:";
    private static final Duration LIST_CACHE_TTL = Duration.ofMinutes(1);
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(5);

    @Override
    public Page<JobPosting> searchJobs(SourceSite source, String keyword, String jobCategory,
                                       String career, String education, String location, String applicationMethod,
                                       String tag, Pageable pageable) {
        ApplicationMethod method = null;
        if (applicationMethod != null && !applicationMethod.isBlank()) {
            try { method = ApplicationMethod.valueOf(applicationMethod); } catch (Exception ignored) {}
        }

        // 목록 캐시: 로컬 DB가 0.1ms로 충분히 빨라 직렬화 오버헤드가 더 큼.
        // 클라우드(DB 네트워크 지연 1~5ms) 환경에서만 캐시 적용 권장.
        return jobPostingRepository.searchJobs(source, keyword, jobCategory, career, education, location, method, tag, pageable);
    }

    @Override
    public JobPosting getJobPosting(Long id) {
        String cacheKey = DETAIL_CACHE_PREFIX + id;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String json) {
                log.debug("[캐시 히트] 공고 상세: {}", id);
                return objectMapper.readValue(json, JobPosting.class);
            }
        } catch (Exception e) {
            log.debug("[캐시 미스] 공고 상세: {}", e.getMessage());
        }

        JobPosting job = jobPostingRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(job), DETAIL_CACHE_TTL);
            log.debug("[캐시 저장] 공고 상세: {} (5분 TTL)", id);
        } catch (Exception e) {
            log.debug("[캐시 저장 실패] {}", e.getMessage());
        }
        return job;
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
        long jobalio = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBALIO);
        long wanted = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.WANTED);

        Map<String, Long> stats = new HashMap<>();
        stats.put("saramin", saramin);
        stats.put("jobplanet", jobplanet);
        stats.put("linkareer", linkareer);
        stats.put("jobkorea", jobkorea);
        stats.put("jobalio", jobalio);
        stats.put("wanted", wanted);
        stats.put("total", saramin + jobplanet + linkareer + jobkorea + wanted);

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
        jobPostingRepository.findById(id).ifPresent(job ->
                clearCrawledCacheByUrl(job.getUrl(), job.getSource().name()));
        aiAnalysisResultRepository.deleteByJobPostingId(id);
        notificationHistoryRepository.deleteByJobPostingId(id);
        jobApplicationRepository.deleteByJobPostingId(id);
        bookmarkRepository.deleteByJobPostingId(id);
        jobPostingRepository.deleteById(id);
        evictJobCaches();
    }

    @Override
    @Transactional
    public void deleteJobs(java.util.List<Long> ids) {
        jobPostingRepository.findAllById(ids).forEach(job ->
                clearCrawledCacheByUrl(job.getUrl(), job.getSource().name()));
        aiAnalysisResultRepository.deleteByJobPostingIdIn(ids);
        notificationHistoryRepository.deleteByJobPostingIdIn(ids);
        jobApplicationRepository.deleteByJobPostingIdIn(ids);
        bookmarkRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllByIdInBatch(ids);
        evictJobCaches();
    }

    @Override
    @Transactional
    public void deleteAllJobs() {
        aiAnalysisResultRepository.deleteAllInBatch();
        notificationHistoryRepository.deleteAllInBatch();
        jobApplicationRepository.deleteAllInBatch();
        bookmarkRepository.deleteAllInBatch();
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
        bookmarkRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllInBatch(jobs);
        clearCrawledCacheBySite(site.toUpperCase());
        evictJobCaches();
        return ids.size();
    }

    @Override
    @Transactional
    public int deleteEmptyPostings() {
        java.util.List<JobPosting> emptyPostings = jobPostingRepository.findEmptyPostings();
        if (emptyPostings.isEmpty()) return 0;

        java.util.List<Long> ids = emptyPostings.stream().map(JobPosting::getId).toList();
        emptyPostings.forEach(job -> clearCrawledCacheByUrl(job.getUrl(), job.getSource().name()));
        aiAnalysisResultRepository.deleteByJobPostingIdIn(ids);
        notificationHistoryRepository.deleteByJobPostingIdIn(ids);
        jobApplicationRepository.deleteByJobPostingIdIn(ids);
        bookmarkRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllInBatch(emptyPostings);
        evictJobCaches();
        return ids.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDetailedStats() {
        Map<String, Object> result = new HashMap<>();

        // 경력별 (신입/경력/경력무관으로 정규화)
        Map<String, Long> careerStats = new java.util.LinkedHashMap<>();
        long rookie = 0, experienced = 0, noMatter = 0, other = 0;
        for (Object[] row : jobPostingRepository.countByCareerGroup()) {
            String career = row[0] != null ? ((String) row[0]).trim() : "";
            long count = (Long) row[1];
            if (career.contains("신입") && !career.contains("경력")) rookie += count;
            else if (career.contains("경력무관") || career.isEmpty()) noMatter += count;
            else if (career.contains("경력") || career.matches(".*\\d+년.*")) experienced += count;
            else other += count;
        }
        careerStats.put("신입", rookie);
        careerStats.put("경력", experienced);
        careerStats.put("경력무관", noMatter);
        if (other > 0) careerStats.put("기타", other);
        result.put("career", careerStats);

        // 학력별
        Map<String, Long> educationStats = new java.util.LinkedHashMap<>();
        for (Object[] row : jobPostingRepository.countByEducationGroup()) {
            String edu = row[0] != null ? ((String) row[0]).trim() : "미표기";
            if (edu.isEmpty()) edu = "미표기";
            educationStats.put(edu, (Long) row[1]);
        }
        result.put("education", educationStats);

        // 지역별 (시/구 단위로 정규화 → 상위 10개)
        Map<String, Long> locationStats = new java.util.LinkedHashMap<>();
        Map<String, Long> tempLocation = new java.util.LinkedHashMap<>();
        for (Object[] row : jobPostingRepository.countByLocationGroup()) {
            String loc = row[0] != null ? ((String) row[0]).trim() : "미표기";
            if (loc.isEmpty()) loc = "미표기";
            // "서울 강남구" → "서울" 로 광역 단위 집계
            String region = loc.split(" ")[0];
            tempLocation.merge(region, (Long) row[1], Long::sum);
        }
        tempLocation.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> locationStats.put(e.getKey(), e.getValue()));
        result.put("location", locationStats);

        return result;
    }

    /** 공고 변경 시 관련 캐시 무효화 */
    public void evictJobCaches() {
        redisTemplate.delete(STATS_CACHE_KEY);
        // 목록 캐시 전체 삭제
        try {
            Set<String> listKeys = redisTemplate.keys(LIST_CACHE_PREFIX + "*");
            if (listKeys != null && !listKeys.isEmpty()) redisTemplate.delete(listKeys);
            Set<String> detailKeys = redisTemplate.keys(DETAIL_CACHE_PREFIX + "*");
            if (detailKeys != null && !detailKeys.isEmpty()) redisTemplate.delete(detailKeys);
        } catch (Exception e) {
            log.debug("[캐시 무효화 실패] {}", e.getMessage());
        }
        log.debug("[캐시 무효화] 공고 통계/목록/상세 캐시 삭제");
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

    private void clearCrawledCacheBySite(String site) {
        try {
            Set<String> keys = redisTemplate.keys("crawled:job:" + site + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Redis [{}] 크롤링 캐시 {} 건 삭제", site, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis 사이트별 캐시 삭제 실패: {}", e.getMessage());
        }
    }

    private void clearCrawledCacheByUrl(String url, String site) {
        if (url == null || site == null) return;
        try {
            String redisKey = "crawled:job:" + site + ":" + url.hashCode();
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.debug("Redis URL 캐시 삭제 실패: {}", e.getMessage());
        }
    }
}
