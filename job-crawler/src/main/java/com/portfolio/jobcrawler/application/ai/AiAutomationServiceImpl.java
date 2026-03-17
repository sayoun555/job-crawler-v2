package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import com.portfolio.jobcrawler.domain.project.entity.Project;
import com.portfolio.jobcrawler.domain.project.repository.ProjectRepository;
import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.repository.TemplateRepository;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.domain.user.repository.UserProfileRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.domain.aianalysis.entity.AiAnalysisResult;
import com.portfolio.jobcrawler.domain.aianalysis.repository.AiAnalysisResultRepository;
import com.portfolio.jobcrawler.domain.aianalysis.vo.AnalysisType;
import com.portfolio.jobcrawler.infrastructure.ai.AiTextGenerator;
import com.portfolio.jobcrawler.infrastructure.ai.CompanyWebSearcher;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationResult;
import com.portfolio.jobcrawler.infrastructure.github.GitHubRepoReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAutomationServiceImpl implements AiAutomationService {

    private final AiTextGenerator aiTextGenerator;
    private final CompanyWebSearcher companyWebSearcher;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final GitHubRepoReader gitHubRepoReader;

    @Override
    @Transactional
    public int analyzeMatchScore(Long userId, Long jobPostingId) {
        // 캐시 확인
        var cached = aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(
                userId, jobPostingId, AnalysisType.MATCH_SCORE);
        if (cached.isPresent() && cached.get().getScore() != null) {
            return cached.get().getScore();
        }

        UserProfile profile = getProfile(userId);
        JobPosting job = getJob(jobPostingId);

        int score = aiTextGenerator.calculateMatchScore(buildProfileString(profile), buildJobString(job));
        job.updateAiMatchScore(score);

        // 유저별 저장
        saveOrUpdateAnalysis(userId, jobPostingId, AnalysisType.MATCH_SCORE, null, score);
        return score;
    }

    @Override
    public List<Project> matchProjects(Long userId, Long jobPostingId) {
        JobPosting job = getJob(jobPostingId);
        List<Project> myProjects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (myProjects.isEmpty())
            return Collections.emptyList();

        Set<String> requiredSkills = job.getTechStack() != null
                ? new java.util.HashSet<>(job.getTechStack().toList().stream().map(String::toLowerCase).toList())
                : Collections.emptySet();
        if (requiredSkills.isEmpty())
            return myProjects.subList(0, Math.min(3, myProjects.size()));

        List<Project> matched = myProjects.stream()
                .filter(p -> {
                    Set<String> projSkills = parseSkills(p.getTechStack());
                    projSkills.retainAll(requiredSkills);
                    return !projSkills.isEmpty();
                })
                .limit(3)
                .collect(Collectors.toList());

        // 매칭되는 프로젝트가 없으면 전체 프로젝트 반환 (AI가 판단)
        if (matched.isEmpty()) {
            return myProjects.subList(0, Math.min(3, myProjects.size()));
        }
        return matched;
    }

    @Override
    public String generateCoverLetter(Long userId, Long jobPostingId, Long templateId) {
        UserProfile profile = getProfile(userId);
        JobPosting job = getJob(jobPostingId);
        List<Project> matched = matchProjects(userId, jobPostingId);
        String projectsStr = matched.stream()
                .map(p -> p.getName() + ": " + p.getDescription())
                .collect(Collectors.joining("\n"));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER)
                        .userProfile(buildProfileString(profile))
                        .jobDescription(buildJobString(job))
                        .companyInfo(job.getCompany())
                        .matchedProjects(projectsStr)
                        .sourceSite(job.getSource().name())
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("자소서 생성 실패: " + result.getErrorMessage());

        if (templateId != null) {
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
            return template.applyVariables(Map.of("content", result.getGeneratedText()));
        }
        return result.getGeneratedText();
    }

    @Override
    public String generatePortfolio(Long userId, Long jobPostingId, Long templateId) {
        UserProfile profile = getProfile(userId);
        JobPosting job = getJob(jobPostingId);
        List<Project> matched = matchProjects(userId, jobPostingId);
        String projectsStr = matched.stream()
                .map(p -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("프로젝트명: ").append(p.getName()).append("\n");
                    if (p.getDescription() != null) sb.append("설명: ").append(p.getDescription()).append("\n");
                    if (p.getTechStack() != null) sb.append("기술스택: ").append(p.getTechStack()).append("\n");
                    if (p.getGithubUrl() != null) sb.append("GitHub: ").append(p.getGithubUrl()).append("\n");
                    if (p.getAiSummary() != null) sb.append("AI분석: ").append(p.getAiSummary()).append("\n");
                    return sb.toString();
                })
                .collect(Collectors.joining("\n---\n"));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.PORTFOLIO)
                        .userProfile(buildProfileString(profile))
                        .jobDescription(buildJobString(job))
                        .matchedProjects(projectsStr)
                        .sourceSite(job.getSource().name())
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("포트폴리오 생성 실패: " + result.getErrorMessage());

        if (templateId != null) {
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
            return template.applyVariables(Map.of("content", result.getGeneratedText()));
        }
        return result.getGeneratedText();
    }

    @Override
    @Transactional
    public String analyzeCompany(Long userId, Long jobPostingId) {
        // 캐시 확인
        var cached = aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(
                userId, jobPostingId, AnalysisType.COMPANY_ANALYSIS);
        if (cached.isPresent() && cached.get().getResultText() != null) {
            log.info("[AI] 캐시된 기업 분석 반환 - userId: {}, jobId: {}", userId, jobPostingId);
            return cached.get().getResultText();
        }

        JobPosting job = getJob(jobPostingId);

        // 웹 검색으로 기업 정보 수집
        log.info("[AI] '{}' 기업 웹 검색 시작", job.getCompany());
        String webSearchResult = companyWebSearcher.searchCompanyInfo(job.getCompany());

        String companyInfo = job.getCompany();
        if (!webSearchResult.isEmpty()) {
            companyInfo += "\n\n[웹 검색으로 수집한 기업 정보]\n" + webSearchResult;
        }

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COMPANY_ANALYSIS)
                        .companyInfo(companyInfo)
                        .jobDescription(buildDetailedJobString(job))
                        .build());

        String analysisText = result.isSuccess() ? result.getGeneratedText() : "분석 실패: " + result.getErrorMessage();

        // 유저별 저장
        if (result.isSuccess()) {
            saveOrUpdateAnalysis(userId, jobPostingId, AnalysisType.COMPANY_ANALYSIS, analysisText, null);
        }

        return analysisText;
    }

    private void saveOrUpdateAnalysis(Long userId, Long jobPostingId, AnalysisType type, String text, Integer score) {
        var existing = aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(userId, jobPostingId, type);
        if (existing.isPresent()) {
            existing.get().updateResult(text, score);
        } else {
            aiAnalysisResultRepository.save(AiAnalysisResult.builder()
                    .userId(userId)
                    .jobPostingId(jobPostingId)
                    .type(type)
                    .resultText(text)
                    .score(score)
                    .build());
        }
    }

    private String buildDetailedJobString(JobPosting j) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(j.getTitle()).append("\n");
        sb.append("회사: ").append(j.getCompany()).append("\n");
        if (j.getLocation() != null) sb.append("위치: ").append(j.getLocation()).append("\n");
        if (j.getEducation() != null) sb.append("학력: ").append(j.getEducation()).append("\n");
        if (j.getCareer() != null) sb.append("경력: ").append(j.getCareer()).append("\n");
        if (j.getSalary() != null && !j.getSalary().isBlank()) sb.append("급여: ").append(j.getSalary()).append("\n");
        if (j.getJobCategory() != null) sb.append("직무: ").append(j.getJobCategory()).append("\n");
        if (j.getTechStack() != null) sb.append("기술스택: ").append(j.getTechStack().toString()).append("\n");
        if (j.getRequirements() != null && !j.getRequirements().isBlank())
            sb.append("자격요건/우대사항:\n").append(j.getRequirements()).append("\n");
        if (j.getDescription() != null && !j.getDescription().isBlank()) {
            String desc = j.getDescription();
            if (desc.length() > 3000) desc = desc.substring(0, 3000) + "...";
            sb.append("상세내용:\n").append(desc);
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public String summarizeProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        String summary = aiTextGenerator.summarizeProject(project.getDescription(), project.getTechStack());
        project.updateAiSummary(summary);
        return summary;
    }

    /**
     * GitHub URL로 프로젝트 AI 분석 (Step 4.4).
     * 1) GitHubRepoReader로 README, 빌드 파일, 소스 구조 읽기
     * 2) AI에게 프로젝트 개요/기술 스택/아키텍처/주요 기능 정리 요청
     */
    @Override
    public String analyzeGitHubProject(String githubUrl) {
        log.info("[AI] GitHub 프로젝트 분석 시작: {}", githubUrl);

        String repoData = gitHubRepoReader.readRepoForAnalysis(githubUrl);

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.PROJECT_SUMMARY)
                        .matchedProjects(repoData)
                        .companyInfo("GitHub 레포지토리 분석")
                        .jobDescription("다음 GitHub 레포지토리 데이터를 분석하여 프로젝트 개요, 기술 스택, "
                                + "아키텍처 패턴, 주요 기능을 정리해주세요. "
                                + "마크다운 문법(*, #, ``` 등)은 절대 사용하지 마세요. 순수 텍스트로만 작성하세요.")
                        .build());

        if (result.isSuccess()) {
            return result.getGeneratedText();
        }
        return "프로젝트 분석 실패: " + result.getErrorMessage();
    }

    // --- private helpers ---

    private UserProfile getProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private JobPosting getJob(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));
    }

    private String buildProfileString(UserProfile p) {
        return "학력: " + nullSafe(p.getEducation()) +
                "\n경력: " + nullSafe(p.getCareer()) +
                "\n자격증: " + nullSafe(p.getCertifications()) +
                "\n기술스택: " + nullSafe(p.getTechStack()) +
                "\n강점: " + nullSafe(p.getStrengths());
    }

    private String buildJobString(JobPosting j) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(j.getTitle());
        sb.append("\n회사: ").append(j.getCompany());
        sb.append("\n위치: ").append(nullSafe(j.getLocation()));
        sb.append("\n학력: ").append(nullSafe(j.getEducation()));
        sb.append("\n경력: ").append(nullSafe(j.getCareer()));
        sb.append("\n급여: ").append(nullSafe(j.getSalary()));
        sb.append("\n직무: ").append(nullSafe(j.getJobCategory()));
        sb.append("\n기술스택: ").append(j.getTechStack() != null ? j.getTechStack().toString() : "");
        if (j.getRequirements() != null && !j.getRequirements().isBlank()) {
            sb.append("\n자격요건/우대사항:\n").append(j.getRequirements());
        }
        if (j.getDescription() != null && !j.getDescription().isBlank()) {
            String desc = j.getDescription();
            if (desc.length() > 3000) desc = desc.substring(0, 3000) + "...";
            sb.append("\n상세내용:\n").append(desc);
        }
        return sb.toString();
    }

    private Set<String> parseSkills(String techStack) {
        if (techStack == null || techStack.isBlank())
            return Collections.emptySet();
        return Arrays.stream(techStack.split("[,;/\\s]+"))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
