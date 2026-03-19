package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import com.portfolio.jobcrawler.domain.resume.repository.ResumeRepository;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.global.util.HtmlSanitizer;
import com.portfolio.jobcrawler.global.util.ImageOcrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiPromptDataBuilder {

    private final ResumeRepository resumeRepository;

    public String buildProfileString(UserProfile profile) {
        boolean profileEmpty = profile.getTechStack() == null
                && profile.getCareer() == null
                && profile.getEducation() == null;

        if (profileEmpty) {
            return resumeRepository.findByUserId(profile.getUser().getId())
                    .map(this::buildFromResume)
                    .orElse("프로필 정보 없음");
        }

        return "학력: " + nullSafe(profile.getEducation()) +
                "\n경력: " + nullSafe(profile.getCareer()) +
                "\n자격증: " + nullSafe(profile.getCertifications()) +
                "\n기술스택: " + nullSafe(profile.getTechStack()) +
                "\n강점: " + nullSafe(profile.getStrengths());
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
            if (desc.length() > 2000) desc = desc.substring(0, 2000) + "...";
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

    private String buildFromResume(Resume resume) {
        StringBuilder sb = new StringBuilder();
        sb.append("이름: ").append(nullSafe(resume.getName()));

        if (!resume.getEducations().isEmpty()) {
            sb.append("\n학력: ");
            resume.getEducations().forEach(e ->
                    sb.append(e.getSchoolName()).append(" ").append(e.getMajor()).append(", "));
        }
        if (!resume.getCareers().isEmpty()) {
            sb.append("\n경력: ");
            resume.getCareers().forEach(c ->
                    sb.append(c.getCompanyName()).append(" ").append(nullSafe(c.getPosition()))
                            .append(" ").append(nullSafe(c.getJobDescription())).append(", "));
        }
        if (!resume.getSkills().isEmpty()) {
            sb.append("\n기술스택: ");
            resume.getSkills().forEach(s -> sb.append(s.getSkillName()).append(", "));
        }
        if (!resume.getCertifications().isEmpty()) {
            sb.append("\n자격증: ");
            resume.getCertifications().forEach(c -> sb.append(c.getCertName()).append(", "));
        }
        if (!resume.getActivities().isEmpty()) {
            sb.append("\n활동: ");
            resume.getActivities().forEach(a ->
                    sb.append(a.getActivityName()).append(" ").append(nullSafe(a.getDescription())).append(", "));
        }
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
