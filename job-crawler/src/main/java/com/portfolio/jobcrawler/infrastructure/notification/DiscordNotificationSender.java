package com.portfolio.jobcrawler.infrastructure.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Discord Webhook 알림 발송 구현체.
 * Embed 메시지 형식 사용.
 */
@Slf4j
@Component
public class DiscordNotificationSender implements NotificationSender {

    @Value("${app.base-url:https://job.eekky.com}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getChannelName() {
        return "DISCORD";
    }

    @Override
    public void send(String webhookUrl, String title, String message, String linkUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Discord Embed 메시지
            Map<String, Object> embed = Map.of(
                    "title", title,
                    "description", message,
                    "color", 3447003, // 파란색
                    "url", linkUrl != null ? linkUrl : baseUrl,
                    "footer", Map.of("text", "이끼잡 | 맞춤형 채용 알림"));

            Map<String, Object> body = Map.of(
                    "username", "이끼잡 알림봇",
                    "embeds", List.of(embed));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
            log.info("[Discord] 알림 발송 성공: {}", title);
        } catch (Exception e) {
            log.error("[Discord] 알림 발송 실패: {}", e.getMessage());
        }
    }
}
