package com.portfolio.jobcrawler.application.jobposting;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * 채용 공고 Application Service 인터페이스.
 */
public interface JobPostingService {
    Page<JobPosting> searchJobs(SourceSite source, String keyword, String jobCategory,
                               String career, String education, String location, String applicationMethod,
                               Pageable pageable);

    JobPosting getJobPosting(Long id);

    Map<String, Long> getStats();

    void deleteJob(Long id);

    void deleteJobs(java.util.List<Long> ids);

    void deleteAllJobs();

    int deleteJobsBySite(String site);
}
