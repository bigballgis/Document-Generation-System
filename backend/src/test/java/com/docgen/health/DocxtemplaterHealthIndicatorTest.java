package com.docgen.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocxtemplaterHealthIndicatorTest {

    @Mock
    private RestTemplate restTemplate;

    private DocxtemplaterHealthIndicator indicator;

    private static final String SERVICE_URL = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        indicator = new DocxtemplaterHealthIndicator(restTemplate, SERVICE_URL);
    }

    @Test
    void health_returnsUp_whenServiceResponds200() {
        when(restTemplate.getForEntity(SERVICE_URL + "/health", String.class))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(SERVICE_URL, health.getDetails().get("url"));
        assertEquals("reachable", health.getDetails().get("status"));
    }

    @Test
    void health_returnsDown_whenServiceResponds500() {
        when(restTemplate.getForEntity(SERVICE_URL + "/health", String.class))
                .thenReturn(new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(500, health.getDetails().get("httpStatus"));
    }

    @Test
    void health_returnsDown_whenConnectionFails() {
        when(restTemplate.getForEntity(SERVICE_URL + "/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Connection refused", health.getDetails().get("error"));
    }
}
