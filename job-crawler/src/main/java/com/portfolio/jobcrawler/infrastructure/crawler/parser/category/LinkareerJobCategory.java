package com.portfolio.jobcrawler.infrastructure.crawler.parser.category;

/**
 * 링커리어 활동 유형 (filterBy_activityTypeID).
 * 5 = 채용공고
 */
public enum LinkareerJobCategory {
    RECRUIT("채용공고", "5"),
    INTERN("인턴", "5"),
    NEWBIE("신입", "5");

    private final String displayName;
    private final String code;

    LinkareerJobCategory(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "기타";
        String lower = raw.toLowerCase();
        if (lower.contains("개발") || lower.contains("엔지니어") || lower.contains("it")) return "개발";
        if (lower.contains("기획") || lower.contains("pm")) return "기획";
        if (lower.contains("디자인") || lower.contains("ui") || lower.contains("ux")) return "디자인";
        if (lower.contains("마케팅") || lower.contains("홍보")) return "마케팅";
        if (lower.contains("영업") || lower.contains("세일즈")) return "영업";
        if (lower.contains("데이터") || lower.contains("ai") || lower.contains("ml")) return "데이터/AI";
        if (lower.contains("생산") || lower.contains("제조") || lower.contains("품질")) return "생산/제조";
        if (lower.contains("연구") || lower.contains("r&d")) return "연구개발";
        return "기타";
    }
}
