package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;

class DitaValidationWorkflowServiceTest {
    @TempDir Path tempDir;

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
        Files.writeString(schemaFolder.resolve("DOCS_CHECK_v1.0.0.sch"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="title-check"><rule context="concept">
                    <assert role="Major" test="title">title이 필요합니다.</assert>
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
                .filter(path -> path.getFileName().toString().equals("schematron-report.html"))
                .findFirst()
                .orElseThrow();
        String reportHtml = Files.readString(report, StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals("SUCCESS", progress.get("test-progress-1").status()),
                () -> assertEquals(2, progress.get("test-progress-1").completed()),
                () -> assertEquals(2, progress.get("test-progress-1").total()),
                () -> assertNotNull(progress.get("test-progress-1").updatedAt()),
                () -> assertTrue(reportHtml.contains("완료:")),
                () -> assertTrue(reportHtml.contains("검사 사용자: <b>테스터</b>")),
                () -> assertFalse(reportHtml.contains("테스터 (tester)")));
        verify(history).success(eq(7L), anyInt(), anyInt(), argThat(Files::isRegularFile),
                argThat(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().equals("DOCS_CHECK_v1.0.0.sch")));
        verify(history, never()).fail(any(), any());
    }
}
