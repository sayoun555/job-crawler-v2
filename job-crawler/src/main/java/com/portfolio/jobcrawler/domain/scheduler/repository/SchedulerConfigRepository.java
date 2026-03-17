package com.portfolio.jobcrawler.domain.scheduler.repository;

import com.portfolio.jobcrawler.domain.scheduler.entity.SchedulerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerConfigRepository extends JpaRepository<SchedulerConfig, Long> {
}
