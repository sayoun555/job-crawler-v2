package com.portfolio.jobcrawler.domain.jobposting.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 일급 컬렉션: 기술 스택(TechStack)
 *
 * 콤마 구분 문자열로 DB에 저장되지만,
 * 도메인 레이어에서는 List<String>으로 다루며
 * 비즈니스 검증·변환 로직을 캡슐화한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStack {

    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String value;

    private TechStack(String value) {
        this.value = value;
    }

    // === 팩토리 메서드 ===

    public static TechStack of(String commaSeparated) {
        return new TechStack(commaSeparated);
    }

    public static TechStack from(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return new TechStack(null);
        }
        String joined = skills.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        return new TechStack(joined.isEmpty() ? null : joined);
    }

    // === 도메인 비즈니스 로직 ===

    /** 기술 스택을 리스트로 변환 */
    public List<String> toList() {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 특정 기술 포함 여부 (대소문자 무시) */
    public boolean contains(String skill) {
        return toList().stream()
                .anyMatch(s -> s.equalsIgnoreCase(skill.trim()));
    }

    /** 기술 스택 개수 */
    public int size() {
        return toList().size();
    }

    /** 비어있는지 확인 */
    public boolean isEmpty() {
        return value == null || value.isBlank();
    }

    /** 두 TechStack 간 교집합 스킬 목록 (매칭 분석용) */
    public List<String> matchWith(TechStack other) {
        if (this.isEmpty() || other.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> otherList = other.toList().stream()
                .map(String::toLowerCase)
                .toList();
        return this.toList().stream()
                .filter(s -> otherList.contains(s.toLowerCase()))
                .toList();
    }

    @Override
    public String toString() {
        return value != null ? value : "";
    }
}
