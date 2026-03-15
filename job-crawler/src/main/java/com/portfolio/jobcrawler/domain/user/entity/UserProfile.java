package com.portfolio.jobcrawler.domain.user.entity;

import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String career;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    @Column(columnDefinition = "TEXT")
    private String techStack;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String preferredJobsSaramin;

    @Column(columnDefinition = "TEXT")
    private String preferredJobsJobplanet;

    @Builder
    public UserProfile(User user) {
        this.user = user;
    }

    // === 도메인 비즈니스 로직 ===

    public void updateBasicInfo(String education, String career, String certifications,
            String techStack, String strengths) {
        this.education = education;
        this.career = career;
        this.certifications = certifications;
        this.techStack = techStack;
        this.strengths = strengths;
    }

    public void updatePreferredJobs(String saraminJobs, String jobplanetJobs) {
        this.preferredJobsSaramin = saraminJobs;
        this.preferredJobsJobplanet = jobplanetJobs;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
