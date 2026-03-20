package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;

/**
 * 사이트별 프로필 문자열 생성 전략 인터페이스 (OCP 준수).
 * 각 사이트가 강조하는 이력서 항목과 서술 스타일이 다르다.
 */
public interface SiteProfileStrategy {

    String getSiteName();

    String buildFromProfile(UserProfile profile);

    String buildFromResume(Resume resume);
}
