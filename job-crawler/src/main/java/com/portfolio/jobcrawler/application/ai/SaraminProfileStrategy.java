package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import org.springframework.stereotype.Component;

/**
 * 사람인 프로필 전략: 경력 상세 서술 + 기술스택 강조 (상세 서술형 선호)
 */
@Component
public class SaraminProfileStrategy implements SiteProfileStrategy {

    @Override
    public String getSiteName() {
        return "SARAMIN";
    }

    @Override
    public String buildFromProfile(UserProfile profile) {
        return "[사람인 이력서 기준 프로필]\n" +
                "기술스택: " + nullSafe(profile.getTechStack()) +
                "\n경력: " + nullSafe(profile.getCareer()) +
                "\n학력: " + nullSafe(profile.getEducation()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n강점: " + nullSafe(profile.getStrengths()) +
                "\n\n※ 사람인은 자유 양식의 상세 서술을 선호합니다. 기술스택과 프로젝트 경험을 구체적으로 서술하세요.";
    }

    @Override
    public String buildFromResume(Resume resume) {
        StringBuilder sb = new StringBuilder("[사람인 이력서 기준 프로필]\n");
        sb.append("이름: ").append(nullSafe(resume.getName()));
        ProfileBuildHelper.appendCareers(sb, resume);
        ProfileBuildHelper.appendSkills(sb, resume);
        ProfileBuildHelper.appendEducations(sb, resume);
        ProfileBuildHelper.appendCertifications(sb, resume);
        ProfileBuildHelper.appendActivities(sb, resume);
        if (resume.getSelfIntroduction() != null) {
            sb.append("\n자기소개: ").append(resume.getSelfIntroduction());
        }
        sb.append("\n\n※ 사람인은 기술스택과 프로젝트 경험을 구체적으로 서술하는 것을 선호합니다.");
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
