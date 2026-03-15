package com.portfolio.jobcrawler.domain.template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.jobcrawler.domain.common.entity.BaseTimeEntity;
import com.portfolio.jobcrawler.domain.template.vo.TemplateType;
import com.portfolio.jobcrawler.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Template extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private boolean isDefault = false;

    @Builder
    public Template(User user, String name, TemplateType type, String content) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.content = content;
    }

    // === 도메인 비즈니스 로직 ===

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void update(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public void markAsDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }

    /**
     * 템플릿 플레이스홀더에 AI 생성 텍스트 주입
     */
    public String applyVariables(java.util.Map<String, String> variables) {
        String result = this.content;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
