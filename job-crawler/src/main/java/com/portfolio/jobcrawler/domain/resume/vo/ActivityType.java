package com.portfolio.jobcrawler.domain.resume.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ActivityType {

    SCHOOL_ACTIVITY("교내활동"),
    INTERN("인턴"),
    VOLUNTEER("자원봉사"),
    CLUB("동아리"),
    PART_TIME("아르바이트"),
    EXTERNAL_ACTIVITY("대외활동"),
    EDUCATION("교육이수"),
    AWARD("수상"),
    OVERSEAS("해외경험");

    private final String description;

    public static ActivityType from(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(t -> t.name().equals(value) || t.description.equals(value))
                .findFirst()
                .orElse(null);
    }
}
