package maoomWeb.ire.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import maoomWeb.ire.user.dto.ProjectExecutionLogDto;

class AdminProjectExecutionLogServiceTest {

    @Test
    void findsAllLogsWhenLimitIsNotProvided() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of());
        AdminProjectExecutionLogService service =
                new AdminProjectExecutionLogService(jdbcTemplate);

        List<ProjectExecutionLogDto> logs = service.findRecent(null);

        assertThat(logs).isEmpty();

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).doesNotContain("LIMIT");
    }

    @Test
    void stillAppliesBoundedLimitWhenLimitIsProvided() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(500)))
                .thenReturn(List.of());
        AdminProjectExecutionLogService service =
                new AdminProjectExecutionLogService(jdbcTemplate);

        List<ProjectExecutionLogDto> logs = service.findRecent(1000);

        assertThat(logs).isEmpty();
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(500));
    }
}
