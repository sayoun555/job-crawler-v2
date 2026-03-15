package com.portfolio.jobcrawler.domain.account.repository;

import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {
    List<ExternalAccount> findByUserId(Long userId);

    Optional<ExternalAccount> findByUserIdAndSite(Long userId, SourceSite site);
}
