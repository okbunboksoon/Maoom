package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import maoomWeb.ire.user.dto.NoteDbItem;
import maoomWeb.ire.user.dto.ProjectDbItem;
import maoomWeb.ire.user.mapper.NoteDbItemMapper;
import maoomWeb.ire.user.mapper.ProjectDbItemMapper;

/** 관리자 TEXT/NOTE DB를 정제 XSL이 읽는 XML 파일로 만든다. */
@Service
public class ProjectTextNoteDbXmlService {

    private static final String TEXT_TABLE = "tb_project_text_db";

    private final ProjectDbItemMapper textMapper;
    private final NoteDbItemMapper noteMapper;

    public ProjectTextNoteDbXmlService(
            ProjectDbItemMapper textMapper,
            NoteDbItemMapper noteMapper) {
        this.textMapper = textMapper;
        this.noteMapper = noteMapper;
    }

    public void writeXmlFiles(Path xslDirectory) throws IOException {
        Files.createDirectories(xslDirectory);
        writeTextRegionXmlFiles(xslDirectory);
        writeNoteXml("EG", xslDirectory.resolve("note_db.xml"));
        writeNoteXml("EG", xslDirectory.resolve("note_db_eg.xml"));
        writeNoteXml("KO", xslDirectory.resolve("note_db_ko.xml"));
    }

    private void writeTextRegionXmlFiles(Path xslDirectory)
            throws IOException {

        // XSL 파일명이 지역 코드별로 고정되어 있어 DB 행도 region 단위로 묶어 출력한다.
        Map<String,List<ProjectDbItem>> byRegion = new LinkedHashMap<>();
        for(ProjectDbItem item : textMapper.findAll(TEXT_TABLE)){
            String region = item.getRegion();
            if(region == null || region.isBlank()){
                continue;
            }
            byRegion.computeIfAbsent(region.toLowerCase(), key -> new java.util.ArrayList<>())
                    .add(item);
        }

        for(Map.Entry<String,List<ProjectDbItem>> entry : byRegion.entrySet()){
            writeTextXml(
                    entry.getValue(),
                    xslDirectory.resolve("asis-tobe_" + entry.getKey() + ".xml"));
        }
    }

    private void writeTextXml(
            List<ProjectDbItem> items,
            Path outputFile) throws IOException {

        try{
            Document document = BerXmlFragments.newDocument();
            Element root = document.createElement("pairs");
            document.appendChild(root);

            for(ProjectDbItem item : items){
                validateTextItem(item);
                Element pair = document.createElement("pair");
                pair.setAttribute("hash", item.getHash());
                // old/new 값에는 inline 태그가 들어올 수 있어 텍스트가 아닌 XML fragment로 붙인다.
                appendTextElement(document, pair, "old", item.getOldText());
                appendTextElement(document, pair, "new", item.getNewText());
                root.appendChild(pair);
            }

            writeDocument(document, outputFile);
        }catch(Exception e){
            throw new IOException(
                    "TEXT DB XML 생성 중 오류가 발생했습니다: " + outputFile,
                    e);
        }
    }

    private void writeNoteXml(String region, Path outputFile) throws IOException {
        try{
            Document document = BerXmlFragments.newDocument();
            Element root = document.createElement("notes");
            document.appendChild(root);

            for(NoteDbItem item : noteMapper.findByRegion(region)){
                validateNoteItem(item);
                Element note = document.createElement("note");
                note.setAttribute("hash", item.getHash());
                note.setAttribute("type", item.getNoteType());
                // note 본문도 태그 포함 가능성을 열어 두고 fragment 파서에 맡긴다.
                appendTextElement(document, note, "text", item.getNoteText());
                root.appendChild(note);
            }

            writeDocument(document, outputFile);
        }catch(Exception e){
            throw new IOException(
                    "NOTE DB XML 생성 중 오류가 발생했습니다: " + outputFile,
                    e);
        }
    }

    private void appendTextElement(
            Document document,
            Element parent,
            String elementName,
            String value) {

        Element element = document.createElement(elementName);
        BerXmlFragments.appendFragment(document, element, value);
        parent.appendChild(element);
    }

    private void writeDocument(Document document, Path outputFile)
            throws Exception {

        var transformer = TransformerFactory
                .newInstance()
                .newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.transform(
                new DOMSource(document),
                new StreamResult(outputFile.toFile()));
    }

    private void validateTextItem(ProjectDbItem item) {
        if(item.getHash() == null || item.getHash().isBlank()){
            throw new IllegalArgumentException(
                    "TEXT DB에 hash가 비어 있는 행이 있습니다.");
        }
        if(item.getNewText() == null || item.getNewText().isBlank()){
            throw new IllegalArgumentException(
                    "TEXT DB에 new_text가 비어 있습니다. hash="
                    + item.getHash());
        }
    }

    private void validateNoteItem(NoteDbItem item) {
        if(item.getHash() == null || item.getHash().isBlank()){
            throw new IllegalArgumentException(
                    "NOTE DB에 hash가 비어 있는 행이 있습니다.");
        }
        if(item.getNoteType() == null || item.getNoteType().isBlank()){
            throw new IllegalArgumentException(
                    "NOTE DB에 type이 비어 있습니다. hash="
                    + item.getHash());
        }
    }
}
