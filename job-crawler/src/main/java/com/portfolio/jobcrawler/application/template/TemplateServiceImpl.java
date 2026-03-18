package com.portfolio.jobcrawler.application.template;

import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.repository.TemplateRepository;
import com.portfolio.jobcrawler.domain.template.vo.TemplateType;
import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import com.portfolio.jobcrawler.global.error.CustomException;
import com.portfolio.jobcrawler.global.error.ErrorCode;
import com.portfolio.jobcrawler.infrastructure.ai.AiTextGenerator;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationRequest;
import com.portfolio.jobcrawler.infrastructure.ai.dto.AiGenerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AiTextGenerator aiTextGenerator;
    private final ObjectMapper objectMapper;

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

    @Override
    public List<Template> getSystemPresets() {
        return templateRepository.findByIsSystemTrueOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public int refreshPresetsWithAi() {
        log.info("[TemplateService] AI 프리셋 갱신 시작");

        AiGenerationResult result = aiTextGenerator.generate(
                AiGenerationRequest.builder()
                        .type(AiGenerationRequest.GenerationType.COVER_LETTER_PRESET_SEARCH)
                        .build());

        if (!result.isSuccess()) {
            log.error("[TemplateService] AI 프리셋 갱신 실패: {}", result.getErrorMessage());
            return 0;
        }

        try {
            String response = result.getGeneratedText().trim();
            // JSON 블록 추출
            if (response.contains("```json")) {
                response = response.substring(response.indexOf("```json") + 7);
                response = response.substring(0, response.indexOf("```"));
            } else if (response.contains("```")) {
                response = response.substring(response.indexOf("```") + 3);
                response = response.substring(0, response.indexOf("```"));
            }
            response = response.trim();

            List<Map<String, Object>> presets = objectMapper.readValue(response, List.class);

            // 기존 시스템 프리셋 삭제
            templateRepository.deleteByIsSystemTrue();

            int count = 0;
            for (Map<String, Object> preset : presets) {
                String name = (String) preset.get("name");
                String company = (String) preset.get("company");
                List<Map<String, Object>> questions = (List<Map<String, Object>>) preset.get("questions");

                StringBuilder content = new StringBuilder();
                if (questions != null) {
                    for (Map<String, Object> q : questions) {
                        int num = q.get("number") instanceof Integer ? (int) q.get("number") : 1;
                        String title = (String) q.get("title");
                        Object maxLenObj = q.get("maxLength");
                        int maxLen = maxLenObj instanceof Integer ? (int) maxLenObj : 0;
                        String guide = (String) q.getOrDefault("guide", "");

                        content.append("[").append(num).append(". ").append(title).append("]");
                        if (maxLen > 0) content.append(" (").append(maxLen).append("자 이내)");
                        content.append("\n");
                        if (!guide.isEmpty()) content.append(guide).append("\n");
                        content.append("{{").append(title).append("}}\n\n");
                    }
                }

                Template template = Template.createSystemTemplate(
                        name, TemplateType.COVER_LETTER, content.toString().trim());
                templateRepository.save(template);
                count++;
                log.info("[TemplateService] 프리셋 저장: {} ({})", name, company);
            }

            log.info("[TemplateService] AI 프리셋 갱신 완료 - {}개", count);
            return count;
        } catch (Exception e) {
            log.error("[TemplateService] 프리셋 파싱 실패: {}", e.getMessage());
            return 0;
        }
    }
}
