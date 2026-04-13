package com.docgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending alert notifications when critical services go DOWN.
 * Logs alerts and can be extended to send emails, Slack messages, etc.
 */
@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    /**
     * Send an alert notification for a service that has gone DOWN.
     *
     * @param serviceName the name of the service that is down
     * @param details     additional details about the failure
     */
    public void sendServiceDownAlert(String serviceName, String details) {
        log.error("ALERT: Service '{}' is DOWN. Details: {}", serviceName, details);
    }

    /**
     * Send a recovery notification for a service that has come back UP.
     *
     * @param serviceName the name of the service that recovered
     */
    public void sendServiceRecoveryAlert(String serviceName) {
        log.info("RECOVERY: Service '{}' is back UP.", serviceName);
    }
}
