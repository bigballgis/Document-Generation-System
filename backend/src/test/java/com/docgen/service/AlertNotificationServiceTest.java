package com.docgen.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AlertNotificationServiceTest {

    private final AlertNotificationService service = new AlertNotificationService();

    @Test
    void sendServiceDownAlert_doesNotThrow() {
        assertDoesNotThrow(() -> service.sendServiceDownAlert("TestService", "Connection refused"));
    }

    @Test
    void sendServiceRecoveryAlert_doesNotThrow() {
        assertDoesNotThrow(() -> service.sendServiceRecoveryAlert("TestService"));
    }
}
