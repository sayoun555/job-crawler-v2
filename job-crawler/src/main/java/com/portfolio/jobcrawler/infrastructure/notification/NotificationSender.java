package com.portfolio.jobcrawler.infrastructure.notification;

/**
 * 알림 발송 인터페이스 (Strategy Pattern).
 * Discord, Email 등 다양한 채널 구현 가능.
 */
public interface NotificationSender {

    void send(String webhookUrl, String title, String message, String linkUrl);

    String getChannelName();
}
