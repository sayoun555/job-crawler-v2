package com.portfolio.jobcrawler.application.ai.profile;

import com.portfolio.jobcrawler.application.ai.SiteProfileStrategy;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import org.springframework.stereotype.Component;

/**
 * 잡코리아 프로필 전략: 자격증 + 스킬 간결 나열 (구조화된 형식 선호)
 */
@Component
public class JobKoreaProfileStrategy implements SiteProfileStrategy {

    @Override
    public String getSiteName() {
        return "JOBKOREA";
    }

    @Override
    public String buildFromProfile(UserProfile profile) {
        return "[잡코리아 이력서 기준 프로필]\n" +
                "스킬: " + nullSafe(profile.getTechStack()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n경력: " + nullSafe(profile.getCareer()) +
                "\n학력: " + nullSafe(profile.getEducation()) +
                "\n강점: " + nullSafe(profile.getStrengths()) +
                "\n\n※ 잡코리아는 간결하고 구조화된 형식을 선호합니다. 핵심 키워드 중심으로 정리하세요.";
    }

    @Override
    public String buildFromResume(Resume resume) {
        StringBuilder sb = new StringBuilder("[잡코리아 이력서 기준 프로필]\n");
        sb.append("이름: ").append(nullSafe(resume.getName()));
        ProfileBuildHelper.appendSkills(sb, resume);
        ProfileBuildHelper.appendCertifications(sb, resume);
        ProfileBuildHelper.appendCareers(sb, resume);
        ProfileBuildHelper.appendEducations(sb, resume);
        sb.append("\n\n※ 잡코리아는 간결하고 구조화된 형식을 선호합니다.");
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
