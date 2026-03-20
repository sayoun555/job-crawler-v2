package com.portfolio.jobcrawler.domain.account.entity;

import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.account.vo.AuthType;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "external_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceSite site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthType authType = AuthType.CREDENTIAL;

    private String accountId;

    @JsonIgnore
    private String encryptedPassword;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String sessionCookies;

    /** 세션 쿠키 중 가장 빨리 만료되는 시각 (Unix epoch seconds → Instant) */
    private Instant sessionExpiresAt;

    /** 이력서 마지막 동기화 시각 */
    private Instant resumeSyncedAt;

    /** 이력서 동기화 상태 (SUCCESS / PARTIAL_SUCCESS / FAILED / null) */
    @Column(length = 20)
    private String resumeSyncStatus;

    /** 이력서 동기화 결과 메시지 */
    @Column(length = 500)
    private String resumeSyncMessage;

    @Builder
    public ExternalAccount(User user, SourceSite site, AuthType authType,
                           String accountId, String encryptedPassword) {
        this.user = user;
        this.site = site;
        this.authType = authType != null ? authType : AuthType.CREDENTIAL;
        this.accountId = accountId;
        this.encryptedPassword = encryptedPassword;
    }

    /** 쿠키 세션 전용 계정 생성 (소셜 로그인용) */
    public static ExternalAccount createCookieSession(User user, SourceSite site, String cookies) {
        ExternalAccount account = new ExternalAccount();
        account.user = user;
        account.site = site;
        account.authType = AuthType.COOKIE_SESSION;
        account.sessionCookies = cookies;
        return account;
    }

    // === 도메인 비즈니스 로직 ===
    public void updateSessionCookies(String cookies) {
        this.sessionCookies = cookies;
    }

    public void updateSessionWithExpiry(String cookies, Instant expiresAt) {
        this.sessionCookies = cookies;
        this.sessionExpiresAt = expiresAt;
    }

    public void invalidateSession() {
        this.sessionCookies = null;
        this.sessionExpiresAt = null;
    }

    public void updateResumeSyncStatus(String status, String message, Instant syncedAt) {
        this.resumeSyncStatus = status;
        this.resumeSyncMessage = message;
        this.resumeSyncedAt = syncedAt;
    }

    public void updateCredentials(String accountId, String encryptedPassword) {
        this.accountId = accountId;
        this.encryptedPassword = encryptedPassword;
    }

    @JsonProperty("sessionValid")
    public boolean hasValidSession() {
        if (this.sessionCookies == null || this.sessionCookies.isBlank()) {
            return false;
        }
        if (this.sessionExpiresAt != null && Instant.now().isAfter(this.sessionExpiresAt)) {
            return false;
        }
        return true;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
