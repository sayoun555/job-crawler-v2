package com.portfolio.jobcrawler.domain.user.vo;

/**
 * 사용자 가입 상태.
 * PENDING: 가입 대기 (관리자 승인 전, 로그인 불가)
 * ACTIVE: 활성 (로그인 가능)
 * SUSPENDED: 정지 (관리자가 차단)
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    SUSPENDED
}
