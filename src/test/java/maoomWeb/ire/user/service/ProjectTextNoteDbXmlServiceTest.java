package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import maoomWeb.ire.user.dto.NoteDbItem;
import maoomWeb.ire.user.dto.ProjectDbItem;
import maoomWeb.ire.user.mapper.NoteDbItemMapper;
import maoomWeb.ire.user.mapper.ProjectDbItemMapper;

class ProjectTextNoteDbXmlServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesTextAndNoteXmlFilesFromDatabaseRows() throws Exception {
        ProjectTextNoteDbXmlService service = new ProjectTextNoteDbXmlService(
                new StubTextMapper(List.of(
                        text("EG", "EG_HASH", "Old <b>EG</b>", "New <b>EG</b>"),
                        text("KO", "KO_HASH", "이전 문장", "새 문장"))),
                new StubNoteMapper(List.of(
                        note("EG", "NOTE_EG_HASH", "tip", "Tip <u>text</u>"),
                        note("KO", "NOTE_KO_HASH", "note", "한글 노트"))));

        service.writeXmlFiles(tempDirectory);

        String egXml = Files.readString(
                tempDirectory.resolve("asis-tobe_eg.xml"),
                StandardCharsets.UTF_8);
        String koXml = Files.readString(
                tempDirectory.resolve("asis-tobe_ko.xml"),
                StandardCharsets.UTF_8);
        String noteXml = Files.readString(
                tempDirectory.resolve("note_db.xml"),
                StandardCharsets.UTF_8);
        String noteEgXml = Files.readString(
                tempDirectory.resolve("note_db_eg.xml"),
                StandardCharsets.UTF_8);
        String noteKoXml = Files.readString(
                tempDirectory.resolve("note_db_ko.xml"),
                StandardCharsets.UTF_8);

        assertThat(egXml)
                .contains("<pairs>")
                .contains("<pair hash=\"EG_HASH\">")
                .contains("<old>Old <b>EG</b></old>")
                .contains("<new>New <b>EG</b></new>");
        assertThat(koXml)
                .contains("<pair hash=\"KO_HASH\">")
                .contains("<old>이전 문장</old>")
                .contains("<new>새 문장</new>");
        assertThat(noteXml)
                .contains("<notes>")
                .contains("<note hash=\"NOTE_EG_HASH\" type=\"tip\">")
                .contains("<text>Tip <u>text</u></text>");
        assertThat(noteEgXml)
                .contains("<note hash=\"NOTE_EG_HASH\" type=\"tip\">")
                .doesNotContain("NOTE_KO_HASH");
        assertThat(noteKoXml)
                .contains("<note hash=\"NOTE_KO_HASH\" type=\"note\">")
                .contains("<text>한글 노트</text>")
                .doesNotContain("NOTE_EG_HASH");
    }

    private static ProjectDbItem text(
            String region,
            String hash,
            String oldText,
            String newText) {

        ProjectDbItem item = new ProjectDbItem();
        item.setRegion(region);
        item.setHash(hash);
        item.setOldText(oldText);
        item.setNewText(newText);
        return item;
    }

    private static NoteDbItem note(
            String region,
            String hash,
            String type,
            String text) {

        NoteDbItem item = new NoteDbItem();
        item.setRegion(region);
        item.setHash(hash);
        item.setNoteType(type);
        item.setNoteText(text);
        return item;
    }

    private record StubTextMapper(List<ProjectDbItem> rows)
            implements ProjectDbItemMapper {

        @Override
        public List<ProjectDbItem> findAll(String tableName) {
            return rows;
        }

        @Override
        public ProjectDbItem findByRegionAndHash(
                String tableName,
                String region,
                String hash) {
            return null;
        }

        @Override
        public int upsert(String tableName, ProjectDbItem item) {
            return 0;
        }

        @Override
        public int deleteByRegionAndHash(
                String tableName,
                String region,
                String hash) {
            return 0;
        }
    }

    private record StubNoteMapper(List<NoteDbItem> rows)
            implements NoteDbItemMapper {

        @Override
        public List<NoteDbItem> findAll() {
            return rows;
        }

        @Override
        public List<NoteDbItem> findByRegion(String region) {
            return rows.stream()
                    .filter(row -> region.equals(row.getRegion()))
                    .toList();
        }

        @Override
        public NoteDbItem findByRegionAndHash(String region, String hash) {
            return null;
        }

        @Override
        public int upsert(NoteDbItem item) {
            return 0;
        }

        @Override
        public int deleteByRegionAndHash(String region, String hash) {
            return 0;
        }
    }
}
