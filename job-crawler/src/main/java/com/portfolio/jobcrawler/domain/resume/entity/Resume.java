package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resumes",
        indexes = @Index(name = "idx_resume_user_site", columnList = "user_id, source_site"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** null이면 마스터(고유) 이력서, 값이 있으면 해당 사이트에서 Import한 이력서 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_site", length = 20)
    private SourceSite sourceSite;

    /** 이력서 제목 (사이트에서 여러 개 이력서를 구분) */
    @Column(name = "resume_title", length = 200)
    private String resumeTitle;

    private String name;

    private String phone;

    private String email;

    private String gender;

    private String birthDate;

    private String address;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Column(columnDefinition = "TEXT")
    private String selfIntroduction;

    private String desiredSalary;

    private String desiredEmploymentType;

    private String desiredLocation;

    private String militaryStatus;

    private String disabilityStatus;

    private String veteranStatus;

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeEducation> educations = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeCareer> careers = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeCertification> certifications = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeLanguage> languages = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumeActivity> activities = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResumePortfolioLink> portfolioLinks = new ArrayList<>();

    @Builder
    private Resume(User user, SourceSite sourceSite, String resumeTitle) {
        this.user = user;
        this.sourceSite = sourceSite;
        this.resumeTitle = resumeTitle;
    }

    /** 마스터(고유) 이력서 생성 */
    public static Resume createFor(User user) {
        return Resume.builder().user(user).build();
    }

    /** 사이트별 이력서 생성 (Import용) */
    public static Resume createSiteResume(User user, SourceSite site, String title) {
        return Resume.builder().user(user).sourceSite(site).resumeTitle(title).build();
    }

    public void updateResumeTitle(String title) {
        this.resumeTitle = title;
    }

    /** 마스터 이력서인지 확인 */
    public boolean isMaster() {
        return this.sourceSite == null;
    }

    public void updateBasicInfo(String name, String phone, String email,
                                String gender, String birthDate, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.birthDate = birthDate;
        this.address = address;
    }

    public void updateIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public void updateSelfIntroduction(String selfIntroduction) {
        this.selfIntroduction = selfIntroduction;
    }

    public void updateDesiredConditions(String desiredSalary, String desiredEmploymentType,
                                        String desiredLocation) {
        this.desiredSalary = desiredSalary;
        this.desiredEmploymentType = desiredEmploymentType;
        this.desiredLocation = desiredLocation;
    }

    public void updateSpecialStatus(String militaryStatus, String disabilityStatus,
                                    String veteranStatus) {
        this.militaryStatus = militaryStatus;
        this.disabilityStatus = disabilityStatus;
        this.veteranStatus = veteranStatus;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
