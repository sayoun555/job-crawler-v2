package com.portfolio.jobcrawler.domain.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_certifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeCertification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    private String certName;

    private String issuingOrganization;

    private String acquiredDate;

    private int sortOrder;

    @Builder
    private ResumeCertification(Resume resume, String certName, String issuingOrganization,
                                String acquiredDate, int sortOrder) {
        this.resume = resume;
        this.certName = certName;
        this.issuingOrganization = issuingOrganization;
        this.acquiredDate = acquiredDate;
        this.sortOrder = sortOrder;
    }

    public void update(String certName, String issuingOrganization, String acquiredDate) {
        this.certName = certName;
        this.issuingOrganization = issuingOrganization;
        this.acquiredDate = acquiredDate;
    }
}
