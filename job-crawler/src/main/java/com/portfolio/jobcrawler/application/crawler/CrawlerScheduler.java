package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.application.notification.NotificationService;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.scheduler.entity.SchedulerConfig;
import com.portfolio.jobcrawler.domain.scheduler.repository.SchedulerConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 동적 크롤링 스케줄러.
 * - 관리자가 cron 표현식을 변경 가능
 * - 매시간 마감 공고 자동 close
 * - 설정(enabled, cron, maxPages)은 DB에 영속화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    private final NotificationService notificationService;
    private final JobPostingRepository jobPostingRepository;
    private final TaskScheduler taskScheduler;
    private final SchedulerConfigRepository schedulerConfigRepository;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean crawling = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        SchedulerConfig config = loadOrCreateConfig();
        scheduleCrawl("crawl1", config.getCrawlCron1());
        scheduleCrawl("crawl2", config.getCrawlCron2());
        scheduleTask("expiredClose", "0 0 * * * *", this::closeExpiredJobs);
        scheduleTask("userNotification", "0 0 * * * *", this::sendScheduledNotifications);
        log.info("[스케줄러] 초기화 - 크롤링: {}, {} / enabled: {} / 마감 정리: 매시간 / 유저 알림: 매시간",
                config.getCrawlCron1(), config.getCrawlCron2(), config.isEnabled());
    }

    @Transactional
    public void updateSchedule(String cron1, String cron2, Integer newMaxPages) {
        SchedulerConfig config = loadOrCreateConfig();
        config.updateSchedule(cron1, cron2, newMaxPages);
        schedulerConfigRepository.save(config);

        if (cron1 != null && !cron1.isBlank()) {
            scheduleCrawl("crawl1", cron1);
        }
        if (cron2 != null && !cron2.isBlank()) {
            scheduleCrawl("crawl2", cron2);
        }
        log.info("[스케줄러] 설정 변경 - 1차: {}, 2차: {}, 최대페이지: {}",
                config.getCrawlCron1(), config.getCrawlCron2(), config.getMaxPages());
    }

    @Transactional
    public boolean toggleEnabled() {
        SchedulerConfig config = loadOrCreateConfig();
        boolean newState = config.toggleEnabled();
        schedulerConfigRepository.save(config);
        log.info("[스케줄러] 자동 크롤링 {}", newState ? "활성화" : "비활성화");
        return newState;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentSchedule() {
        SchedulerConfig config = loadOrCreateConfig();
        return Map.of(
                "schedule1", config.getCrawlCron1(),
                "schedule2", config.getCrawlCron2(),
                "maxPages", config.getMaxPages(),
                "enabled", config.isEnabled()
        );
    }

    private void scheduleCrawl(String taskId, String cron) {
        scheduleTask(taskId, cron, () -> {
            SchedulerConfig config = loadOrCreateConfig();
            if (!config.isEnabled()) {
                log.info("=== [스케줄] 자동 크롤링 비활성화 상태 - 건너뜀 ===");
                return;
            }
            if (!crawling.compareAndSet(false, true)) {
                log.warn("=== [스케줄] 이전 크롤링이 아직 진행 중입니다. 이번 스케줄은 건너뜁니다. ===");
                return;
            }
            try {
                log.info("=== [스케줄] 자동 크롤링 (전체) - 최대 {} 페이지 ===", config.getMaxPages());
                int saved = crawlerService.crawlAll(null, null, config.getMaxPages());
                log.info("[스케줄] 크롤링 완료 - 신규 {} 건 (알림은 유저별 설정 시간에 발송)", saved);
            } finally {
                crawling.set(false);
            }
        });
    }

    private void scheduleTask(String taskId, String cron, Runnable task) {
        ScheduledFuture<?> existing = scheduledTasks.get(taskId);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        scheduledTasks.put(taskId, future);
    }

    private void sendScheduledNotifications() {
        int currentHour = LocalTime.now(ZoneId.of("Asia/Seoul")).getHour();
        try {
            notificationService.notifyScheduledUsers(currentHour);
        } catch (Exception e) {
            log.warn("[스케줄러] 유저 알림 발송 실패: {}", e.getMessage());
        }
    }

    @Transactional
    public void closeExpiredJobs() {
        int closed = jobPostingRepository.closeExpired(LocalDate.now());
        if (closed > 0) {
            log.info("[스케줄러] 마감 공고 {} 건 자동 종료", closed);
        }
    }

    private SchedulerConfig loadOrCreateConfig() {
        return schedulerConfigRepository.findById(1L)
                .orElseGet(() -> schedulerConfigRepository.save(SchedulerConfig.createDefault()));
    }
}
