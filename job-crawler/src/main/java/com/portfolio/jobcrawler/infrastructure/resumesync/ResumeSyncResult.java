package com.portfolio.jobcrawler.infrastructure.resumesync;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이력서 동기화 결과 VO.
 * 섹션별 성공/실패를 개별 추적하여 부분 성공도 표현할 수 있다.
 */
@Getter
public class ResumeSyncResult {

    public enum Status {
        SUCCESS, PARTIAL_SUCCESS, FAILED
    }

    public record SectionResult(boolean succeeded, String message) {

        public static SectionResult ofSuccess() {
            return new SectionResult(true, "성공");
        }

        public static SectionResult ofSuccess(String message) {
            return new SectionResult(true, message);
        }

        public static SectionResult ofFail(String message) {
            return new SectionResult(false, message);
        }
    }

    private final Status status;
    private final String message;
    private final Map<String, SectionResult> sectionResults;
    private final boolean sessionExpired;

    private ResumeSyncResult(Status status, String message,
                             Map<String, SectionResult> sectionResults, boolean sessionExpired) {
        this.status = status;
        this.message = message;
        this.sectionResults = Collections.unmodifiableMap(sectionResults);
        this.sessionExpired = sessionExpired;
    }

    // ── static factory methods ──

    public static ResumeSyncResult success() {
        return new ResumeSyncResult(Status.SUCCESS, "이력서 동기화 완료", new LinkedHashMap<>(), false);
    }

    public static ResumeSyncResult partialSuccess(String message, Map<String, SectionResult> sections) {
        return new ResumeSyncResult(Status.PARTIAL_SUCCESS, message, sections, false);
    }

    public static ResumeSyncResult fail(String message) {
        return new ResumeSyncResult(Status.FAILED, message, new LinkedHashMap<>(), false);
    }

    public static ResumeSyncResult sessionExpired(String message) {
        return new ResumeSyncResult(Status.FAILED, message, new LinkedHashMap<>(), true);
    }

    // ── Builder ──

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public static class Builder {

        private final Map<String, SectionResult> sections = new LinkedHashMap<>();

        public Builder addSectionResult(String sectionName, boolean success, String message) {
            sections.put(sectionName, new SectionResult(success, message));
            return this;
        }

        public Builder addSectionSuccess(String sectionName) {
            sections.put(sectionName, SectionResult.ofSuccess());
            return this;
        }

        public Builder addSectionFail(String sectionName, String message) {
            sections.put(sectionName, SectionResult.ofFail(message));
            return this;
        }

        public ResumeSyncResult build() {
            if (sections.isEmpty()) {
                return ResumeSyncResult.fail("동기화할 섹션이 없습니다.");
            }

            long successCount = sections.values().stream().filter(SectionResult::succeeded).count();
            long totalCount = sections.size();

            if (successCount == totalCount) {
                return new ResumeSyncResult(Status.SUCCESS, "이력서 동기화 완료", sections, false);
            }
            if (successCount == 0) {
                return new ResumeSyncResult(Status.FAILED, "이력서 동기화 전체 실패", sections, false);
            }
            String msg = String.format("이력서 부분 동기화 (%d/%d 섹션 성공)", successCount, totalCount);
            return new ResumeSyncResult(Status.PARTIAL_SUCCESS, msg, sections, false);
        }
    }
}
