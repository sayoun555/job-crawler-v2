package com.portfolio.jobcrawler.application.account;

import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.account.repository.ExternalAccountRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.global.util.AesEncryptor;
import com.portfolio.jobcrawler.infrastructure.autoapply.AuthSessionManager;
import com.portfolio.jobcrawler.infrastructure.autoapply.AutoApplyRobot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalAccountServiceImpl implements ExternalAccountService {

    private final ExternalAccountRepository externalAccountRepository;
    private final UserRepository userRepository;
    private final AutoApplyRobot autoApplyRobot;
    private final AuthSessionManager authSessionManager;
    private final AesEncryptor aesEncryptor;

    @Override
    public List<ExternalAccount> getMyAccounts(Long userId) {
        return externalAccountRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public ExternalAccount registerAccount(Long userId, SourceSite site,
            String accountId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return externalAccountRepository.findByUserIdAndSite(userId, site)
                .map(existing -> {
                    existing.updateCredentials(accountId, aesEncryptor.encrypt(password));
                    return existing;
                })
                .orElseGet(() -> externalAccountRepository.save(
                        ExternalAccount.builder()
                                .user(user).site(site)
                                .accountId(accountId)
                                .encryptedPassword(aesEncryptor.encrypt(password))
                                .build()));
    }

    @Override
    @Transactional
    public ExternalAccount updateAccount(Long userId, Long accountId,
            String newAccountId, String newPassword) {
        ExternalAccount account = externalAccountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));
        if (!account.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        account.updateCredentials(newAccountId, aesEncryptor.encrypt(newPassword));
        return account;
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId, Long accountId) {
        ExternalAccount account = externalAccountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));
        if (!account.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        externalAccountRepository.delete(account);
    }

    @Override
    @Transactional
    public boolean openLoginPopup(Long userId, SourceSite site) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String cookies = autoApplyRobot.openLoginPopup(userId, site.name());
        if (cookies == null || cookies.isBlank() || cookies.equals("[]")) {
            return false;
        }

        externalAccountRepository.findByUserIdAndSite(userId, site)
                .ifPresentOrElse(
                        existing -> existing.updateSessionCookies(cookies),
                        () -> externalAccountRepository.save(
                                ExternalAccount.createCookieSession(user, site, cookies)));

        return true;
    }

    @Override
    @Transactional
    public boolean onetimeLogin(Long userId, SourceSite site, String loginId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean success = autoApplyRobot.loginAndSaveSession(userId, site.name(), loginId, password);
        // password는 이 메서드 스코프를 벗어나면 GC 대상 → 어디에도 저장하지 않음

        if (success) {
            String cookies = authSessionManager.getSession(userId, site.name());
            externalAccountRepository.findByUserIdAndSite(userId, site)
                    .ifPresentOrElse(
                            existing -> existing.updateSessionCookies(cookies),
                            () -> externalAccountRepository.save(
                                    ExternalAccount.createCookieSession(user, site, cookies)));
            log.info("[ExternalAccountService] 일회용 로그인 성공 - userId:{}, site:{}", userId, site);
        }

        return success;
    }

    @Override
    @Transactional
    public ExternalAccount registerCookieSession(Long userId, SourceSite site, String cookiesJson) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ExternalAccount account = externalAccountRepository.findByUserIdAndSite(userId, site)
                .map(existing -> {
                    existing.updateSessionCookies(cookiesJson);
                    return existing;
                })
                .orElseGet(() -> externalAccountRepository.save(
                        ExternalAccount.createCookieSession(user, site, cookiesJson)));

        authSessionManager.saveSessionString(userId, site.name(), cookiesJson);
        log.info("[ExternalAccountService] 브라우저 확장 쿠키 세션 등록 - userId:{}, site:{}", userId, site);
        return account;
    }

    @Override
    @Transactional
    public void invalidateSession(Long userId, SourceSite site) {
        log.info("[ExternalAccountService] 세션 무효화 - userId:{}, site:{}", userId, site);

        authSessionManager.invalidateSession(userId, site.name());

        externalAccountRepository.findByUserIdAndSite(userId, site)
                .ifPresent(ExternalAccount::invalidateSession);
    }
}
