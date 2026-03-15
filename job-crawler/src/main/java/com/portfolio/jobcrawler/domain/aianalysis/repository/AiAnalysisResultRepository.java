package com.portfolio.jobcrawler.domain.aianalysis.repository;

import com.portfolio.jobcrawler.domain.aianalysis.entity.AiAnalysisResult;
import com.portfolio.jobcrawler.domain.aianalysis.vo.AnalysisType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {

    Optional<AiAnalysisResult> findByUserIdAndJobPostingIdAndType(Long userId, Long jobPostingId, AnalysisType type);

    List<AiAnalysisResult> findByUserIdAndType(Long userId, AnalysisType type);

    List<AiAnalysisResult> findByUserIdOrderByCreatedAtDesc(Long userId);
}
