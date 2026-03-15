package com.portfolio.jobcrawler.infrastructure.crawler.parser.category;

public enum JobKoreaJobCategory {
    FRONTEND("프론트엔드개발자", "웹개발", "프론트엔드", "프론트"),
    BACKEND("백엔드개발자", "서버", "백엔드", "backend"),
    WEB("웹개발자", "웹개발", "웹"),
    APP("앱개발자", "앱개발", "앱", "모바일"),
    SYSTEM("시스템엔지니어", "시스템", "인프라"),
    NETWORK("네트워크엔지니어", "네트워크"),
    DBA("DBA", "데이터베이스", "dba"),
    SOFTWARE("소프트웨어개발자", "소프트웨어", "sw"),
    GAME("게임개발자", "게임"),
    HARDWARE("하드웨어개발자", "하드웨어", "hw", "펌웨어"),
    AI_ML("AI/ML엔지니어", "ai", "ml", "머신러닝", "딥러닝"),
    BLOCKCHAIN("블록체인개발자", "블록체인"),
    MLOPS("MLOps엔지니어", "mlops"),
    AI_SERVICE("AI서비스개발자", "ai서비스"),
    CLOUD("클라우드엔지니어", "클라우드", "cloud", "aws", "gcp", "azure"),
    AI_RESEARCH("AI/ML연구원", "연구원", "리서처"),
    DATA_ENGINEER("데이터엔지니어", "데이터엔지니어", "데이터파이프라인"),
    DATA_SCIENTIST("데이터사이언티스트", "데이터사이언", "데이터분석"),
    SECURITY("보안엔지니어", "보안", "security"),
    DATA_ANALYST("데이터분석가", "데이터분석", "분석가");

    private final String displayName;
    private final String[] keywords;

    JobKoreaJobCategory(String displayName, String... keywords) {
        this.displayName = displayName;
        this.keywords = keywords;
    }

    public String getDisplayName() { return displayName; }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "기타";
        String lower = raw.toLowerCase();
        for (JobKoreaJobCategory cat : values()) {
            for (String keyword : cat.keywords) {
                if (lower.contains(keyword)) return cat.displayName;
            }
        }
        return "기타";
    }
}
