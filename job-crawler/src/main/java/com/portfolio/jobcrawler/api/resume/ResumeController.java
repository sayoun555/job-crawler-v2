package com.portfolio.jobcrawler.api.resume;

import com.portfolio.jobcrawler.api.resume.dto.*;
import com.portfolio.jobcrawler.application.resume.ResumeService;
import com.portfolio.jobcrawler.application.resume.dto.*;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult;
import com.portfolio.jobcrawler.infrastructure.resumesync.SaraminResumeImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    // ── 기본 이력서 ──

    @GetMapping
    public ResponseEntity<ApiResponse<Resume>> getResume(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.getOrCreateResume(getUserId(auth))));
    }

    @PutMapping("/basic-info")
    public ResponseEntity<ApiResponse<Resume>> updateBasicInfo(
            Authentication auth, @RequestBody ResumeBasicInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateBasicInfo(getUserId(auth),
                        new ResumeBasicInfoCommand(
                                request.name(), request.phone(), request.email(),
                                request.gender(), request.birthDate(), request.address()))));
    }

    @PutMapping("/introduction")
    public ResponseEntity<ApiResponse<Resume>> updateIntroduction(
            Authentication auth, @RequestBody IntroductionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateIntroduction(getUserId(auth),
                        request.introduction(), request.selfIntroduction())));
    }

    @PutMapping("/desired-conditions")
    public ResponseEntity<ApiResponse<Resume>> updateDesiredConditions(
            Authentication auth, @RequestBody DesiredConditionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateDesiredConditions(getUserId(auth),
                        new DesiredConditionsCommand(
                                request.desiredSalary(), request.desiredEmploymentType(),
                                request.desiredLocation(), request.militaryStatus(),
                                request.disabilityStatus(), request.veteranStatus()))));
    }

    // ── 학력 ──

    @PostMapping("/educations")
    public ResponseEntity<ApiResponse<ResumeEducation>> addEducation(
            Authentication auth, @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addEducation(getUserId(auth),
                        new EducationCommand(
                                request.schoolType(), request.schoolName(), request.major(),
                                request.subMajor(), request.startDate(), request.endDate(),
                                request.graduationStatus(), request.gpa(), request.gpaScale()))));
    }

    @PutMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<ResumeEducation>> updateEducation(
            Authentication auth, @PathVariable Long id, @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateEducation(getUserId(auth), id,
                        new EducationCommand(
                                request.schoolType(), request.schoolName(), request.major(),
                                request.subMajor(), request.startDate(), request.endDate(),
                                request.graduationStatus(), request.gpa(), request.gpaScale()))));
    }

    @DeleteMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEducation(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteEducation(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 경력 ──

    @PostMapping("/careers")
    public ResponseEntity<ApiResponse<ResumeCareer>> addCareer(
            Authentication auth, @RequestBody CareerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addCareer(getUserId(auth),
                        new CareerCommand(
                                request.companyName(), request.department(), request.position(),
                                request.rank(), request.startDate(), request.endDate(),
                                request.currentlyWorking(), request.jobDescription(), request.salary()))));
    }

    @PutMapping("/careers/{id}")
    public ResponseEntity<ApiResponse<ResumeCareer>> updateCareer(
            Authentication auth, @PathVariable Long id, @RequestBody CareerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateCareer(getUserId(auth), id,
                        new CareerCommand(
                                request.companyName(), request.department(), request.position(),
                                request.rank(), request.startDate(), request.endDate(),
                                request.currentlyWorking(), request.jobDescription(), request.salary()))));
    }

    @DeleteMapping("/careers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCareer(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteCareer(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 스킬 ──

    @PostMapping("/skills")
    public ResponseEntity<ApiResponse<ResumeSkill>> addSkill(
            Authentication auth, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addSkill(getUserId(auth), body.get("skillName"))));
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteSkill(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 자격증 ──

    @PostMapping("/certifications")
    public ResponseEntity<ApiResponse<ResumeCertification>> addCertification(
            Authentication auth, @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addCertification(getUserId(auth),
                        new CertificationCommand(
                                request.certName(), request.issuingOrganization(), request.acquiredDate()))));
    }

    @PutMapping("/certifications/{id}")
    public ResponseEntity<ApiResponse<ResumeCertification>> updateCertification(
            Authentication auth, @PathVariable Long id, @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateCertification(getUserId(auth), id,
                        new CertificationCommand(
                                request.certName(), request.issuingOrganization(), request.acquiredDate()))));
    }

    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCertification(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteCertification(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 어학 ──

    @PostMapping("/languages")
    public ResponseEntity<ApiResponse<ResumeLanguage>> addLanguage(
            Authentication auth, @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addLanguage(getUserId(auth),
                        new LanguageCommand(
                                request.languageName(), request.examName(), request.score(),
                                request.grade(), request.examDate()))));
    }

    @PutMapping("/languages/{id}")
    public ResponseEntity<ApiResponse<ResumeLanguage>> updateLanguage(
            Authentication auth, @PathVariable Long id, @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateLanguage(getUserId(auth), id,
                        new LanguageCommand(
                                request.languageName(), request.examName(), request.score(),
                                request.grade(), request.examDate()))));
    }

    @DeleteMapping("/languages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLanguage(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteLanguage(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 활동/수상 ──

    @PostMapping("/activities")
    public ResponseEntity<ApiResponse<ResumeActivity>> addActivity(
            Authentication auth, @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addActivity(getUserId(auth),
                        new ActivityCommand(
                                request.activityType(), request.activityName(), request.organization(),
                                request.description(), request.startDate(), request.endDate()))));
    }

    @PutMapping("/activities/{id}")
    public ResponseEntity<ApiResponse<ResumeActivity>> updateActivity(
            Authentication auth, @PathVariable Long id, @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateActivity(getUserId(auth), id,
                        new ActivityCommand(
                                request.activityType(), request.activityName(), request.organization(),
                                request.description(), request.startDate(), request.endDate()))));
    }

    @DeleteMapping("/activities/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteActivity(
            Authentication auth, @PathVariable Long id) {
        resumeService.deleteActivity(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 포트폴리오 링크 ──

    @PostMapping("/portfolio-links")
    public ResponseEntity<ApiResponse<ResumePortfolioLink>> addPortfolioLink(
            Authentication auth, @RequestBody PortfolioLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addPortfolioLink(getUserId(auth),
                        new PortfolioLinkCommand(
                                request.linkType(), request.url(), request.description()))));
    }

    @DeleteMapping("/portfolio-links/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePortfolioLink(
            Authentication auth, @PathVariable Long id) {
        resumeService.deletePortfolioLink(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 이력서 연동 ──

    @PostMapping("/sync/{site}")
    public ResponseEntity<ApiResponse<ResumeSyncResult>> syncToSite(
            Authentication auth, @PathVariable String site) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.syncToSite(getUserId(auth), site)));
    }

    @PostMapping("/sync/all")
    public ResponseEntity<ApiResponse<java.util.Map<String, ResumeSyncResult>>> syncToAllSites(
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.syncToAllSites(getUserId(auth))));
    }

    @PostMapping("/import/{site}")
    public ResponseEntity<ApiResponse<SaraminResumeImporter.ImportResult>> importFromSite(
            @PathVariable String site, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.importFromSite(getUserId(auth), site)));
    }

    // ── helper ──

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
