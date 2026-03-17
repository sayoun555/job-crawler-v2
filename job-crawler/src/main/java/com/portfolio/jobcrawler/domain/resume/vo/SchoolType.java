package com.portfolio.jobcrawler.domain.resume.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SchoolType {

    HIGH_SCHOOL("고등학교"),
    COLLEGE_2Y("대학교(2,3년)"),
    COLLEGE_4Y("대학교(4년)"),
    GRADUATE_MASTER("대학원(석사)"),
    GRADUATE_DOCTOR("대학원(박사)");

    private final String description;

    public static SchoolType from(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(t -> t.name().equals(value) || t.description.equals(value))
                .findFirst()
                .orElse(null);
    }
}
