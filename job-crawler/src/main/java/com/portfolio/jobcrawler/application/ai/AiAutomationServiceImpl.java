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

import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final com.portfolio.jobcrawler.infrastructure.notion.NotionPageReader notionPageReader;
    private final AiPromptDataBuilder promptDataBuilder;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

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

        // 프로필 + 프로젝트 데이터를 합쳐서 적합률 분석에 제공
        String profileStr = promptDataBuilder.buildProfileString(profile, job.getSource());
        String projectsStr = buildMatchedProjectsDetail(userId, jobPostingId);
        if (!projectsStr.isBlank()) {
            profileStr += "\n\n[보유 프로젝트]\n" + projectsStr;
        }

        String jobString = promptDataBuilder.buildJobStringWithOcr(job);
        Map<String, Object> result = aiTextGenerator.calculateMatchScoreWithReason(profileStr, jobString);

        int score = (int) result.getOrDefault("totalScore", -1);

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
        ProjectData projectData = buildMatchedProjectsData(userId, jobPostingId);

        String companyAnalysis = extractRelevantAnalysis(getOrCreateCompanyAnalysis(userId, jobPostingId, job));

        String profileStr = promptDataBuilder.stripEducation(
                promptDataBuilder.buildProfileString(profile, job.getSource()));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER)
                        .userProfile(profileStr)
                        .jobDescription(promptDataBuilder.buildJobString(job))
                        .companyInfo(companyAnalysis)
                        .matchedProjects(projectData.text())
                        .imageUrls(projectData.imageUrls())
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
        List<Project> matched = matchProjects(userId, jobPostingId);

        if (matched.isEmpty()) {
            throw new RuntimeException("매칭된 프로젝트가 없습니다.");
        }

        String profileStr = promptDataBuilder.buildProfileString(profile, job.getSource());
        String jobStr = promptDataBuilder.buildJobString(job);
        String siteName = job.getSource().name();
        String companyAnalysis = extractRelevantAnalysis(getOrCreateCompanyAnalysis(userId, jobPostingId, job));

        // 프로젝트별로 각각 포트폴리오 생성 → JSON 배열로 반환
        List<Map<String, Object>> portfolioList = new ArrayList<>();
        for (Project project : matched) {
            ProjectData projectData = buildSingleProjectData(project);

            AiGenerationResult result = aiTextGenerator.generate(
                    AiGenerationRequest.builder()
                            .type(AiGenerationRequest.GenerationType.PORTFOLIO)
                            .userProfile(profileStr)
                            .jobDescription(jobStr)
                            .companyInfo(companyAnalysis)
                            .matchedProjects(projectData.text())
                            .imageUrls(projectData.imageUrls())
                            .sourceSite(siteName)
                            .build());

            if (!result.isSuccess()) {
                log.error("[AI] 포트폴리오 생성 실패 (프로젝트: {}): {}", project.getName(), result.getErrorMessage());
                throw new RuntimeException("포트폴리오 생성 실패 (" + project.getName() + "): " + result.getErrorMessage());
            }

            String content = applyTemplate(templateId, result.getGeneratedText());
            portfolioList.add(Map.of(
                    "projectId", project.getId(),
                    "projectName", project.getName(),
                    "content", content));
        }

        log.info("[AI] 포트폴리오 {}개 프로젝트 개별 생성 완료", matched.size());
        return toJson(portfolioList);
    }

    @Override
    public String generatePortfolio(Long userId, Long jobPostingId, Long templateId, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return generatePortfolio(userId, jobPostingId, templateId);
        }
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);
        List<Project> projects = projectIds.stream()
                .map(pid -> projectRepository.findById(pid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (projects.isEmpty()) {
            throw new RuntimeException("선택된 프로젝트를 찾을 수 없습니다.");
        }

        String profileStr = promptDataBuilder.buildProfileString(profile, job.getSource());
        String jobStr = promptDataBuilder.buildJobString(job);
        String siteName = job.getSource().name();
        String companyAnalysis = extractRelevantAnalysis(getOrCreateCompanyAnalysis(userId, jobPostingId, job));

        List<Map<String, Object>> portfolioList = new ArrayList<>();
        for (Project project : projects) {
            ProjectData projectData = buildSingleProjectData(project);
            AiGenerationResult result = aiTextGenerator.generate(
                    AiGenerationRequest.builder()
                            .type(AiGenerationRequest.GenerationType.PORTFOLIO)
                            .userProfile(profileStr)
                            .jobDescription(jobStr)
                            .companyInfo(companyAnalysis)
                            .matchedProjects(projectData.text())
                            .imageUrls(projectData.imageUrls())
                            .sourceSite(siteName)
                            .build());

            if (!result.isSuccess()) {
                throw new RuntimeException("포트폴리오 생성 실패 (" + project.getName() + "): " + result.getErrorMessage());
            }
            String content = applyTemplate(templateId, result.getGeneratedText());
            portfolioList.add(Map.of(
                    "projectId", project.getId(),
                    "projectName", project.getName(),
                    "content", content));
        }
        log.info("[AI] 포트폴리오 {}개 프로젝트(지정) 개별 생성 완료", projects.size());
        return toJson(portfolioList);
    }

    @Override
    public String generateCoverLetter(Long userId, Long jobPostingId, Long templateId, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return generateCoverLetter(userId, jobPostingId, templateId);
        }
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);

        // 지정된 프로젝트로 데이터 조립
        List<String> allImageUrls = new ArrayList<>();
        String projectsText = projectIds.stream()
                .map(pid -> projectRepository.findById(pid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(p -> {
                    ProjectData pd = buildSingleProjectData(p);
                    allImageUrls.addAll(pd.imageUrls());
                    return pd.text();
                })
                .collect(Collectors.joining("\n---\n"));

        String companyAnalysis = extractRelevantAnalysis(getOrCreateCompanyAnalysis(userId, jobPostingId, job));
        String profileStr = promptDataBuilder.stripEducation(
                promptDataBuilder.buildProfileString(profile, job.getSource()));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER)
                        .userProfile(profileStr)
                        .jobDescription(promptDataBuilder.buildJobString(job))
                        .companyInfo(companyAnalysis)
                        .matchedProjects(projectsText)
                        .imageUrls(allImageUrls)
                        .sourceSite(job.getSource().name())
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("자소서 생성 실패: " + result.getErrorMessage());
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
    @Transactional
    public String analyzeCoverLetterPattern(Long coverLetterId) {
        log.info("[AI] 자소서 패턴 분석 시작: coverLetterId={}", coverLetterId);

        // 이미 분석된 결과가 있으면 캐시 반환 (모든 유저 공유)
        var cached = aiAnalysisResultRepository.findByJobPostingIdAndType(
                coverLetterId, AnalysisType.COVER_LETTER_PATTERN);
        if (cached.isPresent() && cached.get().getResultText() != null) {
            log.info("[AI] 자소서 패턴 분석 캐시 반환: coverLetterId={}", coverLetterId);
            return cached.get().getResultText();
        }

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

        String analysisText = result.isSuccess()
                ? result.getGeneratedText()
                : "자소서 패턴 분석 실패: " + result.getErrorMessage();

        // 공유 저장 (userId=0, jobPostingId=coverLetterId 재활용)
        if (result.isSuccess()) {
            saveOrUpdateSharedAnalysis(coverLetterId, AnalysisType.COVER_LETTER_PATTERN, analysisText);
        }

        return analysisText;
    }

    @Override
    public String generateCustomCoverLetter(Long userId, Long jobPostingId,
            String sectionsJson, String additionalRequest) {
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);
        ProjectData projectData = buildMatchedProjectsData(userId, jobPostingId);

        String companyAnalysis = extractRelevantAnalysis(getOrCreateCompanyAnalysis(userId, jobPostingId, job));
        String profileStr = promptDataBuilder.stripEducation(
                promptDataBuilder.buildProfileString(profile, job.getSource()));

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.CUSTOM_COVER_LETTER)
                        .userProfile(profileStr)
                        .jobDescription(promptDataBuilder.buildJobString(job))
                        .companyInfo(companyAnalysis)
                        .matchedProjects(projectData.text())
                        .imageUrls(projectData.imageUrls())
                        .sourceSite(job.getSource().name())
                        .customSections(sectionsJson)
                        .additionalRequest(additionalRequest)
                        .build());

        if (!result.isSuccess())
            throw new RuntimeException("커스텀 자소서 생성 실패: " + result.getErrorMessage());

        return result.getGeneratedText();
    }

    @Override
    public String generateCustomPortfolio(Long userId, Long jobPostingId,
            String sectionsJson, String additionalRequest) {
        UserProfile profile = findProfile(userId);
        JobPosting job = findJob(jobPostingId);
        List<Project> matched = matchProjects(userId, jobPostingId);

        if (matched.isEmpty()) {
            throw new RuntimeException("매칭된 프로젝트가 없습니다.");
        }

        String profileStr = promptDataBuilder.buildProfileString(profile, job.getSource());
        String jobStr = promptDataBuilder.buildJobString(job);
        String siteName = job.getSource().name();

        // 프로젝트별로 각각 커스텀 포트폴리오 생성 → JSON 배열로 반환
        List<Map<String, Object>> portfolioList = new ArrayList<>();
        for (Project project : matched) {
            ProjectData projectData = buildSingleProjectData(project);

            AiGenerationResult result = aiTextGenerator.generate(
                    AiGenerationRequest.builder()
                            .type(AiGenerationRequest.GenerationType.CUSTOM_PORTFOLIO)
                            .userProfile(profileStr)
                            .jobDescription(jobStr)
                            .matchedProjects(projectData.text())
                            .imageUrls(projectData.imageUrls())
                            .sourceSite(siteName)
                            .customSections(sectionsJson)
                            .additionalRequest(additionalRequest)
                            .build());

            if (!result.isSuccess()) {
                log.error("[AI] 커스텀 포트폴리오 생성 실패 (프로젝트: {}): {}", project.getName(), result.getErrorMessage());
                throw new RuntimeException("커스텀 포트폴리오 생성 실패 (" + project.getName() + "): " + result.getErrorMessage());
            }

            portfolioList.add(Map.of(
                    "projectId", project.getId(),
                    "projectName", project.getName(),
                    "content", result.getGeneratedText()));
        }

        log.info("[AI] 커스텀 포트폴리오 {}개 프로젝트 개별 생성 완료", matched.size());
        return toJson(portfolioList);
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

    /** 프로젝트 텍스트 + 노션 이미지 URL을 함께 반환 */
    private record ProjectData(String text, java.util.List<String> imageUrls) {}

    private ProjectData buildMatchedProjectsData(Long userId, Long jobPostingId) {
        java.util.List<String> allImageUrls = new java.util.ArrayList<>();

        String text = matchProjects(userId, jobPostingId).stream()
                .map(p -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("프로젝트명: ").append(p.getName()).append("\n");
                    if (p.getDescription() != null) sb.append("설명: ").append(p.getDescription()).append("\n");
                    if (p.getTechStack() != null) sb.append("기술스택: ").append(p.getTechStack()).append("\n");
                    if (p.getGithubUrl() != null) sb.append("GitHub: ").append(p.getGithubUrl()).append("\n");
                    if (p.getNotionUrl() != null) {
                        sb.append("Notion: ").append(p.getNotionUrl()).append("\n");
                        var notionResult = notionPageReader.readPage(p.getNotionUrl());
                        if (!notionResult.getText().isBlank()) {
                            sb.append("Notion내용:\n").append(notionResult.getText()).append("\n");
                        }
                        allImageUrls.addAll(notionResult.getImageUrls());
                    }
                    if (p.getAiSummary() != null) sb.append("AI분석: ").append(p.getAiSummary()).append("\n");
                    return sb.toString();
                })
                .collect(Collectors.joining("\n---\n"));

        return new ProjectData(text, allImageUrls);
    }

    /** 단일 프로젝트의 텍스트 + 노션 이미지 URL 반환 */
    private ProjectData buildSingleProjectData(Project p) {
        java.util.List<String> imageUrls = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("프로젝트명: ").append(p.getName()).append("\n");
        if (p.getDescription() != null) sb.append("설명: ").append(p.getDescription()).append("\n");
        if (p.getTechStack() != null) sb.append("기술스택: ").append(p.getTechStack()).append("\n");
        if (p.getGithubUrl() != null) sb.append("GitHub: ").append(p.getGithubUrl()).append("\n");
        if (p.getNotionUrl() != null) {
            sb.append("Notion: ").append(p.getNotionUrl()).append("\n");
            var notionResult = notionPageReader.readPage(p.getNotionUrl());
            if (!notionResult.getText().isBlank()) {
                sb.append("Notion내용:\n").append(notionResult.getText()).append("\n");
            }
            imageUrls.addAll(notionResult.getImageUrls());
        }
        if (p.getAiSummary() != null) sb.append("AI분석: ").append(p.getAiSummary()).append("\n");
        return new ProjectData(sb.toString(), imageUrls);
    }

    /** 하위 호환 — 텍스트만 필요한 곳 */
    private String buildMatchedProjectsDetail(Long userId, Long jobPostingId) {
        return buildMatchedProjectsData(userId, jobPostingId).text();
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

    /**
     * 기업 분석 결과를 조회하고, 없으면 AI로 생성 후 반환한다.
     * 내부 호출로 @Transactional 프록시를 타지 않으므로 직접 저장 로직을 구현한다.
     */
    private String getOrCreateCompanyAnalysis(Long userId, Long jobPostingId, JobPosting job) {
        var cached = aiAnalysisResultRepository.findByJobPostingIdAndType(
                jobPostingId, AnalysisType.COMPANY_ANALYSIS);
        if (cached.isPresent() && cached.get().getResultText() != null) {
            return cached.get().getResultText();
        }
        // 없으면 AI로 생성 (저장은 하지 않고 결과만 반환 — 저장은 별도 트랜잭션 필요)
        try {
            log.info("[AI] 기업 분석 자동 생성 시작: {}", job.getCompany());
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
            if (result.isSuccess()) {
                log.info("[AI] 기업 분석 자동 생성 완료: {}", job.getCompany());
                return result.getGeneratedText();
            }
            return job.getCompany();
        } catch (Exception e) {
            log.warn("[AI] 기업 분석 자동 생성 실패, 회사명만 사용: {}", e.getMessage());
            return job.getCompany();
        }
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }

    /**
     * 기업 분석 전문에서 자소서/포트폴리오에 필요한 섹션만 추출한다.
     * 비전과 핵심 가치, 주요 기술/사업 영역, 포트폴리오/자소서 참고 포인트, 지원자 관점 요약만 포함.
     */
    private String extractRelevantAnalysis(String fullAnalysis) {
        if (fullAnalysis == null || fullAnalysis.isBlank()) return "";

        String[] keepSections = {
                "비전과 핵심 가치",
                "주요 기술/사업 영역",
                "포트폴리오/자소서 작성 시 참고 포인트",
                "지원자 관점 요약"
        };

        StringBuilder result = new StringBuilder();
        String[] lines = fullAnalysis.split("\n");

        boolean include = false;
        for (String line : lines) {
            String trimmed = line.trim();
            // ## 섹션 제목 감지
            if (trimmed.startsWith("##") || trimmed.startsWith("# ")) {
                include = false;
                for (String keep : keepSections) {
                    if (trimmed.contains(keep)) {
                        include = true;
                        break;
                    }
                }
            }
            if (include) {
                result.append(line).append("\n");
            }
        }

        String extracted = result.toString().trim();
        return extracted.isEmpty() ? fullAnalysis : extracted;
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

    @Override
    public String generateProjectPortfolio(Long userId, Long projectId) {
        UserProfile profile = findProfile(userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectData projectData = buildSingleProjectData(project);
        String profileStr = promptDataBuilder.buildProfileString(profile, null);

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.PORTFOLIO)
                        .userProfile(profileStr)
                        .jobDescription("")
                        .companyInfo("")
                        .matchedProjects(projectData.text())
                        .imageUrls(projectData.imageUrls())
                        .build());

        if (!result.isSuccess()) {
            throw new RuntimeException("포트폴리오 생성 실패 (" + project.getName() + "): " + result.getErrorMessage());
        }

        String content = result.getGeneratedText();

        // 새 스레드에서 호출되므로 명시적 트랜잭션으로 저장
        try {
            var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            txTemplate.executeWithoutResult(status -> {
                Project p = projectRepository.findById(projectId).orElseThrow();
                p.updatePortfolioContent(content);
                projectRepository.save(p);
                log.info("[AI] 포트폴리오 DB 저장 완료 - projectId:{}, 길이:{}", projectId, content.length());
            });
        } catch (Exception e) {
            log.error("[AI] 포트폴리오 DB 저장 실패 - projectId:{}, 에러:{}", projectId, e.getMessage(), e);
        }

        log.info("[AI] 프로젝트 범용 포트폴리오 생성 완료: {}", project.getName());
        return content;
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
