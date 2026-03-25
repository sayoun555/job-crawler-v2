package com.portfolio.jobcrawler.api.resume;

import com.portfolio.jobcrawler.api.resume.dto.*;
import com.portfolio.jobcrawler.application.resume.ResumeService;
import com.portfolio.jobcrawler.application.resume.dto.*;
import com.portfolio.jobcrawler.domain.resume.entity.*;
import com.portfolio.jobcrawler.global.dto.ApiResponse;
import com.portfolio.jobcrawler.infrastructure.resumesync.ResumeSyncResult;
import com.portfolio.jobcrawler.infrastructure.resumesync.importer.SaraminResumeImporter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "이력서", description = "이력서 CRUD, 학력/경력/스킬 등 섹션 관리, 사이트 동기화")
@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    // ── 기본 이력서 ──

    @Operation(summary = "내 이력서 조회 (없으면 자동 생성)")
    @GetMapping
    public ResponseEntity<ApiResponse<Resume>> getResume(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.getOrCreateResume(getUserId(auth))));
    }

    @Operation(summary = "기본 정보 수정")
    @PutMapping("/basic-info")
    public ResponseEntity<ApiResponse<Resume>> updateBasicInfo(
            Authentication auth, @RequestBody ResumeBasicInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateBasicInfo(getUserId(auth),
                        new ResumeBasicInfoCommand(
                                request.name(), request.phone(), request.email(),
                                request.gender(), request.birthDate(), request.address()))));
    }

    @Operation(summary = "자기소개 수정")
    @PutMapping("/introduction")
    public ResponseEntity<ApiResponse<Resume>> updateIntroduction(
            Authentication auth, @RequestBody IntroductionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateIntroduction(getUserId(auth),
                        request.introduction(), request.selfIntroduction())));
    }

    @Operation(summary = "희망 조건 수정")
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

    @Operation(summary = "학력 추가")
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

    @Operation(summary = "학력 수정")
    @PutMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<ResumeEducation>> updateEducation(
            Authentication auth, @Parameter(description = "학력 ID") @PathVariable Long id, @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateEducation(getUserId(auth), id,
                        new EducationCommand(
                                request.schoolType(), request.schoolName(), request.major(),
                                request.subMajor(), request.startDate(), request.endDate(),
                                request.graduationStatus(), request.gpa(), request.gpaScale()))));
    }

    @Operation(summary = "학력 삭제")
    @DeleteMapping("/educations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEducation(
            Authentication auth, @Parameter(description = "학력 ID") @PathVariable Long id) {
        resumeService.deleteEducation(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 경력 ──

    @Operation(summary = "경력 추가")
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

    @Operation(summary = "경력 수정")
    @PutMapping("/careers/{id}")
    public ResponseEntity<ApiResponse<ResumeCareer>> updateCareer(
            Authentication auth, @Parameter(description = "경력 ID") @PathVariable Long id, @RequestBody CareerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateCareer(getUserId(auth), id,
                        new CareerCommand(
                                request.companyName(), request.department(), request.position(),
                                request.rank(), request.startDate(), request.endDate(),
                                request.currentlyWorking(), request.jobDescription(), request.salary()))));
    }

    @Operation(summary = "경력 삭제")
    @DeleteMapping("/careers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCareer(
            Authentication auth, @Parameter(description = "경력 ID") @PathVariable Long id) {
        resumeService.deleteCareer(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 스킬 ──

    @Operation(summary = "스킬 추가")
    @PostMapping("/skills")
    public ResponseEntity<ApiResponse<ResumeSkill>> addSkill(
            Authentication auth,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "스킬 이름",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"skillName\": \"Java\"}")))
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addSkill(getUserId(auth), body.get("skillName"))));
    }

    @Operation(summary = "스킬 삭제")
    @DeleteMapping("/skills/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(
            Authentication auth, @Parameter(description = "스킬 ID") @PathVariable Long id) {
        resumeService.deleteSkill(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 자격증 ──

    @Operation(summary = "자격증 추가")
    @PostMapping("/certifications")
    public ResponseEntity<ApiResponse<ResumeCertification>> addCertification(
            Authentication auth, @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addCertification(getUserId(auth),
                        new CertificationCommand(
                                request.certName(), request.issuingOrganization(), request.acquiredDate()))));
    }

    @Operation(summary = "자격증 수정")
    @PutMapping("/certifications/{id}")
    public ResponseEntity<ApiResponse<ResumeCertification>> updateCertification(
            Authentication auth, @Parameter(description = "자격증 ID") @PathVariable Long id, @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateCertification(getUserId(auth), id,
                        new CertificationCommand(
                                request.certName(), request.issuingOrganization(), request.acquiredDate()))));
    }

    @Operation(summary = "자격증 삭제")
    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCertification(
            Authentication auth, @Parameter(description = "자격증 ID") @PathVariable Long id) {
        resumeService.deleteCertification(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 어학 ──

    @Operation(summary = "어학 추가")
    @PostMapping("/languages")
    public ResponseEntity<ApiResponse<ResumeLanguage>> addLanguage(
            Authentication auth, @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addLanguage(getUserId(auth),
                        new LanguageCommand(
                                request.languageName(), request.examName(), request.score(),
                                request.grade(), request.examDate()))));
    }

    @Operation(summary = "어학 수정")
    @PutMapping("/languages/{id}")
    public ResponseEntity<ApiResponse<ResumeLanguage>> updateLanguage(
            Authentication auth, @Parameter(description = "어학 ID") @PathVariable Long id, @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateLanguage(getUserId(auth), id,
                        new LanguageCommand(
                                request.languageName(), request.examName(), request.score(),
                                request.grade(), request.examDate()))));
    }

    @Operation(summary = "어학 삭제")
    @DeleteMapping("/languages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLanguage(
            Authentication auth, @Parameter(description = "어학 ID") @PathVariable Long id) {
        resumeService.deleteLanguage(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 활동/수상 ──

    @Operation(summary = "활동/수상 추가")
    @PostMapping("/activities")
    public ResponseEntity<ApiResponse<ResumeActivity>> addActivity(
            Authentication auth, @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addActivity(getUserId(auth),
                        new ActivityCommand(
                                request.activityType(), request.activityName(), request.organization(),
                                request.description(), request.startDate(), request.endDate()))));
    }

    @Operation(summary = "활동/수상 수정")
    @PutMapping("/activities/{id}")
    public ResponseEntity<ApiResponse<ResumeActivity>> updateActivity(
            Authentication auth, @Parameter(description = "활동/수상 ID") @PathVariable Long id, @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateActivity(getUserId(auth), id,
                        new ActivityCommand(
                                request.activityType(), request.activityName(), request.organization(),
                                request.description(), request.startDate(), request.endDate()))));
    }

    @Operation(summary = "활동/수상 삭제")
    @DeleteMapping("/activities/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteActivity(
            Authentication auth, @Parameter(description = "활동/수상 ID") @PathVariable Long id) {
        resumeService.deleteActivity(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 포트폴리오 링크 ──

    @Operation(summary = "포트폴리오 링크 추가")
    @PostMapping("/portfolio-links")
    public ResponseEntity<ApiResponse<ResumePortfolioLink>> addPortfolioLink(
            Authentication auth, @RequestBody PortfolioLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addPortfolioLink(getUserId(auth),
                        new PortfolioLinkCommand(
                                request.linkType(), request.url(), request.description()))));
    }

    @Operation(summary = "포트폴리오 링크 삭제")
    @DeleteMapping("/portfolio-links/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePortfolioLink(
            Authentication auth, @Parameter(description = "포트폴리오 링크 ID") @PathVariable Long id) {
        resumeService.deletePortfolioLink(getUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── 이력서 연동 ──

    @Operation(summary = "특정 사이트로 이력서 동기화")
    @PostMapping("/sync/{site}")
    public ResponseEntity<ApiResponse<ResumeSyncResult>> syncToSite(
            Authentication auth, @Parameter(description = "대상 사이트 (SARAMIN, JOBPLANET 등)") @PathVariable String site) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.syncToSite(getUserId(auth), site)));
    }

    @Operation(summary = "전체 사이트로 이력서 동기화")
    @PostMapping("/sync/all")
    public ResponseEntity<ApiResponse<java.util.Map<String, ResumeSyncResult>>> syncToAllSites(
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.syncToAllSites(getUserId(auth))));
    }

    @Operation(summary = "외부 사이트에서 이력서 가져오기")
    @PostMapping("/import/{site}")
    public ResponseEntity<ApiResponse<SaraminResumeImporter.ImportResult>> importFromSite(
            @Parameter(description = "출처 사이트 (SARAMIN, JOBPLANET 등)") @PathVariable String site, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.importFromSite(getUserId(auth), site)));
    }

    // ── 사이트별 이력서 수정 (resumeId 지정) ──

    @Operation(summary = "특정 이력서 조회 (resumeId 지정)")
    @GetMapping("/{resumeId}")
    public ResponseEntity<ApiResponse<Resume>> getResumeById(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.getResumeById(getUserId(auth), resumeId)));
    }

    @Operation(summary = "사이트별 이력서 기본 정보 수정")
    @PutMapping("/{resumeId}/basic-info")
    public ResponseEntity<ApiResponse<Resume>> updateSiteBasicInfo(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody ResumeBasicInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateBasicInfo(getUserId(auth), resumeId,
                        new ResumeBasicInfoCommand(
                                request.name(), request.phone(), request.email(),
                                request.gender(), request.birthDate(), request.address()))));
    }

    @Operation(summary = "사이트별 이력서 자기소개 수정")
    @PutMapping("/{resumeId}/introduction")
    public ResponseEntity<ApiResponse<Resume>> updateSiteIntroduction(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody IntroductionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateIntroduction(getUserId(auth), resumeId,
                        request.introduction(), request.selfIntroduction())));
    }

    @Operation(summary = "사이트별 이력서 희망 조건 수정")
    @PutMapping("/{resumeId}/desired-conditions")
    public ResponseEntity<ApiResponse<Resume>> updateSiteDesiredConditions(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody DesiredConditionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.updateDesiredConditions(getUserId(auth), resumeId,
                        new DesiredConditionsCommand(
                                request.desiredSalary(), request.desiredEmploymentType(),
                                request.desiredLocation(), request.militaryStatus(),
                                request.disabilityStatus(), request.veteranStatus()))));
    }

    @Operation(summary = "사이트별 이력서에 학력 추가")
    @PostMapping("/{resumeId}/educations")
    public ResponseEntity<ApiResponse<ResumeEducation>> addSiteEducation(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addEducation(getUserId(auth), resumeId,
                        new EducationCommand(
                                request.schoolType(), request.schoolName(), request.major(),
                                request.subMajor(), request.startDate(), request.endDate(),
                                request.graduationStatus(), request.gpa(), request.gpaScale()))));
    }

    @Operation(summary = "사이트별 이력서에 경력 추가")
    @PostMapping("/{resumeId}/careers")
    public ResponseEntity<ApiResponse<ResumeCareer>> addSiteCareer(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody CareerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addCareer(getUserId(auth), resumeId,
                        new CareerCommand(
                                request.companyName(), request.department(), request.position(),
                                request.rank(), request.startDate(), request.endDate(),
                                request.currentlyWorking(), request.jobDescription(), request.salary()))));
    }

    @Operation(summary = "사이트별 이력서에 스킬 추가")
    @PostMapping("/{resumeId}/skills")
    public ResponseEntity<ApiResponse<ResumeSkill>> addSiteSkill(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "스킬 이름",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    example = "{\"skillName\": \"Java\"}")))
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addSkill(getUserId(auth), resumeId, body.get("skillName"))));
    }

    @Operation(summary = "사이트별 이력서에 자격증 추가")
    @PostMapping("/{resumeId}/certifications")
    public ResponseEntity<ApiResponse<ResumeCertification>> addSiteCertification(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody CertificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addCertification(getUserId(auth), resumeId,
                        new CertificationCommand(
                                request.certName(), request.issuingOrganization(), request.acquiredDate()))));
    }

    @Operation(summary = "사이트별 이력서에 어학 추가")
    @PostMapping("/{resumeId}/languages")
    public ResponseEntity<ApiResponse<ResumeLanguage>> addSiteLanguage(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addLanguage(getUserId(auth), resumeId,
                        new LanguageCommand(
                                request.languageName(), request.examName(), request.score(),
                                request.grade(), request.examDate()))));
    }

    @Operation(summary = "사이트별 이력서에 활동/수상 추가")
    @PostMapping("/{resumeId}/activities")
    public ResponseEntity<ApiResponse<ResumeActivity>> addSiteActivity(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addActivity(getUserId(auth), resumeId,
                        new ActivityCommand(
                                request.activityType(), request.activityName(), request.organization(),
                                request.description(), request.startDate(), request.endDate()))));
    }

    @Operation(summary = "사이트별 이력서에 포트폴리오 링크 추가")
    @PostMapping("/{resumeId}/portfolio-links")
    public ResponseEntity<ApiResponse<ResumePortfolioLink>> addSitePortfolioLink(
            Authentication auth, @Parameter(description = "이력서 ID") @PathVariable Long resumeId,
            @RequestBody PortfolioLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.addPortfolioLink(getUserId(auth), resumeId,
                        new PortfolioLinkCommand(
                                request.linkType(), request.url(), request.description()))));
    }

    // ── 사이트별 이력서 ──

    @Operation(summary = "사이트별 이력서 목록 조회")
    @GetMapping("/site/{site}")
    public ResponseEntity<ApiResponse<java.util.List<Resume>>> getSiteResumes(
            Authentication auth, @Parameter(description = "사이트 이름") @PathVariable String site) {
        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.getSiteResumes(getUserId(auth), site)));
    }

    @Operation(summary = "전체 이력서 목록 조회")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<java.util.List<Resume>>> getAllResumes(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.getAllResumes(getUserId(auth))));
    }

    // ── helper ──

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
