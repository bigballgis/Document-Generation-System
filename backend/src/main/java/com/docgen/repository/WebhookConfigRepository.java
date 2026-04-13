package com.docgen.repository;

import com.docgen.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for webhook configuration records.
 */
@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    List<WebhookConfig> findByTemplateIdOrderByCreatedAtDesc(Long templateId);

    List<WebhookConfig> findByTemplateIdAndEnabledTrue(Long templateId);
}
