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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

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
        log.info("[스케줄러] 초기화 - 크롤링: {}, {} / 마감 정리: 매시간", crawlCron1, crawlCron2);
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

    /** 현재 스케줄 조회 */
    public Map<String, Object> getCurrentSchedule() {
        return Map.of(
                "schedule1", crawlCron1,
                "schedule2", crawlCron2,
                "maxPages", maxPages
        );
    }

    private void scheduleCrawl(String taskId, String cron) {
        scheduleTask(taskId, cron, () -> {
            log.info("=== [스케줄] 자동 크롤링 (전체) - 최대 {} 페이지 ===", maxPages);
            int saved = crawlerService.crawlAll(null, null, maxPages);
            // 크롤링 후 희망 직무 매칭 알림 발송
            if (saved > 0) {
                try {
                    notificationService.notifyNewJobPostings();
                    log.info("[스케줄] 새 공고 알림 발송 완료");
                } catch (Exception e) {
                    log.warn("[스케줄] 알림 발송 실패: {}", e.getMessage());
                }
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

    @Transactional
    public void closeExpiredJobs() {
        int closed = jobPostingRepository.closeExpired(LocalDate.now());
        if (closed > 0) {
            log.info("[스케줄러] 마감 공고 {} 건 자동 종료", closed);
        }
    }
}
