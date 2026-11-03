package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import maoomWeb.ire.user.dto.DitaValidationDtos.ReportStateItem;
import maoomWeb.ire.user.dto.DitaValidationDtos.GomsReportSummary;

class SchematronHistoryServiceTest {
    private static final String ISSUE_ID = "0123456789abcdef0123456789abcdef";

    @Test
    void aggregatesGomsReportListSummaryAndReviewCount() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = new SchematronHistoryService(jdbc);
        ResultSet rs = mock(ResultSet.class);
        Path report = Path.of("V:/ValidationCheck/project/report/261102_1545_report.html");
        when(rs.getString("report_file_path")).thenReturn(report.toString());
        when(rs.getLong("id")).thenReturn(44L);
        when(rs.getInt("validated_file_count")).thenReturn(740);
        when(rs.getInt("issue_count")).thenReturn(1459);
        when(rs.getTimestamp("finished_at")).thenReturn(Timestamp.valueOf("2026-11-02 15:45:00"));
        when(rs.getString("tester_name")).thenReturn("QA 담당자");
        when(rs.getString("scope_type")).thenReturn("SELECTED_ONLY");
        when(rs.getInt("applied_count")).thenReturn(18);
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("SUM(CASE WHEN s.checked=TRUE"),
                any(org.springframework.jdbc.core.RowCallbackHandler.class));

        Map<String, GomsReportSummary> summaries = service.findSuccessfulReportSummaries();
        GomsReportSummary summary = summaries.get(SchematronHistoryService.normalizedPathKey(report));

        assertAll(
                () -> assertNotNull(summary),
                () -> assertEquals(44L, summary.historyId()),
                () -> assertEquals(740, summary.validatedFileCount()),
                () -> assertEquals(1459, summary.issueCount()),
                () -> assertEquals("261102_1545", summary.finishedAt()),
                () -> assertEquals("QA 담당자", summary.testerName()),
                () -> assertEquals(18, summary.appliedCount()),
                () -> assertEquals("SELECTED_ONLY", summary.scopeType()));
    }

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
        Path report = reportWith(ISSUE_ID);
        doReturn(report).when(service).reportPath(7L);
        LocalDateTime savedAt = LocalDateTime.of(2026, 10, 14, 9, 30);
        ReportStateItem stored = new ReportStateItem(ISSUE_ID, true, savedAt, "A사용자", null, false);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID)))
                .thenReturn(stored);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of(stored));

        ReportStateItem result = service.saveReportState(7L, ISSUE_ID, true, "user-a", "A사용자");

        assertEquals(stored, result);
        String savedHtml = Files.readString(report);
        assertTrue(savedHtml.contains("\"checked\":true"));
        assertTrue(savedHtml.contains("\"confirmerName\":\"A사용자\""));
        assertTrue(savedHtml.contains("\"updatedAt\":\"2026-10-14T09:30:00\""));
        assertFalse(savedHtml.contains("\"updatedAt\":[2026,10,14"));
        verify(jdbc).update(contains("confirmer_name=VALUES(confirmer_name)"),
                eq(7L), eq(ISSUE_ID), eq(true), eq(true), eq(true), eq("user-a"), eq(true), eq("A사용자"));
    }

    @Test
    void uncheckingClearsTimestampAndConfirmerValues() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        doReturn(reportWith(ISSUE_ID)).when(service).reportPath(7L);
        ReportStateItem cleared = new ReportStateItem(ISSUE_ID, false, null, null, null, false);

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
        ReportStateItem stored = new ReportStateItem(ISSUE_ID, true, LocalDateTime.now(), "A사용자", "번역팀 확인 필요", false);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID))).thenReturn(stored);

        ReportStateItem result = service.saveReportMemo(7L, ISSUE_ID, "  번역팀 확인 필요  ");

        assertEquals("번역팀 확인 필요", result.memo());
        verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE memo=VALUES(memo)"),
                eq(7L), eq(ISSUE_ID), eq("번역팀 확인 필요"));
    }

    @Test
    void savesFalsePositiveIndependentlyFromReviewState() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchematronHistoryService service = spy(new SchematronHistoryService(jdbc));
        Path report = reportWith(ISSUE_ID);
        doReturn(report).when(service).reportPath(7L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        LocalDateTime savedAt = LocalDateTime.of(2026, 10, 30, 15, 30);
        ReportStateItem stored = new ReportStateItem(ISSUE_ID, false, savedAt, "A사용자", null, true);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(7L), eq(ISSUE_ID))).thenReturn(stored);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of(stored));

        ReportStateItem result = service.saveFalsePositive(7L, ISSUE_ID, true, "user-a", "A사용자");

        assertTrue(result.falsePositive());
        assertFalse(result.checked());
        assertEquals("A사용자", result.confirmerName());
        assertEquals(savedAt, result.updatedAt());
        assertTrue(Files.readString(report).contains("\"falsePositive\":true"));
        verify(jdbc).update(contains("false_positive=VALUES(false_positive)"),
                eq(7L), eq(ISSUE_ID), eq(true), eq(true), eq(true), eq("user-a"), eq(true), eq("A사용자"));
    }

    private Path reportWith(String issueId) throws Exception {
        Path report = Files.createTempFile("schematron-report-", ".html");
        Files.writeString(report, "<script id=\"embeddedState\" type=\"application/json\">{}</script>"
                + "<tr data-issue-id=\"" + issueId + "\"></tr>");
        report.toFile().deleteOnExit();
        return report;
    }
}
