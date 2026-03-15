package com.portfolio.jobcrawler.application.auth;

import com.portfolio.jobcrawler.application.auth.dto.LoginCommand;
import com.portfolio.jobcrawler.application.auth.dto.SignupCommand;
import com.portfolio.jobcrawler.application.auth.dto.TokenResult;

/**
 * 인증 Application Service 인터페이스.
 */
public interface AuthService {
    TokenResult signup(SignupCommand command);

    TokenResult login(LoginCommand command);

    TokenResult refresh(String refreshToken);
}
