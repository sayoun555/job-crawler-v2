package com.portfolio.jobcrawler.domain.account.vo;

/**
 * 외부 사이트 인증 방식.
 * CREDENTIAL: 아이디/비밀번호 직접 입력
 * COOKIE_SESSION: Playwright 팝업 브라우저로 소셜 로그인 후 쿠키 자동 추출
 */
public enum AuthType {
    CREDENTIAL,
    COOKIE_SESSION
}
