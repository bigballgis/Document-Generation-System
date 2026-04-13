package com.docgen.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Custom health indicator for the Docxtemplater Node.js service.
 * Calls the /health endpoint to determine service availability.
 */
@Component
public class DocxtemplaterHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DocxtemplaterHealthIndicator.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public DocxtemplaterHealthIndicator(RestTemplate restTemplate,
                                         @Value("${docxtemplater.service-url}") String serviceUrl) {
        this.restTemplate = restTemplate;
        this.serviceUrl = serviceUrl;
    }

    @Override
    public Health health() {
        String healthUrl = serviceUrl + "/health";
        try {
            var response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                        .withDetail("url", serviceUrl)
                        .withDetail("status", "reachable")
                        .build();
            }
            return Health.down()
                    .withDetail("url", serviceUrl)
                    .withDetail("httpStatus", response.getStatusCode().value())
                    .build();
        } catch (Exception e) {
            log.warn("Docxtemplater service health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", serviceUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
