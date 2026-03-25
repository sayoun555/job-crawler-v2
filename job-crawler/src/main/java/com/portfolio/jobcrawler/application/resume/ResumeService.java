package com.portfolio.jobcrawler.application.resume;

import com.portfolio.jobcrawler.application.resume.dto.*;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult;
import com.portfolio.jobcrawler.infrastructure.resumesync.importer.SaraminResumeImporter;

import java.util.Map;

public interface ResumeService {

    // 기본 CRUD
    Resume getOrCreateResume(Long userId);

    Resume updateBasicInfo(Long userId, ResumeBasicInfoCommand command);

    Resume updateIntroduction(Long userId, String introduction, String selfIntroduction);

    Resume updateDesiredConditions(Long userId, DesiredConditionsCommand command);

    // 학력
    ResumeEducation addEducation(Long userId, EducationCommand command);

    ResumeEducation updateEducation(Long userId, Long educationId, EducationCommand command);

    void deleteEducation(Long userId, Long educationId);

    // 경력
    ResumeCareer addCareer(Long userId, CareerCommand command);

    ResumeCareer updateCareer(Long userId, Long careerId, CareerCommand command);

    void deleteCareer(Long userId, Long careerId);

    // 스킬
    ResumeSkill addSkill(Long userId, String skillName);

    void deleteSkill(Long userId, Long skillId);

    // 자격증
    ResumeCertification addCertification(Long userId, CertificationCommand command);

    ResumeCertification updateCertification(Long userId, Long certId, CertificationCommand command);

    void deleteCertification(Long userId, Long certId);

    // 어학
    ResumeLanguage addLanguage(Long userId, LanguageCommand command);

    ResumeLanguage updateLanguage(Long userId, Long langId, LanguageCommand command);

    void deleteLanguage(Long userId, Long langId);

    // 활동/수상
    ResumeActivity addActivity(Long userId, ActivityCommand command);

    ResumeActivity updateActivity(Long userId, Long activityId, ActivityCommand command);

    void deleteActivity(Long userId, Long activityId);

    // 포트폴리오 링크
    ResumePortfolioLink addPortfolioLink(Long userId, PortfolioLinkCommand command);

    void deletePortfolioLink(Long userId, Long linkId);

    // 이력서 연동
    ResumeSyncResult syncToSite(Long userId, String site);

    Map<String, ResumeSyncResult> syncToAllSites(Long userId);

    // 이력서 가져오기
    SaraminResumeImporter.ImportResult importFromSite(Long userId, String site);

    // 사이트별 이력서 목록 조회 (사이트에 여러 이력서 가능)
    java.util.List<Resume> getSiteResumes(Long userId, String site);

    // 유저의 모든 이력서 (마스터 + 사이트별)
    java.util.List<Resume> getAllResumes(Long userId);

    // 사이트별 이력서 수정 (resumeId 지정)
    Resume getResumeById(Long userId, Long resumeId);

    Resume updateBasicInfo(Long userId, Long resumeId, ResumeBasicInfoCommand command);

    Resume updateIntroduction(Long userId, Long resumeId, String introduction, String selfIntroduction);

    Resume updateDesiredConditions(Long userId, Long resumeId, DesiredConditionsCommand command);

    ResumeEducation addEducation(Long userId, Long resumeId, EducationCommand command);

    ResumeCareer addCareer(Long userId, Long resumeId, CareerCommand command);

    ResumeSkill addSkill(Long userId, Long resumeId, String skillName);

    ResumeCertification addCertification(Long userId, Long resumeId, CertificationCommand command);

    ResumeLanguage addLanguage(Long userId, Long resumeId, LanguageCommand command);

    ResumeActivity addActivity(Long userId, Long resumeId, ActivityCommand command);

    ResumePortfolioLink addPortfolioLink(Long userId, Long resumeId, PortfolioLinkCommand command);
}
