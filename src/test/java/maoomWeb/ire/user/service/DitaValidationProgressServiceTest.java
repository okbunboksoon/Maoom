package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

class DitaValidationProgressServiceTest {
    @Test
    void cancellationInterruptsTheRunningRequestAndKeepsCancelledStatus() {
        DitaValidationProgressService service = new DitaValidationProgressService();
        String token = "cancel-test-token";

        service.start(token);
        assertEquals("CANCELLING", service.cancel(token).status());
        assertTrue(service.isCancellationRequested(token));

        service.cancelled(token, "사용자가 Schematron 검사를 취소했습니다.");
        service.finish(token);

        assertEquals("CANCELLED", service.get(token).status());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void removesOnlyExpiredTerminalProgress() {
        DitaValidationProgressService service = new DitaValidationProgressService(60);
        service.start("completed-token");
        service.complete("completed-token", 3);
        service.finish("completed-token");
        service.start("running-token");

        service.cleanupExpired(LocalDateTime.now().plusMinutes(61));

        assertEquals("WAITING", service.get("completed-token").status());
        assertEquals("RUNNING", service.get("running-token").status());
        service.finish("running-token");
    }
}
