package com.portfolio.jobcrawler.application.template;

import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.repository.TemplateRepository;
import com.portfolio.jobcrawler.domain.template.vo.TemplateType;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;

    @Override
    public List<Template> getMyTemplates(Long userId) {
        return templateRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Template getMyTemplate(Long userId, Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
        if (!template.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.TEMPLATE_ACCESS_DENIED);
        }
        return template;
    }

    @Override
    @Transactional
    public Template createTemplate(Long userId, String name, TemplateType type, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return templateRepository.save(
                Template.builder().user(user).name(name).type(type).content(content).build());
    }

    @Override
    @Transactional
    public Template updateTemplate(Long userId, Long templateId, String name, String content) {
        Template template = getMyTemplate(userId, templateId);
        template.update(name, content);
        return template;
    }

    @Override
    @Transactional
    public void deleteTemplate(Long userId, Long templateId) {
        Template template = getMyTemplate(userId, templateId);
        templateRepository.delete(template);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long templateId) {
        Template template = getMyTemplate(userId, templateId);
        // 기존 default 해제 → 도메인 메서드
        templateRepository.findByUserIdAndTypeAndIsDefaultTrue(userId, template.getType())
                .ifPresent(Template::unmarkDefault);
        // 새 default 세팅 → 도메인 메서드
        template.markAsDefault();
    }
}
