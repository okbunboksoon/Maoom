package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import maoomWeb.ire.user.dto.DitaValidationDtos.ReportStateItem;

class SchematronHistoryServiceTest {
    private static final String ISSUE_ID = "0123456789abcdef0123456789abcdef";

    @Test
    void marksPreviouslyRunningHistoriesAsInterruptedOnStartup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = new SchematronHistoryService(jdbc);

        int updated = service.interruptRunningHistories();

        assertEquals(0, updated);
        verify(jdbc).update(argThat((String sql) -> sql.contains("SET status='INTERRUPTED'")
                && sql.contains("WHERE status='RUNNING'")
                && sql.contains("서버 재시작으로 검사가 중단되었습니다.")));
    }

    @Test
    void savesTheLoggedInConfirmerAndReturnsTheServerTimestamp() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        doReturn(reportWith(ISSUE_ID)).when(service).reportPath(7L);
        LocalDateTime savedAt = LocalDateTime.of(2026, 10, 14, 9, 30);
        ReportStateItem stored = new ReportStateItem(ISSUE_ID, true, savedAt, "A사용자", null);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID)))
                .thenReturn(stored);

        ReportStateItem result = service.saveReportState(7L, ISSUE_ID, true, "user-a", "A사용자");

        assertEquals(stored, result);
        verify(jdbc).update(contains("confirmer_name=VALUES(confirmer_name)"),
                eq(7L), eq(ISSUE_ID), eq(true), eq(true), eq(true), eq("user-a"), eq(true), eq("A사용자"));
    }

    @Test
    void uncheckingClearsTimestampAndConfirmerValues() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        doReturn(reportWith(ISSUE_ID)).when(service).reportPath(7L);
        ReportStateItem cleared = new ReportStateItem(ISSUE_ID, false, null, null, null);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID)))
                .thenReturn(cleared);

        ReportStateItem result = service.saveReportState(7L, ISSUE_ID, false, "user-b", "B사용자");

        assertFalse(result.checked());
        assertNull(result.updatedAt());
        assertNull(result.confirmerName());
        verify(jdbc).update(anyString(),
                eq(7L), eq(ISSUE_ID), eq(false), eq(false), eq(false), eq("user-b"), eq(false), eq("B사용자"));
    }

    @Test
    void rejectsTheWholeBatchBeforeWritingWhenAnyIssueIdIsInvalid() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = new SchematronHistoryService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.saveReportStates(7L,
                List.of(ISSUE_ID, "invalid"), true, "user-a", "A사용자"));

        verify(jdbc, never()).batchUpdate(anyString(), anyCollection(), anyInt(), any());
    }

    @Test
    void rejectsWellFormedIssueIdThatDoesNotBelongToTheReport() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        doReturn(reportWith(ISSUE_ID)).when(service).reportPath(7L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.saveReportState(7L,
                "abcdef0123456789abcdef0123456789", true, "user-a", "A사용자"));
        verify(jdbc, never()).update(contains("tb_schematron_report_issue_state"), any(Object[].class));
    }

    @Test
    void savesSharedMemoWithoutChangingCheckedState() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        doReturn(reportWith(ISSUE_ID)).when(service).reportPath(7L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        ReportStateItem stored = new ReportStateItem(ISSUE_ID, true, LocalDateTime.now(), "A사용자", "번역팀 확인 필요");
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID))).thenReturn(stored);

        ReportStateItem result = service.saveReportMemo(7L, ISSUE_ID, "  번역팀 확인 필요  ");

        assertEquals("번역팀 확인 필요", result.memo());
        verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE memo=VALUES(memo)"),
                eq(7L), eq(ISSUE_ID), eq("번역팀 확인 필요"));
    }

    private Path reportWith(String issueId) throws Exception {
        Path report = Files.createTempFile("schematron-report-", ".html");
        Files.writeString(report, "<tr data-issue-id=\"" + issueId + "\"></tr>");
        report.toFile().deleteOnExit();
        return report;
    }
}
