package com.portfolio.jobcrawler.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.user.vo.Role;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

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

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
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
}
