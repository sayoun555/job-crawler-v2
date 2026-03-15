package com.portfolio.jobcrawler.api.template.dto;

import com.portfolio.jobcrawler.domain.template.vo.TemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TemplateRequest {
    @NotBlank(message = "템플릿 이름은 필수입니다.")
    private String name;
    @NotNull(message = "템플릿 타입은 필수입니다.")
    private TemplateType type;
    @NotBlank(message = "템플릿 내용은 필수입니다.")
    private String content;
}
