package com.portfolio.jobcrawler.api.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProfileUpdateRequest {
    private String education;
    private String career;
    private String certifications;
    private String techStack;
    private String strengths;
}
