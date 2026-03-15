package com.portfolio.jobcrawler.infrastructure.crawler.config;

import com.portfolio.jobcrawler.infrastructure.crawler.PlaywrightManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfig {

    @Bean(destroyMethod = "close")
    public PlaywrightManager playwrightManager() {
        PlaywrightManager manager = new PlaywrightManager();
        manager.init();
        return manager;
    }
}
