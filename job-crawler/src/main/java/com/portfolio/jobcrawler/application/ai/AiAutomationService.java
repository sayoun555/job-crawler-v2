package com.portfolio.jobcrawler.application.ai;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.project.entity.Project;

import java.util.List;
import java.util.Map;

/**
 * AI 자동화 Application Service 인터페이스.
 * 자소서/포트폴리오 생성, 적합률 분석, 프로젝트 매칭.
 */
public interface AiAutomationService {

    /** 공고에 대한 적합률 산출 */
    int analyzeMatchScore(Long userId, Long jobPostingId);

    /** 공고에 매칭되는 프로젝트 자동 선별 */
    List<Project> matchProjects(Long userId, Long jobPostingId);

    /** 자소서 자동 생성 */
    String generateCoverLetter(Long userId, Long jobPostingId, Long templateId);

    /** 포트폴리오 자동 생성 */
    String generatePortfolio(Long userId, Long jobPostingId, Long templateId);

    /** 기업/직무 사전 분석 (유저별 저장) */
    String analyzeCompany(Long userId, Long jobPostingId);

    /** 프로젝트 AI 자동 정리 (GitHub 기반) */
    String summarizeProject(Long projectId);

    /** GitHub URL로 프로젝트 AI 분석 (Step 4.4) */
    String analyzeGitHubProject(String githubUrl);

    /** 합격 자소서 패턴 분석 */
    String analyzeCoverLetterPattern(Long coverLetterId);
}
