package com.portfolio.jobcrawler.domain.jobpreference.entity;

import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceSite site;

    @Column(nullable = false)
    private String categoryCode;

    @Column(nullable = false)
    private String categoryName;

    private boolean enabled = true;

    @Builder
    public JobPreference(User user, SourceSite site, String categoryCode, String categoryName) {
        this.user = user;
        this.site = site;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
    }

    // === 도메인 비즈니스 로직 ===

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
