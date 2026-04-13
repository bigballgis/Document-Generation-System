package com.docgen.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalServiceMonitorTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AlertNotificationService alertNotificationService;

    private ExternalServiceMonitor monitor;

    private static final String DOCX_URL = "http://localhost:3000";
    private static final String ONLYOFFICE_URL = "http://localhost";

    @BeforeEach
    void setUp() {
        monitor = new ExternalServiceMonitor(restTemplate, alertNotificationService, DOCX_URL, ONLYOFFICE_URL);
    }

    @Test
    void checkExternalServices_sendsAlert_whenServiceGoesDown() {
        // Service was UP initially, now it fails
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));

        monitor.checkExternalServices();

        verify(alertNotificationService).sendServiceDownAlert(
                eq(ExternalServiceMonitor.DOCXTEMPLATER), anyString());
        assertFalse(monitor.isServiceUp(ExternalServiceMonitor.DOCXTEMPLATER));
        assertTrue(monitor.isServiceUp(ExternalServiceMonitor.ONLYOFFICE));
    }

    @Test
    void checkExternalServices_sendsRecovery_whenServiceComesBackUp() {
        // First make it go down
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));
        monitor.checkExternalServices();

        // Now it comes back up
        reset(restTemplate);
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));
        monitor.checkExternalServices();

        verify(alertNotificationService).sendServiceRecoveryAlert(ExternalServiceMonitor.DOCXTEMPLATER);
        assertTrue(monitor.isServiceUp(ExternalServiceMonitor.DOCXTEMPLATER));
    }

    @Test
    void checkExternalServices_noAlert_whenServiceStaysUp() {
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));

        monitor.checkExternalServices();

        verify(alertNotificationService, never()).sendServiceDownAlert(anyString(), anyString());
        verify(alertNotificationService, never()).sendServiceRecoveryAlert(anyString());
    }

    @Test
    void checkExternalServices_noAlert_whenServiceStaysDown() {
        // First check: goes down
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));
        monitor.checkExternalServices();

        reset(alertNotificationService);
        reset(restTemplate);

        // Second check: still down — no new alert
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));
        monitor.checkExternalServices();

        verify(alertNotificationService, never()).sendServiceDownAlert(anyString(), anyString());
    }

    @Test
    void getAllServiceStatuses_returnsSnapshot() {
        var statuses = monitor.getAllServiceStatuses();
        assertEquals(2, statuses.size());
        assertTrue(statuses.containsKey(ExternalServiceMonitor.DOCXTEMPLATER));
        assertTrue(statuses.containsKey(ExternalServiceMonitor.ONLYOFFICE));
    }

    @Test
    void checkService_handlesNon2xxResponse() {
        when(restTemplate.getForEntity(DOCX_URL + "/health", String.class))
                .thenReturn(new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR));
        when(restTemplate.getForEntity(ONLYOFFICE_URL + "/healthcheck", String.class))
                .thenReturn(new ResponseEntity<>("true", HttpStatus.OK));

        monitor.checkExternalServices();

        assertFalse(monitor.isServiceUp(ExternalServiceMonitor.DOCXTEMPLATER));
        verify(alertNotificationService).sendServiceDownAlert(
                eq(ExternalServiceMonitor.DOCXTEMPLATER), eq("HTTP 500"));
    }
}
