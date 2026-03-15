package com.portfolio.jobcrawler.application.jobpreference;

import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
import com.portfolio.jobcrawler.domain.jobpreference.repository.JobPreferenceRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPreferenceServiceImpl implements JobPreferenceService {

    private final JobPreferenceRepository jobPreferenceRepository;
    private final UserRepository userRepository;

    @Override
    public List<JobPreference> getMyPreferences(Long userId) {
        return jobPreferenceRepository.findByUserId(userId);
    }

    @Override
    public List<JobPreference> getMyPreferences(Long userId, SourceSite site) {
        return jobPreferenceRepository.findByUserIdAndSite(userId, site);
    }

    @Override
    @Transactional
    public JobPreference addPreference(Long userId, SourceSite site,
            String categoryCode, String categoryName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return jobPreferenceRepository.save(
                JobPreference.builder()
                        .user(user).site(site)
                        .categoryCode(categoryCode).categoryName(categoryName)
                        .build());
    }

    @Override
    @Transactional
    public void removePreference(Long userId, Long preferenceId) {
        JobPreference pref = jobPreferenceRepository.findById(preferenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        if (!pref.isOwnedBy(userId))
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        jobPreferenceRepository.delete(pref);
    }

    @Override
    @Transactional
    public void togglePreference(Long userId, Long preferenceId, boolean enabled) {
        JobPreference pref = jobPreferenceRepository.findById(preferenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        if (!pref.isOwnedBy(userId))
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        if (enabled)
            pref.enable();
        else
            pref.disable(); // 도메인 로직
    }

    @Override
    @Transactional
    public void disableAllBySite(Long userId, SourceSite site) {
        List<JobPreference> prefs = jobPreferenceRepository.findByUserIdAndSite(userId, site);
        prefs.forEach(JobPreference::disable); // 도메인 로직
    }
}
