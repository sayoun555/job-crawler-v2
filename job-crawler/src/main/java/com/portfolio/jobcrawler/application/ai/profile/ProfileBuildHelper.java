package com.portfolio.jobcrawler.application.ai.profile;

import com.portfolio.jobcrawler.domain.resume.entity.Resume;

/**
 * Resume 엔티티에서 각 섹션을 StringBuilder에 추가하는 공통 유틸.
 * SiteProfileStrategy 구현체들이 공유한다.
 */
public final class ProfileBuildHelper {

    private ProfileBuildHelper() {}

    public static void appendEducations(StringBuilder sb, Resume resume) {
        if (!resume.getEducations().isEmpty()) {
            sb.append("\n학력: ");
            resume.getEducations().forEach(e ->
                    sb.append(e.getSchoolName()).append(" ").append(e.getMajor()).append(", "));
        }
    }

    public static void appendCareers(StringBuilder sb, Resume resume) {
        if (!resume.getCareers().isEmpty()) {
            sb.append("\n경력: ");
            resume.getCareers().forEach(c ->
                    sb.append(c.getCompanyName()).append(" ")
                            .append(nullSafe(c.getPosition())).append(" ")
                            .append(nullSafe(c.getJobDescription())).append(", "));
        }
    }

    public static void appendSkills(StringBuilder sb, Resume resume) {
        if (!resume.getSkills().isEmpty()) {
            sb.append("\n기술스택: ");
            resume.getSkills().forEach(s -> sb.append(s.getSkillName()).append(", "));
        }
    }

    public static void appendCertifications(StringBuilder sb, Resume resume) {
        if (!resume.getCertifications().isEmpty()) {
            sb.append("\n자격증: ");
            resume.getCertifications().forEach(c -> sb.append(c.getCertName()).append(", "));
        }
    }

    public static void appendActivities(StringBuilder sb, Resume resume) {
        if (!resume.getActivities().isEmpty()) {
            sb.append("\n활동: ");
            resume.getActivities().forEach(a ->
                    sb.append(a.getActivityName()).append(" ")
                            .append(nullSafe(a.getDescription())).append(", "));
        }
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
