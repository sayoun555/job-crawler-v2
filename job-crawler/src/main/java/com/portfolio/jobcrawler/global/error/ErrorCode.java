package com.portfolio.jobcrawler.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "해당 데이터를 찾을 수 없습니다."),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A004", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A005", "접근 권한이 없습니다."),

    // User
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "사용자를 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "프로필을 찾을 수 없습니다."),

    // Project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "프로젝트를 찾을 수 없습니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "P002", "프로젝트 접근 권한이 없습니다."),

    // Template
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "템플릿을 찾을 수 없습니다."),
    TEMPLATE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "T002", "템플릿 접근 권한이 없습니다."),

    // JobPosting
    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "J001", "채용 공고를 찾을 수 없습니다."),

    // Crawler
    CRAWLER_BLOCKED(HttpStatus.SERVICE_UNAVAILABLE, "CR001", "크롤러가 차단되었습니다."),
    CRAWLER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "CR002", "크롤링 시간이 초과되었습니다."),

    // Application
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AP001", "지원 이력을 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "AP002", "이미 지원한 공고입니다."),
    APPLICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AP003", "지원 이력 접근 권한이 없습니다."),

    // External Account
    EXTERNAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "외부 계정을 찾을 수 없습니다."),

    // Resume
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "이력서를 찾을 수 없습니다."),
    RESUME_ACCESS_DENIED(HttpStatus.FORBIDDEN, "R002", "이력서 접근 권한이 없습니다."),
    RESUME_SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "R003", "이력서 항목을 찾을 수 없습니다."),
    RESUME_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R004", "이력서 연동에 실패했습니다."),
    RESUME_SESSION_REQUIRED(HttpStatus.BAD_REQUEST, "R005", "해당 사이트 로그인이 필요합니다."),
    RESUME_EDUCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R006", "학력 정보를 찾을 수 없습니다."),
    RESUME_CAREER_NOT_FOUND(HttpStatus.NOT_FOUND, "R007", "경력 정보를 찾을 수 없습니다."),
    RESUME_SKILL_NOT_FOUND(HttpStatus.NOT_FOUND, "R008", "스킬 정보를 찾을 수 없습니다."),
    RESUME_CERTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R009", "자격증 정보를 찾을 수 없습니다."),
    RESUME_LANGUAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "R010", "어학 정보를 찾을 수 없습니다."),
    RESUME_ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "R011", "활동/수상 정보를 찾을 수 없습니다."),
    RESUME_PORTFOLIO_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "R012", "포트폴리오 링크를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
