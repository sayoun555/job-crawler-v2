package com.portfolio.jobcrawler.application.template;

import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.vo.TemplateType;

import java.util.List;

/**
 * 템플릿 Application Service 인터페이스.
 */
public interface TemplateService {
    List<Template> getMyTemplates(Long userId);

    Template getMyTemplate(Long userId, Long templateId);

    Template createTemplate(Long userId, String name, TemplateType type, String content);

    Template updateTemplate(Long userId, Long templateId, String name, String content);

    void deleteTemplate(Long userId, Long templateId);

    void setDefault(Long userId, Long templateId);

    List<Template> getSystemPresets();

    int refreshPresetsWithAi();
}
