package com.portfolio.jobcrawler.domain.scheduler.entity;

import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "scheduler_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerConfig extends BaseTimeEntity {

    private static final String DEFAULT_CRON_1 = "0 0 9 * * MON-FRI";
    private static final String DEFAULT_CRON_2 = "0 0 14 * * MON-FRI";
    private static final int DEFAULT_MAX_PAGES = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String crawlCron1;

    @Column(nullable = false)
    private String crawlCron2;

    @Column(nullable = false)
    private int maxPages;

    @Column(nullable = false)
    private boolean enabled;

    private SchedulerConfig(String crawlCron1, String crawlCron2, int maxPages, boolean enabled) {
        this.crawlCron1 = crawlCron1;
        this.crawlCron2 = crawlCron2;
        this.maxPages = maxPages;
        this.enabled = enabled;
    }

    public static SchedulerConfig createDefault() {
        return new SchedulerConfig(DEFAULT_CRON_1, DEFAULT_CRON_2, DEFAULT_MAX_PAGES, true);
    }

    public void updateSchedule(String cron1, String cron2, Integer newMaxPages) {
        if (cron1 != null && !cron1.isBlank()) {
            this.crawlCron1 = cron1;
        }
        if (cron2 != null && !cron2.isBlank()) {
            this.crawlCron2 = cron2;
        }
        if (newMaxPages != null && newMaxPages > 0) {
            this.maxPages = newMaxPages;
        }
    }

    public boolean toggleEnabled() {
        this.enabled = !this.enabled;
        return this.enabled;
    }
}
