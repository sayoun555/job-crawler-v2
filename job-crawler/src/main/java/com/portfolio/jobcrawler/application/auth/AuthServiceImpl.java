package com.portfolio.jobcrawler.application.auth;

import com.portfolio.jobcrawler.application.auth.dto.LoginCommand;
import com.portfolio.jobcrawler.application.auth.dto.SignupCommand;
import com.portfolio.jobcrawler.application.auth.dto.TokenResult;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.domain.user.repository.UserProfileRepository;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.auth.JwtTokenProvider;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService 구현체.
 * 흐름만 제어하고, 비즈니스 로직은 User 도메인에 위임.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public TokenResult signup(SignupCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(command.email())
                .password(passwordEncoder.encode(command.password()))
                .nickname(command.nickname())
                .build();
        // 신규 가입은 PENDING 상태 (관리자 승인 전 로그인 불가)
        userRepository.save(user);

        userProfileRepository.save(UserProfile.builder().user(user).build());

        // PENDING 상태이므로 토큰 발급하지 않음
        return null;
    }

    @Override
    public TokenResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new CustomException(ErrorCode.ACCOUNT_NOT_APPROVED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole().name());
        return TokenResult.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenValidityInSeconds());
    }

    @Override
    public TokenResult refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole().name());
        return TokenResult.of(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenValidityInSeconds());
    }
}
