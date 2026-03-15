package com.portfolio.jobcrawler.application.jobpreference;

import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;

import java.util.List;

/**
 * 희망 직무 Application Service 인터페이스.
 */
public interface JobPreferenceService {
    List<JobPreference> getMyPreferences(Long userId);

    List<JobPreference> getMyPreferences(Long userId, SourceSite site);

    JobPreference addPreference(Long userId, SourceSite site, String categoryCode, String categoryName);

    void removePreference(Long userId, Long preferenceId);

    void togglePreference(Long userId, Long preferenceId, boolean enabled);

    void disableAllBySite(Long userId, SourceSite site);
}
