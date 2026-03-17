package com.portfolio.jobcrawler.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "jobPostingId"}),
        indexes = @Index(columnList = "userId"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long jobPostingId;

    @Column(nullable = false)
    private LocalDateTime notifiedAt;

    private NotificationHistory(Long userId, Long jobPostingId) {
        this.userId = userId;
        this.jobPostingId = jobPostingId;
        this.notifiedAt = LocalDateTime.now();
    }

    public static NotificationHistory of(Long userId, Long jobPostingId) {
        return new NotificationHistory(userId, jobPostingId);
    }
}
