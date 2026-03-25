package com.portfolio.jobcrawler.application.user;

import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;

/**
 * 사용자/프로필 Application Service 인터페이스.
 */
public interface UserService {
    User getUser(Long userId);

    User updateNickname(Long userId, String nickname);

    User updateDiscordWebhook(Long userId, String webhookUrl);

    User toggleNotification(Long userId, boolean enabled);

    User updateNotificationHours(Long userId, String hours);

    UserProfile getProfile(Long userId);

    UserProfile updateProfile(Long userId, String education, String career,
            String certifications, String techStack, String strengths);

    java.util.List<User> listAllUsers();

    void approveUser(Long userId);

    void suspendUser(Long userId);
}
