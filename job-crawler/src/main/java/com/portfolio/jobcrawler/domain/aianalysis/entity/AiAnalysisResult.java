package com.portfolio.jobcrawler.domain.aianalysis.entity;

import com.portfolio.jobcrawler.domain.aianalysis.vo.AnalysisType;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_analysis_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "jobPostingId", "type"}),
        indexes = {
                @Index(name = "idx_ai_user_job", columnList = "userId, jobPostingId"),
                @Index(name = "idx_ai_user_type", columnList = "userId, type")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAnalysisResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long jobPostingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisType type;

    @Column(columnDefinition = "TEXT")
    private String resultText;

    private Integer score;

    @Builder
    public AiAnalysisResult(Long userId, Long jobPostingId, AnalysisType type, String resultText, Integer score) {
        this.userId = userId;
        this.jobPostingId = jobPostingId;
        this.type = type;
        this.resultText = resultText;
        this.score = score;
    }

    public void updateResult(String resultText, Integer score) {
        this.resultText = resultText;
        this.score = score;
    }
}
