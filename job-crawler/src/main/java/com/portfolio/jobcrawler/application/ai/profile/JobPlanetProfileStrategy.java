package com.portfolio.jobcrawler.application.ai.profile;

import com.portfolio.jobcrawler.application.ai.SiteProfileStrategy;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import org.springframework.stereotype.Component;

/**
 * 잡플래닛 프로필 전략: 커리어 내러티브 + 기업문화 핏 (회사 리뷰 플랫폼)
 */
@Component
public class JobPlanetProfileStrategy implements SiteProfileStrategy {

    @Override
    public String getSiteName() {
        return "JOBPLANET";
    }

    @Override
    public String buildFromProfile(UserProfile profile) {
        return "[잡플래닛 이력서 기준 프로필]\n" +
                "경력/커리어: " + nullSafe(profile.getCareer()) +
                "\n기술스택: " + nullSafe(profile.getTechStack()) +
                "\n강점: " + nullSafe(profile.getStrengths()) +
                "\n학력: " + nullSafe(profile.getEducation()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n\n※ 잡플래닛은 기업 리뷰 플랫폼입니다. 기업문화 적합성과 커리어 성장 스토리를 강조하세요.";
    }

    @Override
    public String buildFromResume(Resume resume) {
        StringBuilder sb = new StringBuilder("[잡플래닛 이력서 기준 프로필]\n");
        sb.append("이름: ").append(nullSafe(resume.getName()));
        ProfileBuildHelper.appendCareers(sb, resume);
        ProfileBuildHelper.appendSkills(sb, resume);
        ProfileBuildHelper.appendEducations(sb, resume);
        ProfileBuildHelper.appendCertifications(sb, resume);
        if (resume.getSelfIntroduction() != null) {
            sb.append("\n자기소개: ").append(resume.getSelfIntroduction());
        }
        sb.append("\n\n※ 잡플래닛은 기업문화 적합성과 커리어 성장 스토리를 강조하세요.");
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
