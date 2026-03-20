package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import org.springframework.stereotype.Component;

/**
 * 링커리어 프로필 전략: 학력 + 대외활동 + 성장가능성 (대학생/신입 대상)
 */
@Component
public class LinkareerProfileStrategy implements SiteProfileStrategy {

    @Override
    public String getSiteName() {
        return "LINKAREER";
    }

    @Override
    public String buildFromProfile(UserProfile profile) {
        return "[링커리어 이력서 기준 프로필]\n" +
                "학력: " + nullSafe(profile.getEducation()) +
                "\n기술스택: " + nullSafe(profile.getTechStack()) +
                "\n강점/성장가능성: " + nullSafe(profile.getStrengths()) +
                "\n경력: " + nullSafe(profile.getCareer()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n\n※ 링커리어는 대학생/신입 대상입니다. 학습 의지, 대외활동 경험, 성장 가능성을 강조하세요.";
    }

    @Override
    public String buildFromResume(Resume resume) {
        StringBuilder sb = new StringBuilder("[링커리어 이력서 기준 프로필]\n");
        sb.append("이름: ").append(nullSafe(resume.getName()));
        ProfileBuildHelper.appendEducations(sb, resume);
        ProfileBuildHelper.appendActivities(sb, resume);
        ProfileBuildHelper.appendSkills(sb, resume);
        ProfileBuildHelper.appendCareers(sb, resume);
        ProfileBuildHelper.appendCertifications(sb, resume);
        sb.append("\n\n※ 링커리어는 대학생/신입 대상입니다. 학습 의지와 성장 가능성을 강조하세요.");
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
