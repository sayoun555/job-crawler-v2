package com.portfolio.jobcrawler.application.resume;

import com.portfolio.jobcrawler.application.resume.dto.*;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.domain.resume.repository.*;
import com.portfolio.jobcrawler.domain.resume.vo.ActivityType;
import com.portfolio.jobcrawler.domain.resume.vo.GraduationStatus;
import com.portfolio.jobcrawler.domain.resume.vo.SchoolType;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncRobot;
import com.portfolio.jobcrawler.infrastructure.resumesync.SaraminResumeImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeEducationRepository resumeEducationRepository;
    private final ResumeCareerRepository resumeCareerRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final ResumeCertificationRepository resumeCertificationRepository;
    private final ResumeLanguageRepository resumeLanguageRepository;
    private final ResumeActivityRepository resumeActivityRepository;
    private final ResumePortfolioLinkRepository resumePortfolioLinkRepository;
    private final ResumeSyncRobot resumeSyncRobot;
    private final SaraminResumeImporter saraminResumeImporter;

    @Override
    @Transactional
    public Resume getOrCreateResume(Long userId) {
        return resumeRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    return resumeRepository.save(Resume.createFor(user));
                });
    }

    @Override
    @Transactional
    public Resume updateBasicInfo(Long userId, ResumeBasicInfoCommand command) {
        Resume resume = getResumeByUserId(userId);
        resume.updateBasicInfo(
                command.name(), command.phone(), command.email(),
                command.gender(), command.birthDate(), command.address());
        return resume;
    }

    @Override
    @Transactional
    public Resume updateIntroduction(Long userId, String introduction, String selfIntroduction) {
        Resume resume = getResumeByUserId(userId);
        resume.updateIntroduction(introduction);
        resume.updateSelfIntroduction(selfIntroduction);
        return resume;
    }

    @Override
    @Transactional
    public Resume updateDesiredConditions(Long userId, DesiredConditionsCommand command) {
        Resume resume = getResumeByUserId(userId);
        resume.updateDesiredConditions(
                command.desiredSalary(), command.desiredEmploymentType(), command.desiredLocation());
        resume.updateSpecialStatus(
                command.militaryStatus(), command.disabilityStatus(), command.veteranStatus());
        return resume;
    }

    // ── 학력 ──

    @Override
    @Transactional
    public ResumeEducation addEducation(Long userId, EducationCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumeEducation education = ResumeEducation.builder()
                .resume(resume)
                .schoolType(SchoolType.from(command.schoolType()))
                .schoolName(command.schoolName())
                .major(command.major())
                .subMajor(command.subMajor())
                .startDate(command.startDate())
                .endDate(command.endDate())
                .graduationStatus(GraduationStatus.from(command.graduationStatus()))
                .gpa(command.gpa())
                .gpaScale(command.gpaScale())
                .sortOrder(resume.getEducations().size())
                .build();
        resume.getEducations().add(education);
        return resumeEducationRepository.save(education);
    }

    @Override
    @Transactional
    public ResumeEducation updateEducation(Long userId, Long educationId, EducationCommand command) {
        ResumeEducation education = resumeEducationRepository.findById(educationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_EDUCATION_NOT_FOUND));
        validateOwnership(education.getResume(), userId);
        education.update(
                SchoolType.from(command.schoolType()), command.schoolName(),
                command.major(), command.subMajor(), command.startDate(), command.endDate(),
                GraduationStatus.from(command.graduationStatus()),
                command.gpa(), command.gpaScale(), education.getSortOrder());
        return education;
    }

    @Override
    @Transactional
    public void deleteEducation(Long userId, Long educationId) {
        ResumeEducation education = resumeEducationRepository.findById(educationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_EDUCATION_NOT_FOUND));
        validateOwnership(education.getResume(), userId);
        education.getResume().getEducations().remove(education);
    }

    // ── 경력 ──

    @Override
    @Transactional
    public ResumeCareer addCareer(Long userId, CareerCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumeCareer career = ResumeCareer.builder()
                .resume(resume)
                .companyName(command.companyName())
                .department(command.department())
                .position(command.position())
                .rank(command.rank())
                .startDate(command.startDate())
                .endDate(command.endDate())
                .currentlyWorking(command.currentlyWorking())
                .jobDescription(command.jobDescription())
                .salary(command.salary())
                .sortOrder(resume.getCareers().size())
                .build();
        resume.getCareers().add(career);
        return resumeCareerRepository.save(career);
    }

    @Override
    @Transactional
    public ResumeCareer updateCareer(Long userId, Long careerId, CareerCommand command) {
        ResumeCareer career = resumeCareerRepository.findById(careerId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_CAREER_NOT_FOUND));
        validateOwnership(career.getResume(), userId);
        career.update(
                command.companyName(), command.department(), command.position(), command.rank(),
                command.startDate(), command.endDate(), command.currentlyWorking(),
                command.jobDescription(), command.salary(), career.getSortOrder());
        return career;
    }

    @Override
    @Transactional
    public void deleteCareer(Long userId, Long careerId) {
        ResumeCareer career = resumeCareerRepository.findById(careerId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_CAREER_NOT_FOUND));
        validateOwnership(career.getResume(), userId);
        career.getResume().getCareers().remove(career);
    }

    // ── 스킬 ──

    @Override
    @Transactional
    public ResumeSkill addSkill(Long userId, String skillName) {
        Resume resume = getResumeByUserId(userId);
        ResumeSkill skill = ResumeSkill.builder()
                .resume(resume)
                .skillName(skillName)
                .sortOrder(resume.getSkills().size())
                .build();
        resume.getSkills().add(skill);
        return resumeSkillRepository.save(skill);
    }

    @Override
    @Transactional
    public void deleteSkill(Long userId, Long skillId) {
        ResumeSkill skill = resumeSkillRepository.findById(skillId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_SKILL_NOT_FOUND));
        validateOwnership(skill.getResume(), userId);
        skill.getResume().getSkills().remove(skill);
    }

    // ── 자격증 ──

    @Override
    @Transactional
    public ResumeCertification addCertification(Long userId, CertificationCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumeCertification cert = ResumeCertification.builder()
                .resume(resume)
                .certName(command.certName())
                .issuingOrganization(command.issuingOrganization())
                .acquiredDate(command.acquiredDate())
                .sortOrder(resume.getCertifications().size())
                .build();
        resume.getCertifications().add(cert);
        return resumeCertificationRepository.save(cert);
    }

    @Override
    @Transactional
    public ResumeCertification updateCertification(Long userId, Long certId, CertificationCommand command) {
        ResumeCertification cert = resumeCertificationRepository.findById(certId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_CERTIFICATION_NOT_FOUND));
        validateOwnership(cert.getResume(), userId);
        cert.update(command.certName(), command.issuingOrganization(), command.acquiredDate());
        return cert;
    }

    @Override
    @Transactional
    public void deleteCertification(Long userId, Long certId) {
        ResumeCertification cert = resumeCertificationRepository.findById(certId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_CERTIFICATION_NOT_FOUND));
        validateOwnership(cert.getResume(), userId);
        cert.getResume().getCertifications().remove(cert);
    }

    // ── 어학 ──

    @Override
    @Transactional
    public ResumeLanguage addLanguage(Long userId, LanguageCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumeLanguage lang = ResumeLanguage.builder()
                .resume(resume)
                .languageName(command.languageName())
                .examName(command.examName())
                .score(command.score())
                .grade(command.grade())
                .examDate(command.examDate())
                .sortOrder(resume.getLanguages().size())
                .build();
        resume.getLanguages().add(lang);
        return resumeLanguageRepository.save(lang);
    }

    @Override
    @Transactional
    public ResumeLanguage updateLanguage(Long userId, Long langId, LanguageCommand command) {
        ResumeLanguage lang = resumeLanguageRepository.findById(langId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_LANGUAGE_NOT_FOUND));
        validateOwnership(lang.getResume(), userId);
        lang.update(command.languageName(), command.examName(), command.score(),
                command.grade(), command.examDate());
        return lang;
    }

    @Override
    @Transactional
    public void deleteLanguage(Long userId, Long langId) {
        ResumeLanguage lang = resumeLanguageRepository.findById(langId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_LANGUAGE_NOT_FOUND));
        validateOwnership(lang.getResume(), userId);
        lang.getResume().getLanguages().remove(lang);
    }

    // ── 활동/수상 ──

    @Override
    @Transactional
    public ResumeActivity addActivity(Long userId, ActivityCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumeActivity activity = ResumeActivity.builder()
                .resume(resume)
                .activityType(ActivityType.from(command.activityType()))
                .activityName(command.activityName())
                .organization(command.organization())
                .description(command.description())
                .startDate(command.startDate())
                .endDate(command.endDate())
                .sortOrder(resume.getActivities().size())
                .build();
        resume.getActivities().add(activity);
        return resumeActivityRepository.save(activity);
    }

    @Override
    @Transactional
    public ResumeActivity updateActivity(Long userId, Long activityId, ActivityCommand command) {
        ResumeActivity activity = resumeActivityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_ACTIVITY_NOT_FOUND));
        validateOwnership(activity.getResume(), userId);
        activity.update(
                ActivityType.from(command.activityType()), command.activityName(),
                command.organization(), command.description(),
                command.startDate(), command.endDate());
        return activity;
    }

    @Override
    @Transactional
    public void deleteActivity(Long userId, Long activityId) {
        ResumeActivity activity = resumeActivityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_ACTIVITY_NOT_FOUND));
        validateOwnership(activity.getResume(), userId);
        activity.getResume().getActivities().remove(activity);
    }

    // ── 포트폴리오 링크 ──

    @Override
    @Transactional
    public ResumePortfolioLink addPortfolioLink(Long userId, PortfolioLinkCommand command) {
        Resume resume = getResumeByUserId(userId);
        ResumePortfolioLink link = ResumePortfolioLink.builder()
                .resume(resume)
                .linkType(command.linkType())
                .url(command.url())
                .description(command.description())
                .build();
        resume.getPortfolioLinks().add(link);
        return resumePortfolioLinkRepository.save(link);
    }

    @Override
    @Transactional
    public void deletePortfolioLink(Long userId, Long linkId) {
        ResumePortfolioLink link = resumePortfolioLinkRepository.findById(linkId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_PORTFOLIO_LINK_NOT_FOUND));
        validateOwnership(link.getResume(), userId);
        link.getResume().getPortfolioLinks().remove(link);
    }

    // ── 이력서 연동 ──

    @Override
    public ResumeSyncResult syncToSite(Long userId, String site) {
        Resume resume = getResumeByUserId(userId);
        return resumeSyncRobot.syncResume(userId, site, resume);
    }

    @Override
    public Map<String, ResumeSyncResult> syncToAllSites(Long userId) {
        Resume resume = getResumeByUserId(userId);
        return resumeSyncRobot.syncToAllSites(userId, resume);
    }

    @Override
    @Transactional
    public SaraminResumeImporter.ImportResult importFromSite(Long userId, String site) {
        Resume resume = getOrCreateResume(userId);

        if (!"SARAMIN".equalsIgnoreCase(site)) {
            return SaraminResumeImporter.ImportResult.fail("현재 사람인만 가져오기를 지원합니다.");
        }

        // 기존 데이터 초기화
        resume.getEducations().clear();
        resume.getCareers().clear();
        resume.getSkills().clear();
        resume.getCertifications().clear();
        resume.getLanguages().clear();
        resume.getActivities().clear();

        return resumeSyncRobot.importResume(userId, site, resume);
    }

    // ── private helpers ──

    private Resume getResumeByUserId(Long userId) {
        return resumeRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_NOT_FOUND));
    }

    private void validateOwnership(Resume resume, Long userId) {
        if (!resume.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.RESUME_ACCESS_DENIED);
        }
    }
}
