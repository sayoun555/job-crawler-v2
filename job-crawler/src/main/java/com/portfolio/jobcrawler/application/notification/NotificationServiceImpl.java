package com.portfolio.jobcrawler.application.notification;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
import com.portfolio.jobcrawler.domain.jobpreference.repository.JobPreferenceRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.infrastructure.notification.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final UserRepository userRepository;
    private final JobPreferenceRepository jobPreferenceRepository;
    private final JobPostingRepository jobPostingRepository;
    private final List<NotificationSender> senders;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String NOTIFIED_PREFIX = "notified:job:";
    private static final Duration NOTIFIED_TTL = Duration.ofDays(7);

    @Value("${app.base-url:https://job.eekky.com}")
    private String baseUrl;

    @Override
    public void notifyNewJobPostings() {
        List<User> users = userRepository.findByNotificationEnabledTrue();

        for (User user : users) {
            if (!user.hasDiscordWebhook())
                continue;

            List<JobPreference> prefs = jobPreferenceRepository.findByUserIdAndEnabledTrue(user.getId());
            if (prefs.isEmpty())
                continue;

            // 최근 크롤링된 공고 중 매칭 확인 (최신 50개)
            var recentJobs = jobPostingRepository.searchJobs(null, null, null,
                    null, null, null, null,
                    PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));

            for (var job : recentJobs.getContent()) {
                // 중복 알림 방지 (유저별)
                String redisKey = NOTIFIED_PREFIX + user.getId() + ":" + job.getId();
                if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) continue;

                if (matchesPreference(job, prefs)) {
                    String deepLink = baseUrl + "/jobs/" + job.getId();
                    notifyUser(user.getId(),
                            "[새 공고] " + job.getTitle(),
                            job.getCompany() + " | " + nullSafe(job.getLocation())
                                    + "\n기술: " + (job.getTechStack() != null ? job.getTechStack().toString() : ""),
                            deepLink);
                    // 알림 보낸 공고 기록
                    redisTemplate.opsForValue().set(redisKey, "1", NOTIFIED_TTL);
                }
            }
        }
    }

    @Override
    public void notifyUser(Long userId, String title, String message, String linkUrl) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.hasDiscordWebhook())
            return;

        for (NotificationSender sender : senders) {
            sender.send(user.getDiscordWebhookUrl(), title, message, linkUrl);
        }
    }

    @Override
    public void notifyApplicationResult(Long userId, Long applicationId, boolean success, String reason) {
        String title = success ? "✅ 지원 성공" : "❌ 지원 실패";
        String msg = success ? "입사 지원이 성공적으로 완료되었습니다."
                : "지원 실패: " + reason + "\n재시도 버튼을 눌러 다시 시도하세요.";
        String link = baseUrl + "/applications/" + applicationId;
        notifyUser(userId, title, msg, link);
    }

    private boolean matchesPreference(JobPosting job, List<JobPreference> prefs) {
        // 기술 스택 또는 카테고리 매칭 확인
        String title = job.getTitle().toLowerCase();
        String tech = (job.getTechStack() != null ? job.getTechStack().toString() : "").toLowerCase();
        return prefs.stream().anyMatch(p -> title.contains(p.getCategoryName().toLowerCase()) ||
                tech.contains(p.getCategoryName().toLowerCase()));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
