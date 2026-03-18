package com.portfolio.jobcrawler.domain.template.repository;

import com.portfolio.jobcrawler.domain.template.entity.Template;
import com.portfolio.jobcrawler.domain.template.vo.TemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    List<Template> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Template> findByUserIdAndType(Long userId, TemplateType type);

    Optional<Template> findByUserIdAndTypeAndIsDefaultTrue(Long userId, TemplateType type);

    List<Template> findByIsSystemTrueOrderByCreatedAtDesc();

    void deleteByIsSystemTrue();
}
