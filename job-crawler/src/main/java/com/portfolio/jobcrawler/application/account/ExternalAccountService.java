package com.portfolio.jobcrawler.application.account;

import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;

import java.util.List;

/**
 * 외부 계정 Application Service 인터페이스.
 */
public interface ExternalAccountService {
    List<ExternalAccount> getMyAccounts(Long userId);

    ExternalAccount registerAccount(Long userId, SourceSite site, String accountId, String password);

    ExternalAccount updateAccount(Long userId, Long accountId, String newAccountId, String newPassword);

    void deleteAccount(Long userId, Long accountId);

    /** Playwright 팝업 브라우저로 소셜 로그인 후 쿠키 자동 추출 */
    boolean openLoginPopup(Long userId, SourceSite site);
}
