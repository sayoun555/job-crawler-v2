package com.portfolio.jobcrawler.domain.notification.repository;

import com.portfolio.jobcrawler.domain.notification.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);
}
