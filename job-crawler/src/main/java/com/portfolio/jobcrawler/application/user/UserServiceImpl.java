package com.portfolio.jobcrawler.application.user;

import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.domain.user.repository.UserProfileRepository;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public User updateNickname(Long userId, String nickname) {
        User user = getUser(userId);
        user.updateNickname(nickname);
        return user;
    }

    @Override
    @Transactional
    public User updateDiscordWebhook(Long userId, String webhookUrl) {
        User user = getUser(userId);
        user.updateDiscordWebhook(webhookUrl);
        return user;
    }

    @Override
    @Transactional
    public User toggleNotification(Long userId, boolean enabled) {
        User user = getUser(userId);
        user.toggleNotification(enabled);
        return user;
    }

    @Override
    @Transactional
    public User updateNotificationHours(Long userId, String hours) {
        User user = getUser(userId);
        user.updateNotificationHours(hours);
        return user;
    }

    @Override
    public UserProfile getProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROFILE_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserProfile updateProfile(Long userId, String education, String career,
            String certifications, String techStack, String strengths) {
        UserProfile profile = getProfile(userId);
        profile.updateBasicInfo(education, career, certifications, techStack, strengths);
        return profile;
    }
}
