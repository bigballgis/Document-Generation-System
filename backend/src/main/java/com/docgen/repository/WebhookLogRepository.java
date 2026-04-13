package com.docgen.repository;

import com.docgen.entity.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for webhook log records.
 */
@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    Page<WebhookLog> findByWebhookConfigIdOrderBySentAtDesc(Long webhookConfigId, Pageable pageable);
}
