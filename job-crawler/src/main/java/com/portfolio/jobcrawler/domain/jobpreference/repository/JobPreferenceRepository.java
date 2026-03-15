package com.portfolio.jobcrawler.domain.jobpreference.repository;

import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPreferenceRepository extends JpaRepository<JobPreference, Long> {
    List<JobPreference> findByUserId(Long userId);

    List<JobPreference> findByUserIdAndSite(Long userId, SourceSite site);

    List<JobPreference> findByUserIdAndEnabledTrue(Long userId);

    void deleteByUserIdAndSite(Long userId, SourceSite site);
}
