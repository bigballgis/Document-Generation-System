package com.docgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically monitors external services (Docxtemplater, OnlyOffice) and sends
 * alert notifications when a service transitions to DOWN or recovers to UP.
 */
@Service
public class ExternalServiceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ExternalServiceMonitor.class);

    static final String DOCXTEMPLATER = "DocxtemplaterService";
    static final String ONLYOFFICE = "OnlyOfficeDocumentServer";

    private final RestTemplate restTemplate;
    private final AlertNotificationService alertNotificationService;
    private final String docxtemplaterUrl;
    private final String onlyOfficeUrl;

    /** Tracks the last known status of each service: true = UP, false = DOWN. */
    private final Map<String, Boolean> serviceStatus = new ConcurrentHashMap<>();

    public ExternalServiceMonitor(RestTemplate restTemplate,
                                   AlertNotificationService alertNotificationService,
                                   @Value("${docxtemplater.service-url}") String docxtemplaterUrl,
                                   @Value("${onlyoffice.url}") String onlyOfficeUrl) {
        this.restTemplate = restTemplate;
        this.alertNotificationService = alertNotificationService;
        this.docxtemplaterUrl = docxtemplaterUrl;
        this.onlyOfficeUrl = onlyOfficeUrl;
        // Assume services are UP initially
        serviceStatus.put(DOCXTEMPLATER, true);
        serviceStatus.put(ONLYOFFICE, true);
    }

    /**
     * Scheduled health check running every 30 seconds.
     */
    @Scheduled(fixedDelayString = "${monitoring.check-interval-ms:30000}")
    public void checkExternalServices() {
        checkService(DOCXTEMPLATER, docxtemplaterUrl + "/health");
        checkService(ONLYOFFICE, onlyOfficeUrl + "/healthcheck");
    }

    void checkService(String serviceName, String healthUrl) {
        boolean isUp;
        String errorDetail = null;
        try {
            var response = restTemplate.getForEntity(healthUrl, String.class);
            isUp = response.getStatusCode().is2xxSuccessful();
            if (!isUp) {
                errorDetail = "HTTP " + response.getStatusCode().value();
            }
        } catch (Exception e) {
            isUp = false;
            errorDetail = e.getMessage();
        }

        Boolean previousStatus = serviceStatus.put(serviceName, isUp);
        if (previousStatus == null) {
            previousStatus = true;
        }

        // Transition from UP to DOWN → send alert
        if (previousStatus && !isUp) {
            log.warn("Service '{}' transitioned from UP to DOWN", serviceName);
            alertNotificationService.sendServiceDownAlert(serviceName, errorDetail);
        }
        // Transition from DOWN to UP → send recovery
        if (!previousStatus && isUp) {
            log.info("Service '{}' transitioned from DOWN to UP", serviceName);
            alertNotificationService.sendServiceRecoveryAlert(serviceName);
        }
    }

    /**
     * Returns the current known status of a service.
     */
    public boolean isServiceUp(String serviceName) {
        return serviceStatus.getOrDefault(serviceName, false);
    }

    /**
     * Returns a snapshot of all service statuses.
     */
    public Map<String, Boolean> getAllServiceStatuses() {
        return Map.copyOf(serviceStatus);
    }
}
