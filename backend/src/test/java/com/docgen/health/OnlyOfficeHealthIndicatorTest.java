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
class OnlyOfficeHealthIndicatorTest {

    @Mock
    private RestTemplate restTemplate;

    private OnlyOfficeHealthIndicator indicator;

    private static final String ONLYOFFICE_URL = "http://localhost";

    @BeforeEach
    void setUp() {
        indicator = new OnlyOfficeHealthIndicator(restTemplate, ONLYOFFICE_URL);
    }

    @Test
    void health_returnsUp_whenServiceResponds200() {
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(ONLYOFFICE_URL, health.getDetails().get("url"));
        assertEquals("reachable", health.getDetails().get("status"));
    }

    @Test
    void health_returnsDown_whenServiceResponds503() {
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("unavailable", HttpStatus.SERVICE_UNAVAILABLE));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(503, health.getDetails().get("httpStatus"));
    }

    @Test
    void health_returnsDown_whenConnectionFails() {
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Connection refused", health.getDetails().get("error"));
    }
}
