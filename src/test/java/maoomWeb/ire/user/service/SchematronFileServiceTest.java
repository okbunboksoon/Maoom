package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchematronFileServiceTest {
    @TempDir Path tempDir;

    @Test
    void seedsAndValidatesBundledDefaultOnlyOnFirstDirectoryCreation() throws Exception {
        Path folder = tempDir.resolve("new-schemas");
        SchematronFileService service = new SchematronFileService(folder.toString());

        var initial = service.listFiles();
        assertEquals(2, initial.size());
        assertEquals(java.util.List.of("DOCS_CHECK_v1.0.6.sch", "PUB_CHECK_v1.0.1.sch"),
                initial.stream().map(item -> item.fileName()).toList());
        assertTrue(initial.stream().allMatch(item -> item.valid()),
                () -> initial.toString());

        Files.delete(folder.resolve("DOCS_CHECK_v1.0.6.sch"));
        Files.delete(folder.resolve("PUB_CHECK_v1.0.1.sch"));
        assertTrue(service.listFiles().isEmpty(), "사용자가 마지막 파일을 삭제하면 자동 복원하지 않아야 합니다.");
    }

    @Test
    void currentProjectSchemasPassLintAndProvideBothChoices() throws Exception {
        SchematronFileService service = new SchematronFileService(Path.of("schematron").toString());
        var choices = service.latestChoices();
        assertAll(
                () -> assertTrue(choices.stream().filter(choice -> choice.type().equals("DOCS"))
                        .findFirst().orElseThrow().available(), () -> choices.toString()),
                () -> assertTrue(choices.stream().filter(choice -> choice.type().equals("PUB"))
                        .findFirst().orElseThrow().available(), () -> choices.toString()));
    }

    @Test
    void selectsLatestDocsAndPublishingVersionsNumerically() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("versioned-schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());
        Files.writeString(folder.resolve("DOCS_CHECK_v1.0.9.sch"), validSchema("docs-109"), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("DOCS_CHECK_v1.0.10.sch"), validSchema("docs-1010"), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("PUB_CHECK_v2.1.0.sch"), validSchema("pub-210"), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("DOCS_CHECK_backup.sch"), validSchema("ignored"), StandardCharsets.UTF_8);

        var choices = service.latestChoices();
        assertEquals("DOCS_CHECK_v1.0.10.sch", choices.stream()
                .filter(choice -> choice.type().equals("DOCS")).findFirst().orElseThrow().fileName());
        assertEquals("PUB_CHECK_v2.1.0.sch", choices.stream()
                .filter(choice -> choice.type().equals("PUB")).findFirst().orElseThrow().fileName());
        assertEquals("DOCS_CHECK_v1.0.10.sch", service.resolveLatest("DOCS").getFileName().toString());
        assertEquals("PUB_CHECK_v2.1.0.sch", service.resolveLatest("PUB").getFileName().toString());
    }

    @Test
    void fallsBackToThePreviousValidVersionWhenTheNewestFileIsInvalid() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("fallback-schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());
        Files.writeString(folder.resolve("PUB_CHECK_v1.0.1.sch"), validSchema("pub-101"), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("PUB_CHECK_v1.0.2.sch"),
                "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"><pattern id=\"empty\"/></schema>",
                StandardCharsets.UTF_8);

        var publishing = service.latestChoices().stream()
                .filter(choice -> choice.type().equals("PUB")).findFirst().orElseThrow();

        assertAll(
                () -> assertTrue(publishing.available()),
                () -> assertEquals("PUB_CHECK_v1.0.1.sch", publishing.fileName()),
                () -> assertEquals("v1.0.1", publishing.version()),
                () -> assertTrue(publishing.validationMessage().startsWith(
                        "최신 v1.0.2 오류로 v1.0.1 사용 중")),
                () -> assertEquals("PUB_CHECK_v1.0.1.sch", service.resolveLatest("PUB").getFileName().toString()));
    }

    @Test
    void listsAddedAndRemovedSchemasWithoutRestart() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());
        Path first = folder.resolve("A.sch");
        Path second = folder.resolve("B.sch");
        Files.writeString(first, validSchema("a"), StandardCharsets.UTF_8);
        assertEquals(java.util.List.of("A.sch"), service.listFiles().stream().map(item -> item.fileName()).toList());

        Files.writeString(second, validSchema("b"), StandardCharsets.UTF_8);
        assertEquals(java.util.List.of("A.sch", "B.sch"),
                service.listFiles().stream().map(item -> item.fileName()).toList());

        Files.delete(first);
        assertEquals(java.util.List.of("B.sch"), service.listFiles().stream().map(item -> item.fileName()).toList());
    }

    @Test
    void rejectsTraversalAndInvalidSchematron() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());
        assertThrows(IllegalArgumentException.class, () -> service.resolveAndValidate("../outside.sch"));

        Files.writeString(folder.resolve("broken.sch"),
                "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"><pattern id=\"p\"/></schema>",
                StandardCharsets.UTF_8);
        var item = service.listFiles().stream().filter(value -> value.fileName().equals("broken.sch")).findFirst().orElseThrow();
        assertFalse(item.valid());
        assertThrows(IllegalArgumentException.class, () -> service.resolveAndValidate("broken.sch"));
    }

    @Test
    void copiesValidatedSnapshotAndKeepsHash() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());
        Path source = folder.resolve("check.sch");
        Files.writeString(source, validSchema("snapshot"), StandardCharsets.UTF_8);

        String sourceHash = service.sha256(source);
        Path snapshot = service.copySnapshot(service.resolveAndValidate("check.sch"), tempDir.resolve("result"));

        assertTrue(Files.isRegularFile(snapshot));
        assertEquals(sourceHash, service.sha256(snapshot));
    }

    @Test
    void lintsDuplicateIdsUnusedRulesAndRequiredMetadata() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("lint-schemas"));
        SchematronFileService service = new SchematronFileService(folder.toString());

        Path duplicate = folder.resolve("duplicate.sch");
        Files.writeString(duplicate, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="same"><rule context="concept"><assert role="none" test="true()">A</assert></rule></pattern>
                  <pattern id="same"><rule context="concept"><assert role="none" test="true()">B</assert></rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.validate(duplicate)).getMessage().contains("중복 pattern id"));

        Path missingCategory = folder.resolve("missing-category.sch");
        Files.writeString(missingCategory, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:maoom="urn:maoom:schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="concept">
                    <assert role="none" test="title">메시지</assert>
                  </rule></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        IllegalArgumentException categoryError = assertThrows(IllegalArgumentException.class,
                () -> service.validate(missingCategory));
        assertTrue(categoryError.getMessage().contains("maoom:category"));

        Path emptyRule = folder.resolve("empty-rule.sch");
        Files.writeString(emptyRule, """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="concept"/></pattern>
                </schema>
                """, StandardCharsets.UTF_8);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.validate(emptyRule)).getMessage().contains("사용되지 않는 rule"));
    }

    private static String validSchema(String id) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:maoom="urn:maoom:schematron" queryBinding="xslt2">
                  <pattern id="%s"><rule context="concept">
                    <assert role="Major" maoom:category="structure" test="title">title이 필요합니다.</assert>
                  </rule></pattern>
                </schema>
                """.formatted(id);
    }
}
