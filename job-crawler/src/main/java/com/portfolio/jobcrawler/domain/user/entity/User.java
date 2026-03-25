package com.portfolio.jobcrawler.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.user.vo.Role;
import com.portfolio.jobcrawler.domain.user.vo.UserStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 500)
    private String discordWebhookUrl;

    @Column(nullable = false)
    private boolean notificationEnabled = true;

    @Column(length = 50)
    private String notificationHours = "9,18";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status = UserStatus.PENDING;

    @Builder
    public User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = Role.USER;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isPending() {
        return this.status == UserStatus.PENDING;
    }

    public void approve() {
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
        this.status = UserStatus.ACTIVE;
    }

    // === 도메인 비즈니스 로직 ===

    public void updateDiscordWebhook(String webhookUrl) {
        this.discordWebhookUrl = webhookUrl;
    }

    public void toggleNotification(boolean enabled) {
        this.notificationEnabled = enabled;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public boolean hasDiscordWebhook() {
        return this.discordWebhookUrl != null && !this.discordWebhookUrl.isBlank();
    }

    public void updateNotificationHours(String hours) {
        this.notificationHours = hours;
    }

    public boolean shouldNotifyAt(int hour) {
        if (!this.notificationEnabled || this.notificationHours == null || this.notificationHours.isBlank()) {
            return false;
        }
        for (String h : this.notificationHours.split(",")) {
            try {
                if (Integer.parseInt(h.trim()) == hour) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
