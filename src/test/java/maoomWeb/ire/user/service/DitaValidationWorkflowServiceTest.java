package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.sts.dita.core.ValidationCancellation;
import com.sts.dita.core.ValidationIssue;
import com.sts.dita.core.ValidationProgressListener;
import com.sts.dita.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;

class DitaValidationWorkflowServiceTest {
    @TempDir Path tempDir;

    @Test
    void excludesDitamapsInsideOldFoldersIgnoringCase() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("map-search"));
        Files.writeString(project.resolve("current.ditamap"), "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("OLD")).resolve("previous.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("archive").resolve("Old"))
                .resolve("nested.ditamap"), "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("old-data")).resolve("kept.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("report")).resolve("result.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("nested").resolve("Report"))
                .resolve("saved.ditamap"), "<map/>", StandardCharsets.UTF_8);
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                null, null, null, null, new DitaValidationProgressService(),
                tempDir.resolve("reports").toString());

        List<MapItem> maps = service.findMaps(project.toString());

        assertEquals(List.of("current.ditamap", "kept.ditamap"),
                maps.stream().map(MapItem::name).sorted().toList());
    }

    @Test
    void limitsGomsDitamapLookupToTheProjectAndItsDirectChildren() throws Exception {
        Path outputRoot = Files.createDirectories(tempDir.resolve("goms-map-root"));
        Path project = Files.createDirectories(outputRoot.resolve("PU_ICE_27MY_Korean"));
        Path dita = Files.createDirectories(project.resolve("dita"));
        Files.writeString(dita.resolve("main.ditamap"), "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(dita.resolve("nested")).resolve("ignored.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(project.resolve("report")).resolve("ignored.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        Files.writeString(Files.createDirectories(dita.resolve("OLD")).resolve("ignored.ditamap"),
                "<map/>", StandardCharsets.UTF_8);
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                null, null, null, null, new DitaValidationProgressService(),
                tempDir.resolve("goms-map-reports").toString(), outputRoot.toString());

        List<MapItem> maps = service.findMaps(project.toString());

        assertEquals(List.of("main.ditamap"), maps.stream().map(MapItem::name).toList());
    }

    @Test
    void listsGomsReportsAndConnectsSuccessfulHistory() throws Exception {
        Path outputRoot = Files.createDirectories(tempDir.resolve("report-browser"));
        Path project = Files.createDirectories(outputRoot.resolve("SP3_ICE_HEV_27MY_Korean_V2_27MY_Update"));
        Path reportFolder = Files.createDirectories(project.resolve("report"));
        Path editable = Files.writeString(reportFolder.resolve("261030_1146_root_report.html"),
                "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(reportFolder.resolve("261030_1139_old_report.html"),
                "<html></html>", StandardCharsets.UTF_8);
        Files.createDirectories(outputRoot.resolve("EMPTY_PROJECT"));
        Files.createDirectories(outputRoot.resolve("old").resolve("report"));
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        when(history.findSuccessfulReportSummaries()).thenReturn(
                Map.of(SchematronHistoryService.normalizedPathKey(editable),
                        new GomsReportSummary(44L, 740, 1459, "261102_1545", "테스터", 18,
                                "SELECTED_WITH_DESCENDANTS")));
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                null, history, null, null, new DitaValidationProgressService(),
                tempDir.resolve("central-reports").toString(), outputRoot.toString());

        List<GomsReportProject> projects = service.findGomsReports();

        assertEquals(List.of("EMPTY_PROJECT", "SP3_ICE_HEV_27MY_Korean_V2_27MY_Update"),
                projects.stream().map(GomsReportProject::projectName).toList());
        List<GomsReportFile> reports = projects.get(1).reports();
        assertEquals(2, reports.size());
        assertEquals("261030_1146_root_report.html", reports.get(0).fileName());
        assertTrue(reports.get(0).editable());
        assertEquals("/api/dita-validation/reports/44", reports.get(0).url());
        assertEquals(740, reports.get(0).validatedFileCount());
        assertEquals(1459, reports.get(0).issueCount());
        assertEquals("261102_1545", reports.get(0).finishedAt());
        assertEquals("테스터", reports.get(0).testerName());
        assertEquals(18, reports.get(0).appliedCount());
        assertEquals("SELECTED_WITH_DESCENDANTS", reports.get(0).scopeType());
        assertEquals("261030_1139_old_report.html", reports.get(1).fileName());
        assertFalse(reports.get(1).editable());
        assertEquals(editable, service.resolveGomsReport(project.getFileName().toString(),
                editable.getFileName().toString()));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveGomsReport("..", editable.getFileName().toString()));
    }

    @Test
    void scansMapBuildsTreeWritesReportAndUsesDedicatedHistory() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.writeString(project.resolve("topic.dita"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><concept id=\"t\"><title>제목</title><conbody><p>내용</p></conbody></concept>",
                StandardCharsets.UTF_8);
        Path map = project.resolve("root.ditamap");
        Files.writeString(map,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map><topicref href=\"topic.dita\"><topicmeta><navtitle>장</navtitle></topicmeta></topicref></map>",
                StandardCharsets.UTF_8);

        SchematronHistoryService history = mock(SchematronHistoryService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        when(history.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(7L);
        when(users.getUserId(authentication)).thenReturn("tester");
        when(users.getUserName(authentication)).thenReturn("테스터");
        Path schemaFolder = Files.createDirectories(tempDir.resolve("schematron"));
        Files.writeString(schemaFolder.resolve("DOCS_PUB_CHECK_v1.0.0.sch"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="title-check"><rule context="concept">
                    <assert type="DOCS" role="Major" test="title">title이 필요합니다.</assert>
                  </rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        DitaValidationProgressService progress = new DitaValidationProgressService();
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                new DitaSchematronService(), history, new SchematronFileService(schemaFolder.toString()),
                users, progress, tempDir.resolve("reports").toString());

        assertEquals(1, service.findMaps(project.toString()).size());
        MapTree tree = service.loadTree(map.toString());
        assertEquals("장", tree.roots().get(0).title());

        RunResponse result = service.run(new RunRequest(project.toString(), map.toString(),
                "DOCS", "ALL", Set.of(), true, "test-progress-1"), authentication);

        assertEquals(7L, result.historyId());
        assertEquals("SUCCESS", result.status());
        assertEquals("/api/dita-validation/reports/7", result.reportUrl());
        Path report = Files.walk(tempDir.resolve("reports"))
                .filter(path -> path.getFileName().toString().matches("\\d{6}_\\d{4}_root_SchematronReport\\.html"))
                .findFirst()
                .orElseThrow();
        String reportHtml = Files.readString(report, StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals("SUCCESS", progress.get("test-progress-1").status()),
                () -> assertEquals(2, progress.get("test-progress-1").completed()),
                () -> assertEquals(2, progress.get("test-progress-1").total()),
                () -> assertNotNull(progress.get("test-progress-1").updatedAt()),
                () -> assertTrue(reportHtml.contains("검사 완료 시간:")),
                () -> assertTrue(reportHtml.contains("QA 테스터: <b>테스터</b>")),
                () -> assertTrue(reportHtml.contains("<body class=\"report-loading\" data-history-id=\"7\" data-map-name=\"root.ditamap\" data-report-file-name=\"" + report.getFileName() + "\">")),
                () -> assertTrue(reportHtml.contains("/api/dita-validation/reports/'+historyId+'/state")),
                () -> assertTrue(reportHtml.contains("<th>확인자</th><th>수정 시각</th>")),
                () -> assertFalse(reportHtml.contains("테스터 (tester)")));
        verify(history).success(eq(7L), anyInt(), anyInt(), argThat(Files::isRegularFile),
                argThat(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().equals("DOCS_PUB_CHECK_v1.0.0.sch")));
        verify(history, never()).fail(any(), any());
    }

    @Test
    void recordsUserCancellationAsCancelledInsteadOfFailed() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("cancel-project"));
        Path map = project.resolve("root.ditamap");
        Files.writeString(map, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map/>", StandardCharsets.UTF_8);
        Path schemaFolder = Files.createDirectories(tempDir.resolve("cancel-schematron"));
        Files.writeString(schemaFolder.resolve("DOCS_PUB_CHECK_v1.0.0.sch"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="map"><assert type="DOCS" role="none" test="true()">정상</assert></rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        DitaSchematronService validation = mock(DitaSchematronService.class);
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        DitaValidationProgressService progress = new DitaValidationProgressService();
        when(history.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(8L);
        when(users.getUserId(authentication)).thenReturn("tester");
        when(validation.validate(any(com.sts.dita.core.ValidationRequest.class),
                any(ValidationProgressListener.class), any(ValidationCancellation.class)))
                .thenThrow(new CancellationException("사용자가 Schematron 검사를 취소했습니다."));
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                validation, history, new SchematronFileService(schemaFolder.toString()), users, progress,
                tempDir.resolve("cancel-reports").toString());

        assertThrows(CancellationException.class, () -> service.run(new RunRequest(
                project.toString(), map.toString(), "DOCS", "ALL", Set.of(), true, "cancel-token"),
                authentication));

        verify(history).cancel(8L, "사용자가 Schematron 검사를 취소했습니다.");
        verify(history, never()).fail(any(), any());
        assertEquals("CANCELLED", progress.get("cancel-token").status());
    }

    @Test
    void writesGomsReportIntoGeneratedValidationFolder() throws Exception {
        Path outputRoot = Files.createDirectories(tempDir.resolve("ValidationCheck"));
        Path project = Files.createDirectories(outputRoot.resolve("NQ5_HEV_PE_27MY_English_LHD"));
        Path map = project.resolve("root.ditamap");
        Files.writeString(map, "<map><topicref href=\"topic.dita\"/></map>", StandardCharsets.UTF_8);
        Path topic = project.resolve("topic.dita");
        Files.writeString(topic, "<concept id=\"topic\"><title>Topic</title><conbody><p>내용</p></conbody></concept>",
                StandardCharsets.UTF_8);
        Path existingReportFolder = Files.createDirectories(project.resolve("report"));
        Files.writeString(existingReportFolder.resolve("existing.html"), "기존 리포트", StandardCharsets.UTF_8);
        Path oldFolder = Files.createDirectories(project.resolve("OLD"));
        Files.writeString(oldFolder.resolve("previous.dita"), "<concept id=\"old\"><title>Old</title><conbody/></concept>",
                StandardCharsets.UTF_8);
        Path schemaFolder = Files.createDirectories(tempDir.resolve("goms-schematron"));
        Files.writeString(schemaFolder.resolve("DOCS_PUB_CHECK_v1.0.0.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="map"><assert type="DOCS" role="none" test="true()">정상</assert></rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        DitaSchematronService validation = mock(DitaSchematronService.class);
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        DitaValidationProgressService progress = new DitaValidationProgressService();
        when(history.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(9L);
        when(users.getUserId(authentication)).thenReturn("tester");
        when(users.getUserName(authentication)).thenReturn("테스터");
        Instant finishedAt = Instant.parse("2026-10-29T05:35:00Z");
        when(validation.validate(any(com.sts.dita.core.ValidationRequest.class),
                any(ValidationProgressListener.class), any(ValidationCancellation.class)))
                .thenAnswer(invocation -> {
                    com.sts.dita.core.ValidationRequest stagedRequest = invocation.getArgument(0);
                    assertTrue(Files.isRegularFile(stagedRequest.mapFile()));
                    assertTrue(Files.isRegularFile(stagedRequest.projectRoot().resolve("topic.dita")));
                    assertFalse(Files.exists(stagedRequest.projectRoot().resolve("report")));
                    assertFalse(Files.exists(stagedRequest.projectRoot().resolve("OLD")));
                    ValidationIssue issue = new ValidationIssue(stagedRequest.projectRoot().resolve("topic.dita"),
                            "Topic", List.of(), 1, "minor", "whitespace", "test-rule", "DOCS",
                            "/concept[1]/conbody[1]/p[1]", "테스트 오류", 0, 1);
                    return new ValidationResult(stagedRequest.mapFile(), finishedAt.minusSeconds(10), finishedAt,
                            2, List.of(issue));
                });
        Path stagingRoot = tempDir.resolve("staging");
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                validation, history, new SchematronFileService(schemaFolder.toString()), users, progress,
                tempDir.resolve("central-reports").toString(), outputRoot.toString(), stagingRoot.toString());

        service.run(new RunRequest(project.toString(), map.toString(), "DOCS", "ALL", Set.of(), true,
                "goms-token", "GOMS"), authentication);

        Path report = Files.list(project.resolve("report"))
                .filter(path -> path.getFileName().toString().matches("\\d{6}_\\d{4}_root_report\\.html"))
                .findFirst().orElseThrow();
        String html = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(html.contains("data-report-file-name=\"" + report.getFileName() + "\""));
        assertTrue(html.contains("ditamap 경로: " + map));
        assertFalse(html.contains(stagingRoot.toString()));
        assertFalse(Files.exists(stagingRoot.resolve("history-9")));
        verify(history).success(eq(9L), eq(2), eq(1), eq(report),
                argThat(path -> path.startsWith(tempDir.resolve("central-reports"))));
    }

    @Test
    void removesGomsStagingFolderWhenValidationFails() throws Exception {
        Path outputRoot = Files.createDirectories(tempDir.resolve("failure-ValidationCheck"));
        Path project = Files.createDirectories(outputRoot.resolve("FAIL_PROJECT_Korean"));
        Path map = project.resolve("root.ditamap");
        Files.writeString(map, "<map/>", StandardCharsets.UTF_8);
        Path schemaFolder = Files.createDirectories(tempDir.resolve("failure-schematron"));
        Files.writeString(schemaFolder.resolve("DOCS_PUB_CHECK_v1.0.0.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="map"><assert type="DOCS" role="none" test="true()">정상</assert></rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        DitaSchematronService validation = mock(DitaSchematronService.class);
        SchematronHistoryService history = mock(SchematronHistoryService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        Authentication authentication = mock(Authentication.class);
        when(history.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(10L);
        when(users.getUserId(authentication)).thenReturn("tester");
        when(users.getUserName(authentication)).thenReturn("테스터");
        when(validation.validate(any(com.sts.dita.core.ValidationRequest.class),
                any(ValidationProgressListener.class), any(ValidationCancellation.class)))
                .thenThrow(new IllegalStateException("의도한 검사 실패"));
        Path stagingRoot = tempDir.resolve("failure-staging");
        DitaValidationWorkflowService service = new DitaValidationWorkflowService(
                validation, history, new SchematronFileService(schemaFolder.toString()), users,
                new DitaValidationProgressService(), tempDir.resolve("failure-reports").toString(),
                outputRoot.toString(), stagingRoot.toString());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.run(new RunRequest(project.toString(), map.toString(), "DOCS", "ALL", Set.of(),
                        true, "failure-token", "GOMS"), authentication));

        assertEquals("의도한 검사 실패", error.getMessage());
        assertFalse(Files.exists(stagingRoot.resolve("history-10")));
        verify(history).fail(eq(10L), same(error));
        verify(history, never()).success(any(), anyInt(), anyInt(), any(), any());
    }
}
