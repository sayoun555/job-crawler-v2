package com.portfolio.jobcrawler.application.notification;

/**
 * 알림 Application Service 인터페이스.
 */
public interface NotificationService {

    /** 새 공고 알림 (희망 직무 매칭된 모든 사용자에게) */
    void notifyNewJobPostings();

    /** 현재 시간에 알림을 받아야 하는 유저에게만 새 공고 알림 발송 */
    void notifyScheduledUsers(int currentHour);

    /** 특정 사용자에게 알림 */
    void notifyUser(Long userId, String title, String message, String linkUrl);

    /** 지원 결과 알림 */
    void notifyApplicationResult(Long userId, Long applicationId, boolean success, String reason);
}
