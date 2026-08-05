package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import maoomWeb.ire.user.dto.ReplaceDarkSymbolItem;
import maoomWeb.ire.user.mapper.ReplaceDarkSymbolMapper;

/** DB에 저장된 이미지 심볼 치환 목록을 replace_dark_symbol.xml로 만든다. */
@Service
public class ReplaceDarkSymbolService {

    private static final String RESOURCE_PATH = "xsl/replace_dark_symbol.xml";

    private final ReplaceDarkSymbolMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public ReplaceDarkSymbolService(
            ReplaceDarkSymbolMapper mapper,
            JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        ensureTable();

        if(mapper.countAll() > 0){
            return;
        }

        for(ReplaceDarkSymbolItem item : readItems(RESOURCE_PATH)){
            mapper.upsert(item);
        }
    }

    public List<ReplaceDarkSymbolItem> findAll() {
        ensureTable();
        return mapper.findAll();
    }

    @Transactional
    public ReplaceDarkSymbolItem save(ReplaceDarkSymbolItem item) {
        ensureTable();
        normalizeAndValidate(item);
        mapper.upsert(item);
        return mapper.findByFromSymbol(item.getFromSymbol());
    }

    @Transactional
    public void delete(String fromSymbol) {
        ensureTable();
        String normalizedFrom = normalize(fromSymbol);

        if(normalizedFrom.isBlank()){
            throw new IllegalArgumentException(
                    "삭제할 From 값을 입력해 주세요.");
        }

        mapper.deleteByFromSymbol(normalizedFrom);
    }

    public void writeXml(Path xslDirectory) throws IOException {
        if(xslDirectory == null){
            throw new IllegalArgumentException(
                    "replace_dark_symbol.xml을 쓸 xsl 폴더가 비어 있습니다.");
        }

        ensureTable();
        Files.createDirectories(xslDirectory);
        Path outputFile = xslDirectory.resolve("replace_dark_symbol.xml");

        try{
            Document document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .newDocument();
            Element root = document.createElement("replacements");
            document.appendChild(root);

            for(ReplaceDarkSymbolItem item : mapper.findAll()){
                Element replace = document.createElement("replace");
                replace.setAttribute("from", item.getFromSymbol());
                replace.setAttribute("to", item.getToSymbol());
                root.appendChild(replace);
            }

            var transformer = TransformerFactory
                    .newInstance()
                    .newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(outputFile.toFile()));
        }catch(Exception e){
            throw new IOException(
                    "replace_dark_symbol.xml 생성 중 오류가 발생했습니다: "
                    + outputFile,
                    e);
        }
    }

    List<ReplaceDarkSymbolItem> readItems(String resourcePath) {
        try(InputStream input = new ClassPathResource(resourcePath)
                .getInputStream()){
            return readItems(input);
        }catch(Exception e){
            throw new IllegalStateException(
                    "replace_dark_symbol.xml을 DB 초기 데이터로 읽지 못했습니다: "
                    + resourcePath,
                    e);
        }
    }

    List<ReplaceDarkSymbolItem> readItems(InputStream input) {
        try{
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(input);
            NodeList nodes =
                    document.getDocumentElement().getElementsByTagName("replace");
            List<ReplaceDarkSymbolItem> items = new ArrayList<>();

            for(int index = 0; index < nodes.getLength(); index++){
                Element replace = (Element) nodes.item(index);
                ReplaceDarkSymbolItem item = new ReplaceDarkSymbolItem();
                item.setFromSymbol(replace.getAttribute("from"));
                item.setToSymbol(replace.getAttribute("to"));
                normalizeAndValidate(item);
                items.add(item);
            }

            return items;
        }catch(Exception e){
            throw new IllegalStateException(
                    "replace_dark_symbol.xml을 읽지 못했습니다.",
                    e);
        }
    }

    private void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tb_replace_dark_symbol (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    from_symbol VARCHAR(255) NOT NULL,
                    to_symbol VARCHAR(255) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_replace_dark_symbol_from (from_symbol),
                    INDEX idx_replace_dark_symbol_to (to_symbol)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void normalizeAndValidate(ReplaceDarkSymbolItem item) {
        if(item == null){
            throw new IllegalArgumentException(
                    "저장할 replace dark symbol 항목이 없습니다.");
        }

        item.setFromSymbol(normalize(item.getFromSymbol()));
        item.setToSymbol(normalize(item.getToSymbol()));

        if(item.getFromSymbol().isBlank()){
            throw new IllegalArgumentException("From 값을 입력해 주세요.");
        }
        if(item.getToSymbol().isBlank()){
            throw new IllegalArgumentException("To 값을 입력해 주세요.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
