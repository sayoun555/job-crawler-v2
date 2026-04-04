package com.portfolio.jobcrawler.application.notification;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.jobpreference.entity.JobPreference;
import com.portfolio.jobcrawler.domain.jobpreference.repository.JobPreferenceRepository;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.domain.notification.entity.NotificationHistory;
import com.portfolio.jobcrawler.domain.notification.repository.NotificationHistoryRepository;
import com.portfolio.jobcrawler.infrastructure.notification.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final UserRepository userRepository;
    private final JobPreferenceRepository jobPreferenceRepository;
    private final JobPostingRepository jobPostingRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final List<NotificationSender> senders;

    @Value("${app.base-url:https://job.eekky.com}")
    private String baseUrl;

    @Override
    public void notifyNewJobPostings() {
        List<User> users = userRepository.findByNotificationEnabledTrue();
        sendMatchingNotifications(users);
    }

    @Override
    public void notifyScheduledUsers(int currentHour) {
        List<User> users = userRepository.findByNotificationEnabledTrue().stream()
                .filter(u -> u.shouldNotifyAt(currentHour))
                .toList();

        if (users.isEmpty()) {
            log.debug("[알림] {}시에 알림 대상 유저 없음", currentHour);
            return;
        }

        log.info("[알림] {}시 알림 발송 - 대상 유저 {} 명", currentHour, users.size());
        sendMatchingNotifications(users);
    }

    private void sendMatchingNotifications(List<User> users) {
        var recentJobs = jobPostingRepository.searchJobs(null, null, null,
                null, null, null, null, null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (User user : users) {
            if (!user.hasDiscordWebhook()) continue;

            List<JobPreference> prefs = jobPreferenceRepository.findByUserIdAndEnabledTrue(user.getId());
            if (prefs.isEmpty()) continue;

            for (var job : recentJobs.getContent()) {
                if (notificationHistoryRepository.existsByUserIdAndJobPostingId(user.getId(), job.getId())) continue;

                if (matchesPreference(job, prefs)) {
                    String deepLink = baseUrl + "/jobs/" + job.getId();
                    notifyUser(user.getId(),
                            "[새 공고] " + job.getTitle(),
                            job.getCompany() + " | " + nullSafe(job.getLocation())
                                    + "\n기술: " + (job.getTechStack() != null ? job.getTechStack().toString() : ""),
                            deepLink);
                    notificationHistoryRepository.save(NotificationHistory.of(user.getId(), job.getId()));
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
