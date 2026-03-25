package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.application.ai.profile.ProfileBuildHelper;
import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.resume.repository.ResumeRepository;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import com.portfolio.jobcrawler.global.util.ImageOcrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 프롬프트용 데이터 빌더 (SRP: 프롬프트 데이터 조립만 담당).
 * 사이트별 프로필 강조 로직은 SiteProfileStrategy에 위임한다.
 */
@Slf4j
@Component
public class AiPromptDataBuilder {

    private final ResumeRepository resumeRepository;
    private final Map<String, SiteProfileStrategy> strategies;

    public AiPromptDataBuilder(ResumeRepository resumeRepository,
                               List<SiteProfileStrategy> strategyList) {
        this.resumeRepository = resumeRepository;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(SiteProfileStrategy::getSiteName, Function.identity()));
    }

    /**
     * 사이트별로 최적화된 프로필 문자열을 생성한다.
     */
    public String buildProfileString(UserProfile profile, SourceSite site) {
        SiteProfileStrategy strategy = site != null ? strategies.get(site.name()) : null;

        boolean profileEmpty = profile.getTechStack() == null
                && profile.getCareer() == null
                && profile.getEducation() == null;

        if (profileEmpty) {
            Long userId = profile.getUser().getId();
            // 사이트별 이력서 우선 (최신) → 없으면 마스터 이력서 fallback
            java.util.Optional<com.portfolio.jobcrawler.domain.resume.entity.Resume> resume =
                    site != null
                            ? resumeRepository.findFirstByUserIdAndSourceSiteOrderByUpdatedAtDesc(userId, site)
                                    .or(() -> resumeRepository.findByUserIdAndSourceSiteIsNull(userId))
                            : resumeRepository.findByUserIdAndSourceSiteIsNull(userId);

            return resume
                    .map(r -> {
                        log.info("[AiPromptDataBuilder] 이력서 사용: {} (id={}, site={})",
                                r.isMaster() ? "마스터" : r.getSourceSite().name(),
                                r.getId(), site);
                        return strategy != null
                                ? strategy.buildFromResume(r)
                                : defaultResumeString(r);
                    })
                    .orElse("프로필 정보 없음");
        }

        return strategy != null
                ? strategy.buildFromProfile(profile)
                : defaultProfileString(profile);
    }

    /** 하위 호환 (사이트 미지정 시) */
    public String buildProfileString(UserProfile profile) {
        return buildProfileString(profile, null);
    }

    public String buildJobString(JobPosting job) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(job.getTitle());
        sb.append("\n회사: ").append(job.getCompany());
        sb.append("\n위치: ").append(nullSafe(job.getLocation()));
        sb.append("\n학력: ").append(nullSafe(job.getEducation()));
        sb.append("\n경력: ").append(nullSafe(job.getCareer()));
        sb.append("\n급여: ").append(nullSafe(job.getSalary()));
        sb.append("\n직무: ").append(nullSafe(job.getJobCategory()));
        sb.append("\n기술스택: ").append(job.getTechStack() != null ? job.getTechStack().toString() : "");
        if (job.getRequirements() != null && !job.getRequirements().isBlank()) {
            sb.append("\n자격요건/우대사항:\n").append(job.getRequirements());
        }
        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            String desc = HtmlSanitizer.toPlainText(job.getDescription());
            if (desc.length() > 5000) desc = desc.substring(0, 5000) + "...";
            sb.append("\n상세내용:\n").append(desc);
        }
        return sb.toString();
    }

    public String buildDetailedJobString(JobPosting job) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(job.getTitle()).append("\n");
        sb.append("회사: ").append(job.getCompany()).append("\n");
        if (job.getLocation() != null) sb.append("위치: ").append(job.getLocation()).append("\n");
        if (job.getEducation() != null) sb.append("학력: ").append(job.getEducation()).append("\n");
        if (job.getCareer() != null) sb.append("경력: ").append(job.getCareer()).append("\n");
        if (job.getSalary() != null && !job.getSalary().isBlank()) sb.append("급여: ").append(job.getSalary()).append("\n");
        if (job.getJobCategory() != null) sb.append("직무: ").append(job.getJobCategory()).append("\n");
        if (job.getTechStack() != null) sb.append("기술스택: ").append(job.getTechStack().toString()).append("\n");
        if (job.getRequirements() != null && !job.getRequirements().isBlank())
            sb.append("자격요건/우대사항:\n").append(job.getRequirements()).append("\n");
        if (job.getDescription() != null && !job.getDescription().isBlank()) {
            String desc = job.getDescription();
            if (desc.length() > 3000) desc = desc.substring(0, 3000) + "...";
            sb.append("상세내용:\n").append(desc);
        }
        return sb.toString();
    }

    public String buildJobStringWithOcr(JobPosting job) {
        String jobString = buildJobString(job);
        if (job.getCompanyImages() != null && !job.getCompanyImages().isBlank()) {
            List<String> imageUrls = Arrays.stream(job.getCompanyImages().split(","))
                    .map(String::trim).filter(u -> u.startsWith("http")).limit(3).toList();
            String ocrText = ImageOcrUtil.extractTextFromImages(imageUrls);
            if (!ocrText.isBlank()) {
                jobString += "\n\n[이미지에서 추출한 텍스트]\n" + ocrText;
            }
        }
        return jobString;
    }

    private String defaultProfileString(UserProfile profile) {
        return "학력: " + nullSafe(profile.getEducation()) +
                "\n경력: " + nullSafe(profile.getCareer()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n기술스택: " + nullSafe(profile.getTechStack()) +
                "\n강점: " + nullSafe(profile.getStrengths());
    }

    private String defaultResumeString(com.portfolio.jobcrawler.domain.resume.entity.Resume resume) {
        StringBuilder sb = new StringBuilder();
        sb.append("이름: ").append(nullSafe(resume.getName()));
        ProfileBuildHelper.appendEducations(sb, resume);
        ProfileBuildHelper.appendCareers(sb, resume);
        ProfileBuildHelper.appendSkills(sb, resume);
        ProfileBuildHelper.appendCertifications(sb, resume);
        ProfileBuildHelper.appendActivities(sb, resume);
        return sb.toString();
    }

    /**
     * 프로필 문자열에서 학력 관련 줄을 제거한다.
     * 자소서/포트폴리오에는 학력 정보를 포함하지 않기 위해 사용.
     */
    public String stripEducation(String profileString) {
        if (profileString == null) return "";
        return java.util.Arrays.stream(profileString.split("\n"))
                .filter(line -> {
                    String trimmed = line.trim().toLowerCase();
                    return !trimmed.startsWith("학력") && !trimmed.startsWith("education")
                            && !trimmed.contains("대학교") && !trimmed.contains("대학원")
                            && !trimmed.contains("university") && !trimmed.contains("college")
                            && !trimmed.contains("졸업") && !trimmed.contains("학점");
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
