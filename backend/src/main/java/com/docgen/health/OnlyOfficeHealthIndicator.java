package com.docgen.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Custom health indicator for the OnlyOffice Document Server.
 * Calls the /healthcheck endpoint to determine service availability.
 */
@Component
public class OnlyOfficeHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeHealthIndicator.class);

    private final RestTemplate restTemplate;
    private final String onlyOfficeUrl;

    public OnlyOfficeHealthIndicator(RestTemplate restTemplate,
                                      @Value("${onlyoffice.url}") String onlyOfficeUrl) {
        this.restTemplate = restTemplate;
        this.onlyOfficeUrl = onlyOfficeUrl;
    }

    @Override
    public Health health() {
        String healthUrl = onlyOfficeUrl + "/healthcheck";
        try {
            var response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                        .withDetail("url", onlyOfficeUrl)
                        .withDetail("status", "reachable")
                        .build();
            }
            return Health.down()
                    .withDetail("url", onlyOfficeUrl)
                    .withDetail("httpStatus", response.getStatusCode().value())
                    .build();
        } catch (Exception e) {
            log.warn("OnlyOffice Document Server health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", onlyOfficeUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
