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

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingServiceImpl implements JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

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
        long saramin = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.SARAMIN);
        long jobplanet = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBPLANET);
        long linkareer = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.LINKAREER);
        long jobkorea = jobPostingRepository.countBySourceAndClosedFalse(SourceSite.JOBKOREA);
        return Map.of("saramin", saramin, "jobplanet", jobplanet, "linkareer", linkareer, "jobkorea", jobkorea, "total", saramin + jobplanet + linkareer + jobkorea);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        jobApplicationRepository.deleteByJobPostingId(id);
        jobPostingRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteJobs(java.util.List<Long> ids) {
        jobApplicationRepository.deleteByJobPostingIdIn(ids);
        jobPostingRepository.deleteAllByIdInBatch(ids);
    }

    @Override
    @Transactional
    public void deleteAllJobs() {
        jobApplicationRepository.deleteAllInBatch();
        jobPostingRepository.deleteAllInBatch();
        clearCrawledCache();
    }

    @Override
    @Transactional
    public int deleteJobsBySite(String site) {
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        java.util.List<com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting> jobs = jobPostingRepository.findBySource(sourceSite);
        if (jobs.isEmpty()) return 0;
        java.util.List<Long> ids = jobs.stream().map(j -> j.getId()).toList();
        // 연관된 지원 내역 먼저 삭제
        for (Long jobId : ids) {
            jobApplicationRepository.deleteByJobPostingId(jobId);
        }
        jobPostingRepository.deleteAllInBatch(jobs);
        return ids.size();
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
