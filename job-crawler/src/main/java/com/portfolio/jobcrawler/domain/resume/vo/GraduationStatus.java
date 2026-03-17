package com.portfolio.jobcrawler.domain.resume.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum GraduationStatus {

    ENROLLED("재학"),
    LEAVE_OF_ABSENCE("휴학"),
    GRADUATED("졸업"),
    COMPLETED("수료"),
    DROPPED("중퇴"),
    EXPECTED("졸업예정");

    private final String description;

    public static GraduationStatus from(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(s -> s.name().equals(value) || s.description.equals(value))
                .findFirst()
                .orElse(null);
    }
}
