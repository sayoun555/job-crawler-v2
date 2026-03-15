package com.portfolio.jobcrawler.application.account;

import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.account.repository.ExternalAccountRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.infrastructure.autoapply.AutoApplyRobot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalAccountServiceImpl implements ExternalAccountService {

    private final ExternalAccountRepository externalAccountRepository;
    private final UserRepository userRepository;
    private final AutoApplyRobot autoApplyRobot;

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

        // 이미 등록된 계정이면 업데이트
        return externalAccountRepository.findByUserIdAndSite(userId, site)
                .map(existing -> {
                    existing.updateCredentials(accountId, encryptPassword(password));
                    return existing;
                })
                .orElseGet(() -> externalAccountRepository.save(
                        ExternalAccount.builder()
                                .user(user).site(site)
                                .accountId(accountId)
                                .encryptedPassword(encryptPassword(password))
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
        account.updateCredentials(newAccountId, encryptPassword(newPassword));
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

    // TODO: 실제로는 AES-256 등 양방향 암호화 사용 (복호화 필요하므로)
    private String encryptPassword(String plainPassword) {
        return Base64.getEncoder().encodeToString(plainPassword.getBytes());
    }

    @Override
    @Transactional
    public boolean openLoginPopup(Long userId, SourceSite site) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String cookies = autoApplyRobot.openLoginPopup(userId, site.name());
        if (cookies == null) {
            return false;
        }

        // 기존 계정이 있으면 쿠키 업데이트, 없으면 신규 생성
        externalAccountRepository.findByUserIdAndSite(userId, site)
                .ifPresentOrElse(
                        existing -> existing.updateSessionCookies(cookies),
                        () -> externalAccountRepository.save(
                                ExternalAccount.createCookieSession(user, site, cookies)));

        return true;
    }
}
