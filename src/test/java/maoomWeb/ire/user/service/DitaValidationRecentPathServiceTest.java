package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DitaValidationRecentPathServiceTest {
    @Test
    void savesAndReturnsRecentPathsByUserAndMode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DitaValidationRecentPathService service = new DitaValidationRecentPathService(jdbc);
        when(jdbc.queryForList(contains("ORDER BY last_used_at DESC"), eq(String.class),
                eq("hkim"), eq("GOMS"), eq(10)))
                .thenReturn(List.of("2026/CT1_EV_27MY/Korean", "2026/JA_ICE_PE2_27MY/Korean"));

        service.save("hkim", "goms", " 2026/CT1_EV_27MY/Korean ");
        List<String> result = service.findRecent("hkim", "GOMS", 50);

        assertEquals(2, result.size());
        verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE"), eq("hkim"), eq("GOMS"),
                org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"), eq("2026/CT1_EV_27MY/Korean"));
    }

    @Test
    void rejectsUnsupportedExecutionMode() {
        DitaValidationRecentPathService service = new DitaValidationRecentPathService(mock(JdbcTemplate.class));
        assertThrows(IllegalArgumentException.class, () -> service.save("hkim", "UNKNOWN", "path"));
    }
}
