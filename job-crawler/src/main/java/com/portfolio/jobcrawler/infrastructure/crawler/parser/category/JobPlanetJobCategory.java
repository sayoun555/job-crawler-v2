package com.portfolio.jobcrawler.infrastructure.crawler.parser.category;

import java.util.List;

public enum JobPlanetJobCategory {
    // 상위 카테고리 — 개발 직군 전체
    DEV("개발", null, "개발"),

    // 하위 카테고리
    WEB("웹개발", "116", "웹개발"),
    SERVER("서버개발", "117", "서버", "백엔드", "backend"),
    FRONTEND("프론트엔드개발", "119", "프론트", "front"),
    ANDROID("안드로이드개발", "121", "안드로이드", "android"),
    IOS("iOS개발", "122", "ios"),
    DATA("데이터엔지니어", "125", "데이터"),
    ML("머신러닝개발", "127", "머신러닝", "ai"),
    SYSTEM("시스템엔지니어", "126", "시스템"),
    QA("QA", "131", "qa", "테스트"),
    PLANNING("기획자", "123", "기획"),
    DESIGN("디자이너", "124", "디자인", "디자이너");

    // 개발 직군 하위 카테고리 코드 목록
    private static final List<String> DEV_CODES = List.of(
            "116", "117", "119", "121", "122", "125", "126", "127", "131"
    );

    private final String displayName;
    private final String code;
    private final String[] keywords;

    JobPlanetJobCategory(String displayName, String code, String... keywords) {
        this.displayName = displayName;
        this.code = code;
        this.keywords = keywords;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "기타";
        String lower = raw.toLowerCase();
        for (JobPlanetJobCategory category : values()) {
            for (String keyword : category.keywords) {
                if (lower.contains(keyword)) {
                    return category.displayName;
                }
            }
        }
        return "기타";
    }

    public static String getCodeByDisplayName(String displayName) {
        for (JobPlanetJobCategory category : values()) {
            if (category.displayName.equals(displayName)) {
                return category.code;
            }
        }
        return null;
    }

    /**
     * "개발" 상위 카테고리인지 판별
     */
    public static boolean isDevCategory(String displayName) {
        return "개발".equals(displayName);
    }

    /**
     * 개발 직군 하위 카테고리 코드 목록 반환
     */
    public static List<String> getDevCategoryCodes() {
        return DEV_CODES;
    }
}
