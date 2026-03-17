package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

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
    private Resume(User user) {
        this.user = user;
    }

    public static Resume createFor(User user) {
        return Resume.builder()
                .user(user)
                .build();
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
