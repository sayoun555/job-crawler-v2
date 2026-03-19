package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.coverletter.repository.CoverLetterRepository;
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
    private final CoverLetterRepository coverLetterRepository;
    private final GitHubRepoReader gitHubRepoReader;
    private final AiPromptDataBuilder promptDataBuilder;

    @Override
    @Transactional
    public int analyzeMatchScore(Long userId, Long jobPostingId) {
        return analyzeMatchScore(userId, jobPostingId, false);
    }

    @Transactional
    public int analyzeMatchScore(Long userId, Long jobPostingId, boolean force) {
        if (!force) {
            var cached = aiAnalysisResultRepository.findByUserIdAndJobPostingIdAndType(
                    userId, jobPostingId, AnalysisType.MATCH_SCORE);
            if (cached.isPresent() && cached.get().getScore() != null) {
                return cached.get().getScore();
            }
        }

        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);

        String jobString = promptDataBuilder.buildJobStringWithOcr(job);
        Map<String, Object> result = aiTextGenerator.calculateMatchScoreWithReason(
                promptDataBuilder.buildProfileString(profile), jobString);

        int score = (int) result.getOrDefault("totalScore", -1);
        job.updateAiMatchScore(score);

        // 근거를 JSON으로 저장
        String reasonJson = null;
        try {
            reasonJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        } catch (Exception ignored) {}

        saveOrUpdateAnalysis(userId, jobPostingId, AnalysisType.MATCH_SCORE, reasonJson, score);
        return score;
    }

    @Override
    public List<Project> matchProjects(Long userId, Long jobPostingId) {
        JobPosting job = findJob(jobPostingId);
        List<Project> myProjects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (myProjects.isEmpty()) return Collections.emptyList();

        Set<String> requiredSkills = job.getTechStack() != null
                ? new HashSet<>(job.getTechStack().toList().stream().map(String::toLowerCase).toList())
                : Collections.emptySet();
        if (requiredSkills.isEmpty())
            return myProjects.subList(0, Math.min(3, myProjects.size()));

        List<Project> matched = myProjects.stream()
                .filter(p -> hasOverlappingSkills(p.getTechStack(), requiredSkills))
                .limit(3)
                .collect(Collectors.toList());

        return matched.isEmpty()
                ? myProjects.subList(0, Math.min(3, myProjects.size()))
                : matched;
    }

    @Override
    public String generateCoverLetter(Long userId, Long jobPostingId, Long templateId) {
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);
        String projectsStr = buildMatchedProjectsSummary(userId, jobPostingId);

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER)
                        .userProfile(promptDataBuilder.buildProfileString(profile))
                        .jobDescription(promptDataBuilder.buildJobString(job))
                        .companyInfo(job.getCompany())
                        .matchedProjects(projectsStr)
                        .sourceSite(job.getSource().name())
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("자소서 생성 실패: " + result.getErrorMessage());

        return applyTemplate(templateId, result.getGeneratedText());
    }

    @Override
    public String generatePortfolio(Long userId, Long jobPostingId, Long templateId) {
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);
        String projectsStr = buildMatchedProjectsDetail(userId, jobPostingId);

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.PORTFOLIO)
                        .userProfile(promptDataBuilder.buildProfileString(profile))
                        .jobDescription(promptDataBuilder.buildJobString(job))
                        .matchedProjects(projectsStr)
                        .sourceSite(job.getSource().name())
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("포트폴리오 생성 실패: " + result.getErrorMessage());

        return applyTemplate(templateId, result.getGeneratedText());
    }

    @Override
    @Transactional
    public String analyzeCompany(Long userId, Long jobPostingId) {
        var cached = aiAnalysisResultRepository.findByJobPostingIdAndType(
                jobPostingId, AnalysisType.COMPANY_ANALYSIS);
        if (cached.isPresent() && cached.get().getResultText() != null) {
            return cached.get().getResultText();
        }

        JobPosting job = findJob(jobPostingId);

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
                        .jobDescription(promptDataBuilder.buildDetailedJobString(job))
                        .build());

        String analysisText = result.isSuccess() ? result.getGeneratedText() : "분석 실패: " + result.getErrorMessage();

        if (result.isSuccess()) {
            saveOrUpdateSharedAnalysis(jobPostingId, AnalysisType.COMPANY_ANALYSIS, analysisText);
        }
        return analysisText;
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

        return result.isSuccess()
                ? result.getGeneratedText()
                : "프로젝트 분석 실패: " + result.getErrorMessage();
    }

    @Override
    public String analyzeCoverLetterPattern(Long coverLetterId) {
        log.info("[AI] 자소서 패턴 분석 시작: coverLetterId={}", coverLetterId);

        var coverLetter = coverLetterRepository.findById(coverLetterId)
                .orElseThrow(() -> new CustomException(ErrorCode.COVER_LETTER_NOT_FOUND));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER_ANALYSIS)
                        .companyInfo(coverLetter.getCompany())
                        .jobDescription(coverLetter.getPosition())
                        .sourceSite(coverLetter.getCompanyType())
                        .matchedProjects(coverLetter.getContent())
                        .build());

        return result.isSuccess()
                ? result.getGeneratedText()
                : "자소서 패턴 분석 실패: " + result.getErrorMessage();
    }

    // --- private helpers ---

    private UserProfile findProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private JobPosting findJob(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSTING_NOT_FOUND));
    }

    private String buildMatchedProjectsSummary(Long userId, Long jobPostingId) {
        return matchProjects(userId, jobPostingId).stream()
                .map(p -> p.getName() + ": " + p.getDescription())
                .collect(Collectors.joining("\n"));
    }

    private String buildMatchedProjectsDetail(Long userId, Long jobPostingId) {
        return matchProjects(userId, jobPostingId).stream()
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
    }

    private boolean hasOverlappingSkills(String projectTechStack, Set<String> requiredSkills) {
        if (projectTechStack == null || projectTechStack.isBlank()) return false;
        Set<String> projSkills = Arrays.stream(projectTechStack.split("[,;/\\s]+"))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        projSkills.retainAll(requiredSkills);
        return !projSkills.isEmpty();
    }

    private String applyTemplate(Long templateId, String generatedText) {
        if (templateId == null) return generatedText;
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
        return template.applyVariables(Map.of("content", generatedText));
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

    private void saveOrUpdateSharedAnalysis(Long jobPostingId, AnalysisType type, String text) {
        var existing = aiAnalysisResultRepository.findByJobPostingIdAndType(jobPostingId, type);
        if (existing.isPresent()) {
            existing.get().updateResult(text, null);
        } else {
            aiAnalysisResultRepository.save(AiAnalysisResult.builder()
                    .userId(0L)
                    .jobPostingId(jobPostingId)
                    .type(type)
                    .resultText(text)
                    .build());
        }
    }
}
