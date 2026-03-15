package com.portfolio.jobcrawler.infrastructure.crawler.parser.category;

public enum SaraminJobCategory {
    SERVER("서버/네트워크/보안", "84", "서버", "백엔드", "backend", "네트워크", "보안", "클라우드", "인프라"),
    WEB("웹개발", "86"),
    APP("앱개발", "87", "안드로이드", "ios", "앱"),
    FRONTEND("프론트엔드", "92", "프론트", "front"),
    UI("퍼블리싱/UI개발", "93", "퍼블리셔", "ui"),
    GAME("게임개발", "95", "게임"),
    QA("QA/테스트", "98", "qa", "테스트"),
    PM("기획/PM", "99", "기획", "pm"),
    DATA("데이터/AI", "101", "데이터", "ai", "머신러닝");

    private final String displayName;
    private final String code;
    private final String[] keywords;

    SaraminJobCategory(String displayName, String code, String... keywords) {
        this.displayName = displayName;
        this.code = code;
        this.keywords = keywords;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "기타";
        String lower = raw.toLowerCase();
        for (SaraminJobCategory category : values()) {
            for (String keyword : category.keywords) {
                if (lower.contains(keyword)) {
                    return category.displayName;
                }
            }
        }
        return "기타";
    }

    public static String getCodeByDisplayName(String displayName) {
        for (SaraminJobCategory category : values()) {
            if (category.displayName.equals(displayName)) {
                return category.code;
            }
        }
        return null;
    }
}
