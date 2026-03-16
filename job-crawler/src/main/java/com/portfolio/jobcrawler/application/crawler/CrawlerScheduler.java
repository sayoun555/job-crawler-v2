package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.application.notification.NotificationService;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    private final NotificationService notificationService;
    private final JobPostingRepository jobPostingRepository;
    private final TaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean crawling = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    // 기본 스케줄: 평일 오전 9시, 오후 2시
    private String crawlCron1 = "0 0 9 * * MON-FRI";
    private String crawlCron2 = "0 0 14 * * MON-FRI";

    // 최대 수집 페이지 수 (기본 50)
    private int maxPages = 50;

    @PostConstruct
    public void init() {
        scheduleCrawl("crawl1", crawlCron1);
        scheduleCrawl("crawl2", crawlCron2);
        // 매시간 마감 공고 정리
        scheduleTask("expiredClose", "0 0 * * * *", this::closeExpiredJobs);
        // 매시간 유저별 알림 시간 체크 후 발송
        scheduleTask("userNotification", "0 0 * * * *", this::sendScheduledNotifications);
        log.info("[스케줄러] 초기화 - 크롤링: {}, {} / 마감 정리: 매시간 / 유저 알림: 매시간", crawlCron1, crawlCron2);
    }

    /** 크롤링 스케줄 및 설정 변경 (관리자 API 호출용) */
    public void updateSchedule(String cron1, String cron2, Integer newMaxPages) {
        if (cron1 != null && !cron1.isBlank()) {
            this.crawlCron1 = cron1;
            scheduleCrawl("crawl1", cron1);
        }
        if (cron2 != null && !cron2.isBlank()) {
            this.crawlCron2 = cron2;
            scheduleCrawl("crawl2", cron2);
        }
        if (newMaxPages != null && newMaxPages > 0) {
            this.maxPages = newMaxPages;
        }
        log.info("[스케줄러] 설정 변경 - 1차: {}, 2차: {}, 최대페이지: {}", crawlCron1, crawlCron2, maxPages);
    }

    /** 스케줄 on/off 토글 */
    public boolean toggleEnabled() {
        boolean newState = !enabled.get();
        enabled.set(newState);
        log.info("[스케줄러] 자동 크롤링 {}", newState ? "활성화" : "비활성화");
        return newState;
    }

    /** 현재 스케줄 조회 */
    public Map<String, Object> getCurrentSchedule() {
        return Map.of(
                "schedule1", crawlCron1,
                "schedule2", crawlCron2,
                "maxPages", maxPages,
                "enabled", enabled.get()
        );
    }

    private void scheduleCrawl(String taskId, String cron) {
        scheduleTask(taskId, cron, () -> {
            if (!enabled.get()) {
                log.info("=== [스케줄] 자동 크롤링 비활성화 상태 - 건너뜀 ===");
                return;
            }
            if (!crawling.compareAndSet(false, true)) {
                log.warn("=== [스케줄] 이전 크롤링이 아직 진행 중입니다. 이번 스케줄은 건너뜁니다. ===");
                return;
            }
            try {
                log.info("=== [스케줄] 자동 크롤링 (전체) - 최대 {} 페이지 ===", maxPages);
                int saved = crawlerService.crawlAll(null, null, maxPages);
                log.info("[스케줄] 크롤링 완료 - 신규 {} 건 (알림은 유저별 설정 시간에 발송)", saved);
            } finally {
                crawling.set(false);
            }
        });
    }

    private void scheduleTask(String taskId, String cron, Runnable task) {
        // 기존 스케줄 취소
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
}
