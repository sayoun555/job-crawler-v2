package com.portfolio.jobcrawler.infrastructure.autoapply;

import lombok.Getter;

/**
 * 자동 지원 결과 VO.
 */
@Getter
public class ApplyResult {

    public enum Status {
        SUCCESS, FAILED, UNKNOWN
    }

    private final Status status;
    private final String message;

    private ApplyResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ApplyResult success() {
        return new ApplyResult(Status.SUCCESS, "지원 완료");
    }

    public static ApplyResult fail(String reason) {
        return new ApplyResult(Status.FAILED, reason);
    }

    public static ApplyResult unknown(String message) {
        return new ApplyResult(Status.UNKNOWN, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
