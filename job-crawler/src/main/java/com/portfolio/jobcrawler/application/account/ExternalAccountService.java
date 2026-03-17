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

    /** 일회용 로그인: ID/PW로 한 번 로그인 후 쿠키만 저장, 비밀번호는 폐기 */
    boolean onetimeLogin(Long userId, SourceSite site, String loginId, String password);

    /** 브라우저 확장에서 전달받은 쿠키로 세션을 등록한다 */
    ExternalAccount registerCookieSession(Long userId, SourceSite site, String cookiesJson);

    /** 세션 만료 시 DB + Redis 세션을 모두 무효화한다 */
    void invalidateSession(Long userId, SourceSite site);
}
