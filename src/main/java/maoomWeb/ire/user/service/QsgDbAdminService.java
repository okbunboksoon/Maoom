package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import maoomWeb.ire.user.dto.QsgDbImportResult;
import maoomWeb.ire.user.dto.QsgDbTerm;

/**
 * QSG_DB.xml을 관리자 화면용 행 목록으로 변환한다.
 *
 * <p>원본 XML 구조는 아래처럼 hash 하나 아래에 여러 언어 term이 들어 있다.</p>
 *
 * <pre>
 * &lt;entry hash="..."&gt;
 *   &lt;term lang="EN-GB"&gt;...&lt;/term&gt;
 *   &lt;term lang="ko-KR"&gt;...&lt;/term&gt;
 * &lt;/entry&gt;
 * </pre>
 *
 * <p>관리자 테이블은 검색/정렬이 쉬워야 하므로 이를
 * {@code hash + lang + term} 세로형 행으로 펼친다.
 * 따라서 화면에서 한 row의 유니크 기준은 {@code hash + lang}이고,
 * {@code hash} 단독은 같은 문장 묶음을 가리키는 그룹 키다.</p>
 *
 * <p>주의: 이 서비스는 아직 DB 저장을 하지 않는다. QSG DB를 실제 테이블로
 * 이관할 때는 이 파일을 대체하거나, 이 로직을 초기 적재(seed) 용도로 옮기면 된다.</p>
 */
@Service
public class QsgDbAdminService {

    /** 관리자 QSG DB 화면이 읽는 원본 XML. QSG 배치 XSL도 같은 파일명을 참조한다. */
    private static final String QSG_DB_PATH = "xsl/QSG_DB.xml";
    private static final int HEADER_SEARCH_ROW_LIMIT = 20;

    /**
     * QSG_DB.xml 전체를 읽어서 관리자 화면 테이블에 그대로 표시할 행 목록을 만든다.
     *
     * <p>정렬은 hash, lang 순서로 고정한다. 화면 DataTable에서도 정렬할 수 있지만,
     * API 응답 자체가 항상 같은 순서로 나오면 테스트와 장애 확인이 쉬워진다.</p>
     */
    public List<QsgDbTerm> findAll() {
        try{
            return toTerms(readDocument());
        }catch(IOException
                | ParserConfigurationException
                | SAXException exception){
            throw new IllegalStateException(
                    "QSG_DB.xml을 읽지 못했습니다.",
                    exception);
        }
    }

    /**
     * QSG 배치가 실행될 임시 작업 폴더의 xsl 폴더에 현재 QSG_DB.xml을 쓴다.
     *
     * <p>BER 배치가 관리자 BER DB를 실행 직전에 {@code asis-tobe_*.xml}로
     * 다시 만들어 넣는 것과 같은 목적이다. 관리자 화면에서 엑셀 업로드로
     * 수정한 QSG_DB.xml이 classpath 복사본보다 우선해야 하므로,
     * {@link QsgApplyService}는 공용 xsl 폴더를 복사한 뒤 이 메서드로
     * {@code QSG_DB.xml}을 한 번 더 덮어쓴다.</p>
     *
     * <p>현재 QSG DB는 아직 별도 DB 테이블이 아니라 XML 파일이 원본이다.
     * 개발 실행에서는 {@code src/main/resources/xsl/QSG_DB.xml}을 우선 사용하고,
     * 패키징 실행처럼 원본 파일이 없으면 classpath의 {@code xsl/QSG_DB.xml}을
     * 사용한다. 나중에 DB 테이블로 이관할 때는 이 메서드 내부만 DB 조회 후
     * XML 생성 방식으로 바꾸면 배치 서비스는 그대로 둘 수 있다.</p>
     */
    public void writeQsgDbXml(Path xslDirectory) throws IOException {
        if(xslDirectory == null){
            throw new IllegalArgumentException(
                    "QSG_DB.xml을 쓸 xsl 폴더가 비어 있습니다.");
        }

        Files.createDirectories(xslDirectory);
        Path outputFile = xslDirectory.resolve("QSG_DB.xml");
        Path editablePath = editableQsgDbPath();

        if(editablePath != null){
            Files.copy(
                    editablePath,
                    outputFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        try(InputStream input = new ClassPathResource(QSG_DB_PATH)
                .getInputStream()){
            Files.copy(
                    input,
                    outputFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 관리자가 업로드한 엑셀을 QSG_DB.xml에 반영한다.
     *
     * <p>업로드 엑셀은 {@code hash, lang, term} 헤더가 있으면 된다.
     * {@code hash + lang}이 이미 있으면 term을 수정하고, 없으면 해당 hash entry나
     * term을 새로 만든다. 즉 사용자가 엑셀에서 term 값을 바꿔 올리면 같은
     * {@code hash + lang} 행에 반영된다.</p>
     */
    @Transactional
    public QsgDbImportResult importExcel(InputStream excelInput)
            throws IOException {

        if(excelInput == null){
            throw new IllegalArgumentException(
                    "엑셀 파일이 비어 있습니다.");
        }

        try(Workbook workbook = WorkbookFactory.create(excelInput)){
            Sheet sheet = findImportSheet(workbook);
            HeaderColumns headers = findHeaders(sheet);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();
            Map<String,QsgDbTerm> imports = new LinkedHashMap<>();
            int totalRows = 0;
            int skippedCount = 0;

            for(int rowIndex = headers.rowIndex() + 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++){
                Row row = sheet.getRow(rowIndex);
                if(row == null){
                    continue;
                }

                String hash = formatCell(
                        row,
                        headers.hashColumn(),
                        formatter,
                        evaluator).trim();
                String lang = formatCell(
                        row,
                        headers.langColumn(),
                        formatter,
                        evaluator).trim();
                String term = formatCell(
                        row,
                        headers.termColumn(),
                        formatter,
                        evaluator);

                if(hash.isBlank() && lang.isBlank() && term.isBlank()){
                    continue;
                }

                totalRows++;

                if(hash.isBlank() || lang.isBlank()){
                    skippedCount++;
                    continue;
                }

                imports.put(hash + "\n" + lang, new QsgDbTerm(
                        hash,
                        lang,
                        term));
            }

            if(imports.isEmpty()){
                throw new IllegalArgumentException(
                        "등록할 QSG DB 데이터가 없습니다.");
            }

            Document document = readDocument();
            int insertedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for(QsgDbTerm item : imports.values()){
                ApplyStatus status = applyTerm(document, item);
                if(status == ApplyStatus.INSERTED){
                    insertedCount++;
                }else if(status == ApplyStatus.UPDATED){
                    updatedCount++;
                }else{
                    unchangedCount++;
                }
            }

            writeDocument(document);
            return new QsgDbImportResult(
                    totalRows,
                    insertedCount,
                    updatedCount,
                    unchangedCount,
                    skippedCount);
        }catch(ParserConfigurationException
                | SAXException
                | TransformerException exception){
            throw new IllegalStateException(
                    "QSG_DB.xml 반영 중 오류가 발생했습니다.",
                    exception);
        }
    }

    private Document readDocument()
            throws IOException, ParserConfigurationException, SAXException {
        Path editablePath = editableQsgDbPath();
        try(InputStream input = editablePath == null
                ? new ClassPathResource(QSG_DB_PATH).getInputStream()
                : Files.newInputStream(editablePath)){
            return newDocumentBuilder().parse(input);
        }
    }

    private DocumentBuilder newDocumentBuilder()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        /*
         * QSG_DB.xml은 프로젝트 내부 리소스지만 XML 파서는 외부 엔티티를
         * 기본 허용할 수 있다. 관리자 화면 조회용이라도 XML 외부 엔티티(XXE)는
         * 열어둘 이유가 없으므로 명시적으로 막아 둔다.
         */
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private List<QsgDbTerm> toTerms(Document document) {
        NodeList entries = document.getElementsByTagName("entry");
        List<QsgDbTerm> terms = new ArrayList<>();

        /*
         * entry 한 개는 hash 그룹이고, 그 안의 term들이 실제 화면 row가 된다.
         * 예: hash=ABC 아래 EN-GB/ko-KR/ja-JP가 있으면 화면에는 3줄이 표시된다.
         */
        for(int entryIndex = 0;
                entryIndex < entries.getLength();
                entryIndex++){
            Node entryNode = entries.item(entryIndex);
            if(entryNode.getNodeType() != Node.ELEMENT_NODE){
                continue;
            }

            Element entry = (Element) entryNode;
            String hash = entry.getAttribute("hash");
            NodeList termNodes = entry.getElementsByTagName("term");

            for(int termIndex = 0;
                    termIndex < termNodes.getLength();
                    termIndex++){
                Node termNode = termNodes.item(termIndex);
                if(termNode.getNodeType() != Node.ELEMENT_NODE){
                    continue;
                }

                Element term = (Element) termNode;
                terms.add(new QsgDbTerm(
                        hash,
                        term.getAttribute("lang"),
                        term.getTextContent()));
            }
        }

        terms.sort(Comparator
                .comparing(QsgDbTerm::getHash,
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(QsgDbTerm::getLang,
                        Comparator.nullsLast(String::compareTo)));
        return terms;
    }

    private ApplyStatus applyTerm(Document document, QsgDbTerm item) {
        Element entry = findEntry(document, item.getHash());
        if(entry == null){
            entry = document.createElement("entry");
            entry.setAttribute("hash", item.getHash());
            document.getDocumentElement().appendChild(entry);
        }

        Element term = findTerm(entry, item.getLang());
        if(term == null){
            term = document.createElement("term");
            term.setAttribute("lang", item.getLang());
            term.setTextContent(item.getTerm() == null ? "" : item.getTerm());
            entry.appendChild(term);
            return ApplyStatus.INSERTED;
        }

        String nextTerm = item.getTerm() == null ? "" : item.getTerm();
        if(Objects.equals(term.getTextContent(), nextTerm)){
            return ApplyStatus.UNCHANGED;
        }

        term.setTextContent(nextTerm);
        return ApplyStatus.UPDATED;
    }

    private Element findEntry(Document document, String hash) {
        NodeList entries = document.getElementsByTagName("entry");
        for(int index = 0; index < entries.getLength(); index++){
            Element entry = (Element) entries.item(index);
            if(Objects.equals(entry.getAttribute("hash"), hash)){
                return entry;
            }
        }
        return null;
    }

    private Element findTerm(Element entry, String lang) {
        NodeList terms = entry.getElementsByTagName("term");
        for(int index = 0; index < terms.getLength(); index++){
            Element term = (Element) terms.item(index);
            if(Objects.equals(term.getAttribute("lang"), lang)){
                return term;
            }
        }
        return null;
    }

    private void writeDocument(Document document)
            throws IOException, TransformerException {
        Path path = qsgDbFilePath();
        Transformer transformer =
                TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        try(OutputStream output = Files.newOutputStream(path)){
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(output));
        }
    }

    private Path qsgDbFilePath() throws IOException {
        Path editablePath = editableQsgDbPath();
        if(editablePath != null){
            return editablePath;
        }

        Resource resource = new ClassPathResource(QSG_DB_PATH);
        if(!resource.isFile()){
            throw new IllegalStateException(
                    "QSG_DB.xml을 파일로 수정할 수 없는 실행 환경입니다.");
        }
        return resource.getFile().toPath();
    }

    private Path editableQsgDbPath() {
        /*
         * STS/개발 실행에서는 user.dir이 프로젝트 루트라서 src/main/resources의
         * 원본 XML을 직접 수정할 수 있다. 이렇게 해야 업로드 반영 결과가
         * 다음 실행과 버전 관리에 남는다. 패키징된 JAR처럼 원본 경로가 없으면
         * classpath 파일을 읽고, 쓰기 가능한 파일 실행 환경에서만 저장한다.
         */
        Path path = Path.of("src", "main", "resources", QSG_DB_PATH);
        return Files.isRegularFile(path) ? path : null;
    }

    private Sheet findImportSheet(Workbook workbook) {
        for(Sheet sheet : workbook){
            try{
                findHeaders(sheet);
                return sheet;
            }catch(IllegalArgumentException ignored){
            }
        }

        throw new IllegalArgumentException(
                "엑셀에서 hash, lang, term 헤더를 찾지 못했습니다.");
    }

    private HeaderColumns findHeaders(Sheet sheet) {
        int lastSearchRow = Math.min(
                sheet.getLastRowNum(),
                HEADER_SEARCH_ROW_LIMIT - 1);

        for(int rowIndex = sheet.getFirstRowNum();
                rowIndex <= lastSearchRow;
                rowIndex++){
            Row row = sheet.getRow(rowIndex);
            if(row == null){
                continue;
            }

            Integer hashColumn = null;
            Integer langColumn = null;
            Integer termColumn = null;
            for(int column = row.getFirstCellNum();
                    column >= 0 && column < row.getLastCellNum();
                    column++){
                String header = new DataFormatter(Locale.KOREA)
                        .formatCellValue(row.getCell(column))
                        .replaceAll("\\s+", "")
                        .replace("-", "_")
                        .toLowerCase(Locale.ROOT);

                if(header.equals("hash")){
                    hashColumn = column;
                }else if(header.equals("lang")
                        || header.equals("language")
                        || header.equals("언어")){
                    langColumn = column;
                }else if(header.equals("term")
                        || header.equals("text")
                        || header.equals("문구")){
                    termColumn = column;
                }
            }

            if(hashColumn != null && langColumn != null
                    && termColumn != null){
                return new HeaderColumns(
                        rowIndex,
                        hashColumn,
                        langColumn,
                        termColumn);
            }
        }

        throw new IllegalArgumentException("필수 헤더가 없습니다.");
    }

    private String formatCell(
            Row row,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        return formatter.formatCellValue(row.getCell(column), evaluator);
    }

    private record HeaderColumns(
            int rowIndex,
            int hashColumn,
            int langColumn,
            int termColumn) {
    }

    private enum ApplyStatus {
        INSERTED,
        UPDATED,
        UNCHANGED
    }
}
