package maoomWeb.ire.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import maoomWeb.ire.user.dto.DitamapTreeNode;
import maoomWeb.ire.user.dto.DitamapTreeResponse;
import maoomWeb.ire.user.dto.DitamapAttributeUpdate;
import maoomWeb.ire.user.dto.DitamapAttributeUpdateRequest;
import maoomWeb.ire.user.dto.DitamapAttributeUpdateResponse;
import maoomWeb.ire.user.dto.DitamapLegalRow;
import maoomWeb.ire.user.dto.DitamapLegalSaveRequest;
import maoomWeb.ire.user.dto.DitamapLegalSaveResponse;
import maoomWeb.ire.user.dto.DitamapLegalTarget;
import maoomWeb.ire.user.dto.DitamapTopicTitleRequest;
import maoomWeb.ire.user.dto.DitamapTopicTitleResponse;
import maoomWeb.ire.user.mapper.DitamapLegalTargetMapper;

/** 설정으로 허용한 DITA 작업 폴더의 DITAMAP 파일을 화면용 트리 구조로 변환한다. */
@Service
public class DitamapBuilderService {

    private static final String[] ATTRIBUTE_CANDIDATES = {
            "veh-legalid"
    };
    private static final String MANUAL_CHECKED_GROUP_TITLE =
            "사용설명서(V체크한 타이틀)";

    private final List<Path> allowedRoots;
    private final Map<String, String> mappedDriveCache =
            new ConcurrentHashMap<>();
    private final DitamapLegalHashService ditamapLegalHashService;
    private final DitamapLegalTargetMapper ditamapLegalTargetMapper;
    private final JdbcTemplate jdbcTemplate;

    public DitamapBuilderService(
            @Value("${ditamap.builder.allowed-roots:}") String configuredRoots,
            DitamapLegalHashService ditamapLegalHashService,
            DitamapLegalTargetMapper ditamapLegalTargetMapper,
            JdbcTemplate jdbcTemplate) {
        this.allowedRoots = createAllowedRoots(configuredRoots);
        this.ditamapLegalHashService = ditamapLegalHashService;
        this.ditamapLegalTargetMapper = ditamapLegalTargetMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DitamapTreeResponse readTree(String rawPath) {
        return readTree(rawPath, false);
    }

    public DitamapTreeResponse readTreeSummary(String rawPath) {
        return readTree(rawPath, true);
    }

    public List<String> readDitaFiles(String rawPath) {
        try{
            Path ditamap = findDitamap(rawPath);
            Path directory = ditamap.getParent();

            if(directory == null || !Files.isDirectory(directory)){
                return List.of();
            }

            try(var stream = Files.walk(directory)){
                return stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(fileName -> fileName.toLowerCase(Locale.ROOT)
                                .endsWith(".dita"))
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        }catch(IOException exception){
            throw new IllegalArgumentException(
                    "DITA 파일 목록을 읽지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    public DitamapTreeResponse readLegalTemplate() {
        Resource template =
                new ClassPathResource("revision-tool/xsl/LM-ditamap.ditamap");

        if(!template.exists()){
            template = new ClassPathResource("revision-tool/xsl/LM-template.xml");
        }

        if(!template.exists()){
            throw new IllegalArgumentException(
                    "법규 DITAMAP 템플릿을 찾지 못했습니다.");
        }

        try(InputStream input = template.getInputStream()){
            Document document = parseXml(input);
            Element root = document.getDocumentElement();

            return new DitamapTreeResponse(
                    "법규 DITAMAP",
                    template.getFilename(),
                    readLegalTemplateChildren(root, 1));
        }catch(IOException | ParserConfigurationException
                | SAXException exception){
            throw new IllegalArgumentException(
                    "법규 DITAMAP 템플릿을 읽지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    public DitamapTreeResponse readLegalMaster() {
        Resource master =
                new ClassPathResource("revision-tool/xsl/LM-ditamap.ditamap");

        if(!master.exists()){
            throw new IllegalArgumentException(
                    "법규 마스터 DITAMAP을 찾지 못했습니다.");
        }

        try(InputStream input = master.getInputStream()){
            Document document = parseXml(input);
            Element root = document.getDocumentElement();

            return new DitamapTreeResponse(
                    readMapTitle(root, Path.of("LM-ditamap.ditamap")),
                    "revision-tool/xsl/LM-ditamap.ditamap",
                    readLegalMasterChildren(root, 1));
        }catch(IOException | ParserConfigurationException
                | SAXException exception){
            throw new IllegalArgumentException(
                    "법규 마스터 DITAMAP을 읽지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    public List<String> readLegalTargetFiles() {
        return readLegalTargets().stream()
                .map(DitamapLegalTarget::getFileName)
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedLegalTargetsOnStartup() {
        ensureLegalTargetsSeeded();
    }

    public List<DitamapLegalTarget> readLegalTargets() {
        ensureLegalTargetsSeeded();
        return ditamapLegalTargetMapper.findAll();
    }

    public DitamapTopicTitleResponse readTopicTitle(
            DitamapTopicTitleRequest request) {
        /*
         * 화면에서 파일명을 바꾸면 ditamap row의 title도 같이 맞춰야 한다.
         * 이 메서드는 기준 ditamap 위치와 새 파일명으로 실제 DITA 파일을 찾아
         * title만 읽어 오며, DITA 원본 파일은 절대 수정하지 않는다.
         */
        if(request == null
                || request.baseDitamapFile() == null
                || request.baseDitamapFile().isBlank()){
            throw new IllegalArgumentException(
                    "기준 DITAMAP 경로가 없어 title을 읽을 수 없습니다.");
        }

        String nextHref = normalizeEditedHref(request.href(), request.fileName());

        if(nextHref.isBlank()){
            throw new IllegalArgumentException("파일명을 입력해 주세요.");
        }

        try{
            Path baseDitamap = findDitamap(request.baseDitamapFile());
            Path target = resolveHref(baseDitamap.getParent(), nextHref);

            /*
             * ditamap 안의 topicref href는 기준 ditamap 폴더 기준 상대 경로다.
             * 따라서 먼저 실제 파일 경로를 계산한 뒤, 존재하는 일반 DITA 파일인지
             * 확인한다. ditamap 파일은 title 동기화 대상에서 제외한다.
             */
            if(target == null
                    || !Files.exists(target)
                    || !Files.isRegularFile(target)
                    || isDitamap(target)){
                throw new IllegalArgumentException(
                        "DITA 파일을 찾지 못했습니다: " + nextHref);
            }

            Path realAllowedRoot = findAllowedRoot(target.toRealPath());

            /*
             * 사용자가 파일명에 ../ 같은 값을 넣어 허용 폴더 밖 파일을 읽지 못하게
             * 실제 경로 기준으로 다시 한 번 allowed root 내부인지 검증한다.
             */
            if(!target.toRealPath().startsWith(realAllowedRoot)){
                throw new IllegalArgumentException(
                        "허용된 DITA 작업 경로 밖의 파일입니다: " + nextHref);
            }

            Document document = parseXml(target);
            String title = firstDirectChildText(
                    document.getDocumentElement(),
                    "title");

            // title이 비어 있는 DITA도 화면이 깨지지 않도록 파일명을 fallback으로 쓴다.
            if(title.isBlank()){
                title = target.getFileName()
                        .toString()
                        .replaceFirst("(?i)\\.(dita|ditamap)$", "");
            }

            return new DitamapTopicTitleResponse(
                    title,
                    target.getFileName().toString(),
                    nextHref);
        }catch(IOException | ParserConfigurationException | SAXException exception){
            throw new IllegalArgumentException(
                    "DITA title을 읽지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    private void ensureLegalTargetsSeeded() {
        ensureLegalTargetTable();

        for(DitamapLegalTarget target : readDefaultLegalTargets()){
            ditamapLegalTargetMapper.insertIgnore(target);
        }
    }

    private void ensureLegalTargetTable() {
        /*
         * spring.sql.init.schema-locations가 local 설정에서 덮이거나,
         * 서버를 재시작하지 않은 상태로 2안 API만 먼저 호출하면 테이블이 없어서 500이 날 수 있다.
         * 2안 API 진입 전에도 테이블을 한 번 더 보장해 배포/개발 PC 설정 차이를 흡수한다.
         */
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tb_ditamap_legal_target (
                    file_name VARCHAR(255) NOT NULL,
                    title VARCHAR(1000) NOT NULL DEFAULT '',
                    parent_l1_file VARCHAR(255) NOT NULL DEFAULT '',
                    parent_l2_file VARCHAR(255) NOT NULL DEFAULT '',
                    level_no INT NOT NULL DEFAULT 3,
                    reg_dt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (file_name),
                    INDEX idx_ditamap_legal_target_parent (parent_l1_file, parent_l2_file)
                )
                """);
    }

    private List<DitamapLegalTarget> readDefaultLegalTargets() {
        Resource template =
                new ClassPathResource("revision-tool/xsl/LM-ditamap.ditamap");

        if(!template.exists()){
            template = new ClassPathResource("revision-tool/xsl/LM-template.xml");
        }

        if(!template.exists()){
            return List.of();
        }

        try(InputStream input = template.getInputStream()){
            Document document = parseXml(input);
            List<DitamapLegalTarget> targets = new ArrayList<>();
            collectLegalTargets(
                    document.getDocumentElement(),
                    1,
                    "",
                    "",
                    targets);
            return targets;
        }catch(IOException | ParserConfigurationException
                | SAXException exception){
            throw new IllegalArgumentException(
                    "법규 대상 파일명 DB 초기 데이터를 만들지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    private void collectLegalTargets(
            Element parent,
            int level,
            String parentL1File,
            String parentL2File,
            List<DitamapLegalTarget> targets) {
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)
                    || !"topicref".equals(element.getTagName())){
                continue;
            }

            String href = element.getAttribute("href");
            String fileName = extractHrefFileName(href);
            String nextParentL1File = level == 1
                    ? fileName
                    : parentL1File;
            String nextParentL2File = level == 2
                    ? fileName
                    : parentL2File;

            /*
             * LM 법규 마스터에서 1~2레벨 legal_*.dita는 고정 구조이고,
             * 3레벨 이하의 실제 topic 파일을 DB 자동 체크 대상으로 저장한다.
             * 일부 법규 전용 topic은 t*.dita가 아니라 legal_scr*.dita처럼
             * legal_*.dita 이름을 쓰므로 함께 허용한다.
             */
            if(level >= 3 && isLegalTargetTopicFile(fileName)){
                targets.add(new DitamapLegalTarget(
                        fileName,
                        readLegalTemplateTitle(element, fileName),
                        parentL1File,
                        parentL2File,
                        level));
            }

            collectLegalTargets(
                    element,
                    level + 1,
                    nextParentL1File,
                    nextParentL2File,
                    targets);
        }
    }

    private String readLegalTemplateTitle(
            Element element,
            String fileName) {
        String title = firstTopicMetaText(element, "navtitle");

        if(title.isBlank()){
            title = firstTopicMetaText(element, "linktext");
        }

        if(title.isBlank()){
            title = firstDirectChildText(element, "title");
        }

        if(title.isBlank()){
            title = readLegalResourceTitle(fileName);
        }

        if(title.isBlank()){
            return fileName == null || fileName.isBlank()
                    ? "(제목 없음)"
                    : fileName.replaceFirst("(?i)\\.(dita|ditamap)$", "");
        }

        return title;
    }

    private String extractHrefFileName(String href) {
        if(href == null || href.isBlank()){
            return "";
        }

        return Path.of(href.replace('\\', '/').split("#", 2)[0])
                .getFileName()
                .toString();
    }

    private boolean isLegalTargetTopicFile(String fileName) {
        String normalized = fileName == null
                ? ""
                : fileName.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("t\\d+\\.dita")
                || normalized.matches("legal_(?!\\d{2}(?:_\\d{2})?\\.dita$)[\\w-]+\\.dita");
    }

    private DitamapTreeResponse readTree(
            String rawPath,
            boolean summaryOnly) {
        if(rawPath == null || rawPath.isBlank()){
            throw new IllegalArgumentException(
                    "DITA 경로를 입력해 주세요.");
        }

        try{
            Path ditamap = findDitamap(rawPath);
            Document document = parseXml(ditamap);
            Element root = document.getDocumentElement();
            List<DitamapTreeNode> nodes = readTopicChildren(
                    root,
                    ditamap.getParent(),
                    ditamap,
                    "",
                    1,
                    new HashSet<>(),
                    summaryOnly);

            return new DitamapTreeResponse(
                    readMapTitle(root, ditamap),
                    ditamap.toString(),
                    nodes);
        }catch(IOException | ParserConfigurationException
                | SAXException exception){
            throw new IllegalArgumentException(
                    createReadableXmlError(exception),
                    exception);
        }
    }

    public DitamapAttributeUpdateResponse updateAttributes(
            DitamapAttributeUpdateRequest request) {

        if(request == null){
            throw new IllegalArgumentException(
                    "DITAMAP 저장 요청 정보가 없습니다.");
        }

        int updatedCount = 0;

        try{
            List<DitamapAttributeUpdate> updates = request.updates() == null
                    ? List.of()
                    : request.updates();

            for(DitamapAttributeUpdate update : updates){
                updateAttribute(update);
                updatedCount++;
            }

            return new DitamapAttributeUpdateResponse(updatedCount);
        }catch(IOException | ParserConfigurationException
                | SAXException | TransformerException exception){
            throw new IllegalArgumentException(
                    "속성값을 저장하지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    public DitamapTreeResponse createLegalHash(
            DitamapAttributeUpdateRequest request) {
        if(request == null || request.ditamapFile() == null
                || request.ditamapFile().isBlank()){
            throw new IllegalArgumentException(
                    "legal hash를 생성할 DITAMAP 정보가 없습니다.");
        }

        try{
            Path legalDitamap =
                    ditamapLegalHashService.run(findDitamap(request.ditamapFile()));
            return readTree(legalDitamap.toString());
        }catch(IOException | InterruptedException
                | IllegalArgumentException exception){
            if(exception instanceof InterruptedException){
                Thread.currentThread().interrupt();
            }

            throw new IllegalArgumentException(
                    "legal hash 생성에 실패했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    public DitamapLegalSaveResponse saveLegalDitamap(
            DitamapLegalSaveRequest request) {
        if(request == null){
            throw new IllegalArgumentException(
                    "저장할 법규 DITAMAP 정보가 없습니다.");
        }

        List<DitamapLegalRow> rows = request.rows() == null
                ? List.of()
                : request.rows();

        try{
            Path baseDitamap = findDitamap(resolveBaseDitamapFile(request));
            Path outputDirectory = baseDitamap.getParent();

            if(outputDirectory == null){
                throw new IllegalArgumentException(
                        "법규 DITAMAP을 저장할 폴더를 찾지 못했습니다.");
            }

            Path outputDitamap = outputDirectory.resolve(
                    createLegalDitamapFileName(baseDitamap));
            Document document = createLegalDitamapDocument(
                    baseDitamap,
                    rows,
                    outputDirectory);
            writeXml(document, outputDitamap, true);

            return new DitamapLegalSaveResponse(
                    rows.size(),
                    outputDitamap.toString());
        }catch(IOException | ParserConfigurationException | SAXException
                | TransformerException exception){
            throw new IllegalArgumentException(
                    "법규 DITAMAP을 저장하지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    private String resolveBaseDitamapFile(DitamapLegalSaveRequest request) {
        if(request.baseDitamapFile() != null
                && !request.baseDitamapFile().isBlank()){
            return request.baseDitamapFile();
        }

        if(request.ditamapFile() != null
                && !request.ditamapFile().isBlank()
                && !"편집 중".equals(request.ditamapFile())){
            return request.ditamapFile();
        }

        throw new IllegalArgumentException(
                "기준 DITAMAP 경로가 없어 법규 DITAMAP 저장 위치를 정할 수 없습니다.");
    }

    private String createLegalDitamapFileName(Path baseDitamap) {
        String fileName = baseDitamap.getFileName().toString();

        if(fileName.toLowerCase().endsWith(".ditamap")){
            return "LM_" + fileName;
        }

        return "LM_" + fileName + ".ditamap";
    }

    private Document createLegalDitamapDocument(
            Path baseDitamap,
            List<DitamapLegalRow> rows,
            Path outputDirectory)
            throws ParserConfigurationException, IOException, SAXException {
        Document document = parseXml(baseDitamap);
        Element root = document.getDocumentElement();

        /*
         * 최종 LM DITAMAP은 기준 DITAMAP의 기본 선언/속성/메타데이터를 그대로 사용한다.
         * DOCTYPE, path2rootmap-uri PI, map 속성, title 뒤의 topicmeta metadata는 유지하고
         * 기존 topicref 구조만 법규 화면에서 편집한 구조로 교체한다.
         */
        updateLegalMapTitle(root, baseDitamap);
        removeTopicRefChildren(root);

        appendLegalExportRows(
                document,
                root,
                rows,
                outputDirectory);
        return document;
    }

    private void updateLegalMapTitle(Element root, Path baseDitamap) {
        String baseTitle = baseDitamap.getFileName()
                .toString()
                .replaceFirst("(?i)\\.ditamap$", "");
        NodeList children = root.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(child instanceof Element element
                    && "title".equals(element.getTagName())){
                String title = element.getTextContent() == null
                        ? ""
                        : element.getTextContent().trim();
                element.setTextContent("LM_" + (title.isBlank()
                        ? baseTitle
                        : title.replaceFirst("(?i)^LM_", "")));
                return;
            }
        }

        Element title = root.getOwnerDocument().createElement("title");
        title.setTextContent("LM_" + baseTitle);
        root.insertBefore(title, root.getFirstChild());
    }

    private void appendLegalExportRows(
            Document document,
            Element root,
            List<DitamapLegalRow> rows,
            Path outputDirectory)
            throws IOException {
        List<Element> parents = new ArrayList<>();
        parents.add(root);

        for(DitamapLegalRow row : rows){
            if(row == null){
                continue;
            }

            if(isExcludedLegalTitle(row.title())){
                continue;
            }

            Element element = createLegalExportElement(
                    document,
                    row,
                    outputDirectory);
            int level = Math.max(1, row.level());

            while(parents.size() > level){
                parents.remove(parents.size() - 1);
            }

            parents.get(parents.size() - 1).appendChild(element);
            parents.add(element);
        }
    }

    private Element createLegalExportElement(
            Document document,
            DitamapLegalRow row,
            Path outputDirectory)
            throws IOException {
        String href = normalizeLegalExportHref(row);
        Element element = href.isBlank()
                ? document.createElement("topichead")
                : document.createElement("topicref");

        if(!href.isBlank()){
            element.setAttribute("href", href);
            element.setAttribute("type", "concept");
            copyLegalResourceIfNeeded(row.fileName(), outputDirectory);
        }

        appendTopicMeta(
                document,
                element,
                row.title());
        return element;
    }

    private String normalizeLegalExportHref(DitamapLegalRow row) {
        if(row.href() != null && !row.href().isBlank()){
            return row.href().replace('\\', '/');
        }

        if(row.fileName() != null && !row.fileName().isBlank()){
            return row.fileName().replace('\\', '/');
        }

        return "";
    }

    private String normalizeEditedHref(String currentHref, String nextFileName) {
        /*
         * 화면에서는 파일명만 입력받지만 ditamap의 href에는 폴더 경로나 #fragment가
         * 붙어 있을 수 있다. 기존 href의 경로 구조는 유지하고 마지막 파일명만
         * 교체해야 저장 시 topicref 연결이 엉뚱한 위치로 바뀌지 않는다.
         */
        String fileName = nextFileName == null
                ? ""
                : nextFileName.trim().replace('\\', '/');

        if(fileName.isBlank()){
            return "";
        }

        String href = currentHref == null
                ? ""
                : currentHref.trim().replace('\\', '/');

        if(href.isBlank() || href.endsWith("/")){
            return fileName;
        }

        String[] hrefParts = href.split("#", 2);
        String pathOnly = hrefParts[0];
        String fragment = hrefParts.length > 1
                ? "#" + hrefParts[1]
                : "";
        int slashIndex = pathOnly.lastIndexOf('/');

        if(slashIndex < 0){
            return fileName + fragment;
        }

        return pathOnly.substring(0, slashIndex + 1) + fileName + fragment;
    }

    private void appendTopicMeta(
            Document document,
            Element element,
            String title) {
        String safeTitle = title == null || title.isBlank()
                ? "(제목 없음)"
                : title;
        Element topicMeta = document.createElement("topicmeta");
        Element navTitle = document.createElement("navtitle");
        Element linkText = document.createElement("linktext");

        navTitle.setTextContent(safeTitle);
        linkText.setTextContent(safeTitle);
        topicMeta.appendChild(navTitle);
        topicMeta.appendChild(linkText);
        element.appendChild(topicMeta);
    }

    private void copyLegalResourceIfNeeded(
            String fileName,
            Path outputDirectory)
            throws IOException {
        if(fileName == null
                || !fileName.toLowerCase().matches("legal_[\\w-]+\\.dita")){
            return;
        }

        Resource resource = new ClassPathResource(
                "revision-tool/legal/" + fileName);

        if(!resource.exists()){
            return;
        }

        try(InputStream input = resource.getInputStream()){
            Files.copy(
                    input,
                    outputDirectory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 오른쪽 법규 DITAMAP 영역의 폴더열기 버튼에서 사용자가 DITA 경로 폴더를
     * 바로 확인할 수 있도록 탐색기를 연다.
     * 브라우저는 보안상 로컬/네트워크 드라이브를 직접 열 수 없어서 서버에서 Windows 탐색기를 실행한다.
     * 입력이 .ditamap 파일이면 부모 폴더를 열고, 폴더 경로이면 해당 폴더를 연다.
     * 저장 버튼과 분리된 기능이므로, 이 메서드는 파일 저장 작업을 수행하지 않는다.
     */
    public void openFolder(String rawPath) {
        if(rawPath == null || rawPath.isBlank()){
            throw new IllegalArgumentException(
                    "열 폴더 경로가 없습니다.");
        }

        try{
            Path path = Path.of(rawPath.trim())
                    .toAbsolutePath()
                    .normalize();

            if(!Files.exists(path)){
                throw new IllegalArgumentException(
                        "경로가 존재하지 않습니다: " + path);
            }

            Path realPath = path.toRealPath();
            // 화면에서 전달된 경로라도 허용 루트 밖이면 탐색기를 열지 않는다.
            findAllowedRoot(realPath);
            Path folder = Files.isRegularFile(realPath)
                    ? realPath.getParent()
                    : realPath;

            if(folder == null || !Files.isDirectory(folder)){
                throw new IllegalArgumentException(
                        "열 수 있는 폴더가 아닙니다: " + realPath);
            }

            /*
             * cmd start는 기존 Explorer 창을 재사용하면서 작업표시줄만 깜빡일 때가 있다.
             * /n,/e 옵션으로 새 탐색기 창을 직접 열어 사용자가 폴더를 바로 찾기 쉽게 한다.
             * 단, Windows의 포커스 탈취 방지 정책 때문에 항상 최상위 포커스를 보장할 수는 없다.
             */
            new ProcessBuilder(
                    "explorer.exe",
                    "/n,/e," + folder)
                    .start();
        }catch(IOException exception){
            throw new IllegalArgumentException(
                    "폴더를 열지 못했습니다. " + exception.getMessage(),
                    exception);
        }
    }

    private Path findDitamap(String rawPath) throws IOException {
        Path path = Path.of(rawPath.trim())
                .toAbsolutePath()
                .normalize();

        if(!Files.exists(path)){
            throw new IllegalArgumentException(
                    "경로가 존재하지 않습니다: " + path);
        }

        Path realPath = path.toRealPath();
        Path realAllowedRoot = findAllowedRoot(realPath);

        if(!realPath.startsWith(realAllowedRoot)){
            throw new IllegalArgumentException(
                    "허용된 DITA 작업 경로는 "
                    + realAllowedRoot
                    + " 아래입니다.");
        }

        if(Files.isRegularFile(realPath)){
            if(isDitamap(realPath)){
                return realPath;
            }

            throw new IllegalArgumentException(
                    "DITAMAP 파일이 아닙니다: "
                    + realPath.getFileName());
        }

        try(var stream = Files.list(realPath)){
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isDitamap)
                    .sorted(Comparator.comparing(
                            candidate -> candidate.getFileName()
                                    .toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "해당 폴더에서 .ditamap 파일을 찾지 못했습니다."));
        }
    }

    private List<Path> createAllowedRoots(String configuredRoots) {
        List<Path> roots = new ArrayList<>();

        if(configuredRoots != null && !configuredRoots.isBlank()){
            for(String rawRoot : configuredRoots.split(";")){
                String root = rawRoot.trim();

                if(root.isBlank()){
                    continue;
                }

                roots.add(Path.of(expandPathTokens(root))
                        .toAbsolutePath()
                        .normalize());
            }
        }

        return roots.stream()
                .distinct()
                .toList();
    }

    private String expandPathTokens(String path) {
        return path.replace(
                "${user.home}",
                System.getProperty("user.home"));
    }

    private Path findAllowedRoot(Path path) throws IOException {
        Path realPath = path.toRealPath();

        if(allowedRoots.isEmpty()){
            throw new IllegalArgumentException(describeAllowedRoots());
        }

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path realAllowedRoot = allowedRoot.toRealPath();

            if(isSameOrChildPath(realPath, realAllowedRoot)){
                return realAllowedRoot;
            }
        }

        throw new IllegalArgumentException(
                "허용된 DITA 작업 경로는 "
                + describeAllowedRoots()
                + " 아래입니다.");
    }

    private boolean isUnderAllowedRoot(Path path) throws IOException {
        try{
            findAllowedRoot(path);
            return true;
        }catch(IllegalArgumentException exception){
            return false;
        }
    }

    private Path resolveAllowedRelativePath(String relativePath)
            throws IOException {
        if(relativePath == null || relativePath.isBlank()){
            throw new IllegalArgumentException(
                    "수정할 파일 정보가 없습니다.");
        }

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path target = allowedRoot.resolve(relativePath)
                    .normalize();

            if(Files.exists(target)
                    && isSameOrChildPath(
                            target.toRealPath(),
                            allowedRoot.toRealPath())){
                return target;
            }
        }

        throw new IllegalArgumentException(
                "허용된 작업 경로 안에서 수정할 파일을 찾지 못했습니다: "
                + relativePath);
    }

    private String describeAllowedRoots() {
        if(allowedRoots.isEmpty()){
            return "설정된 작업 루트가 없습니다. ditamap.builder.allowed-roots를 설정해 주세요.";
        }

        return allowedRoots.stream()
                .map(Path::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("(없음)");
    }

    private String toAllowedRelativePath(Path target)
            throws IOException {
        Path realTarget = target.toRealPath();

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path realAllowedRoot = allowedRoot.toRealPath();

            if(!isSameOrChildPath(realTarget, realAllowedRoot)){
                continue;
            }

            if(realTarget.startsWith(realAllowedRoot)){
                return realAllowedRoot.relativize(realTarget)
                        .toString();
            }

            String targetText = toComparablePathText(realTarget);
            String rootText = toComparablePathText(realAllowedRoot);

            if(targetText.equals(rootText)){
                return "";
            }

            return targetText.substring(rootText.length() + 1)
                    .replace('/', '\\');
        }

        throw new IllegalArgumentException(
                "허용된 작업 경로 안에서 상대 경로를 만들 수 없습니다: "
                + target);
    }

    private boolean isSameOrChildPath(
            Path path,
            Path root)
            throws IOException {
        String pathText = toComparablePathText(path);
        String rootText = toComparablePathText(root);

        return pathText.equals(rootText)
                || pathText.startsWith(rootText + "\\");
    }

    private String toComparablePathText(Path path)
            throws IOException {
        String text = path.toRealPath()
                .toString();
        return normalizeComparablePath(convertMappedDrivePathToUnc(text));
    }

    private String normalizeComparablePath(String path) {
        String normalized = path.replace('/', '\\');

        while(normalized.endsWith("\\") && normalized.length() > 3){
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private String convertMappedDrivePathToUnc(String path) {
        if(path.length() < 3
                || path.charAt(1) != ':'
                || path.charAt(2) != '\\'){
            return path;
        }

        String drive = path.substring(0, 2)
                .toUpperCase(Locale.ROOT);
        String remote = mappedDriveCache.computeIfAbsent(
                drive,
                this::readMappedDriveRemote);

        if(remote.isBlank()){
            return path;
        }

        return remote + path.substring(2);
    }

    private String readMappedDriveRemote(String drive) {
        try{
            Process process = new ProcessBuilder(
                    "cmd",
                    "/c",
                    "net",
                    "use",
                    drive)
                    .redirectErrorStream(true)
                    .start();

            if(!process.waitFor(3, TimeUnit.SECONDS)){
                process.destroyForcibly();
                return "";
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int uncStart = output.indexOf("\\\\");

            if(uncStart < 0){
                return "";
            }

            int uncEnd = uncStart;

            while(uncEnd < output.length()
                    && !Character.isWhitespace(output.charAt(uncEnd))){
                uncEnd++;
            }

            return output.substring(uncStart, uncEnd)
                    .replace('/', '\\');
        }catch(IOException | InterruptedException exception){
            if(exception instanceof InterruptedException){
                Thread.currentThread().interrupt();
            }

            return "";
        }
    }

    private boolean isDitamap(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".ditamap");
    }

    private Document parseXml(Path path)
            throws ParserConfigurationException, IOException, SAXException {
        try(InputStream input = Files.newInputStream(path)){
            return parseXml(input);
        }
    }

    private Document parseXml(InputStream input)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        setXmlFeatureIfSupported(
                factory,
                "http://apache.org/xml/features/disallow-doctype-decl",
                false);
        setXmlFeatureIfSupported(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false);
        setXmlFeatureIfSupported(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        setXmlFeatureIfSupported(
                factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        setXmlAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_DTD,
                "");
        setXmlAttributeIfSupported(
                factory,
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(input);
    }

    private void setXmlFeatureIfSupported(
            DocumentBuilderFactory factory,
            String feature,
            boolean value)
            throws ParserConfigurationException {
        try{
            factory.setFeature(feature, value);
        }catch(ParserConfigurationException exception){
            String message = exception.getMessage() == null
                    ? ""
                    : exception.getMessage();

            if(message.contains("not recognized")
                    || message.contains("not supported")){
                return;
            }

            throw exception;
        }
    }

    private void setXmlAttributeIfSupported(
            DocumentBuilderFactory factory,
            String attribute,
            String value) {
        try{
            factory.setAttribute(attribute, value);
        }catch(IllegalArgumentException exception){
            // 일부 XML parser 구현체는 ACCESS_EXTERNAL_* 속성을 지원하지 않는다.
        }
    }

    private String readMapTitle(Element root, Path ditamap) {
        String title = firstDirectChildText(root, "title");

        if(!title.isBlank()){
            return title;
        }

        return ditamap.getFileName()
                .toString()
                .replaceFirst("(?i)\\.ditamap$", "");
    }

    private List<DitamapTreeNode> readTopicChildren(
            Element parent,
            Path baseDirectory,
            Path sourceFile,
            String parentElementPath,
            int level,
            Set<Path> visitedMaps)
            throws IOException, ParserConfigurationException, SAXException {
        return readTopicChildren(
                parent,
                baseDirectory,
                sourceFile,
                parentElementPath,
                level,
                visitedMaps,
                false);
    }

    private List<DitamapTreeNode> readTopicChildren(
            Element parent,
            Path baseDirectory,
            Path sourceFile,
            String parentElementPath,
            int level,
            Set<Path> visitedMaps,
            boolean summaryOnly)
            throws IOException, ParserConfigurationException, SAXException {
        List<DitamapTreeNode> nodes = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)){
                continue;
            }

            /*
             * topicmeta는 navtitle/linktext/data 같은 메타 정보 컨테이너다.
             * 내부의 data name="image" href="..."를 topicref처럼 읽으면
             * 화면에 "images" 같은 가짜 L2 항목이 표시되므로 트리 탐색에서 제외한다.
             */
            if(isTopicMetaElement(element)){
                continue;
            }

            if(isTopicRefLike(element)){
                nodes.add(readTopicNode(
                        element,
                        baseDirectory,
                        sourceFile,
                        appendElementPath(parentElementPath, index),
                        level,
                        visitedMaps,
                        summaryOnly));
            }else{
                nodes.addAll(readTopicChildren(
                        element,
                        baseDirectory,
                        sourceFile,
                        appendElementPath(parentElementPath, index),
                        level,
                        visitedMaps,
                        summaryOnly));
            }
        }

        return nodes;
    }

    private List<DitamapTreeNode> readLegalTemplateChildren(
            Element parent,
            int level) {
        List<DitamapTreeNode> nodes = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)
                    || !"topicref".equals(element.getTagName())){
                continue;
            }

            String href = element.getAttribute("href");
            String fileName = href == null || href.isBlank()
                    ? ""
                    : Path.of(href.replace('\\', '/'))
                            .getFileName()
                            .toString();
            String title = firstTopicMetaText(element, "navtitle");

            if(title.isBlank()){
                title = firstTopicMetaText(element, "linktext");
            }

            if(title.isBlank()){
                title = firstDirectChildText(element, "title");
            }

            if(title.isBlank()){
                title = readLegalResourceTitle(fileName);
            }

            if(title.isBlank()){
                title = fileName.isBlank()
                        ? "(제목 없음)"
                        : fileName.replaceFirst("(?i)\\.(dita|ditamap)$", "");
            }

            if(isExcludedLegalTitle(title)){
                continue;
            }

            nodes.add(new DitamapTreeNode(
                    title,
                    level,
                    "",
                    "veh-legalid",
                    fileName,
                    "",
                    "",
                    "",
                    href,
                    readLegalTemplateChildren(element, level + 1)));
        }

        return nodes;
    }

    private List<DitamapTreeNode> readLegalMasterChildren(
            Element parent,
            int level) {
        List<DitamapTreeNode> nodes = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)
                    || !"topicref".equals(element.getTagName())){
                continue;
            }

            String href = element.getAttribute("href");
            String fileName = extractHrefFileName(href);
            String title = readLegalTemplateTitle(element, fileName);

            if(isExcludedLegalTitle(title)){
                continue;
            }

            nodes.add(new DitamapTreeNode(
                    title,
                    level,
                    "",
                    "veh-legalid",
                    fileName,
                    "",
                    "",
                    "",
                    href,
                    readLegalMasterChildren(element, level + 1)));
        }

        return nodes;
    }

    private String readLegalResourceTitle(String fileName) {
        if(fileName == null || fileName.isBlank()){
            return "";
        }

        Resource resource = new ClassPathResource(
                "revision-tool/legal/" + fileName.replace('\\', '/'));

        if(!resource.exists()){
            return "";
        }

        try(InputStream input = resource.getInputStream()){
            Document document = parseXml(input);
            return firstDirectChildText(
                    document.getDocumentElement(),
                    "title");
        }catch(IOException | ParserConfigurationException | SAXException exception){
            return "";
        }
    }

    private boolean isExcludedLegalTitle(String title) {
        if(title == null){
            return false;
        }

        String normalizedTitle = title.trim()
                .replaceAll("\\s+", " ");
        return normalizedTitle.equalsIgnoreCase(MANUAL_CHECKED_GROUP_TITLE);
    }

    private DitamapTreeNode readTopicNode(
            Element element,
            Path baseDirectory,
            Path sourceFile,
            String elementPath,
            int level,
            Set<Path> visitedMaps,
            boolean summaryOnly)
            throws IOException, ParserConfigurationException, SAXException {
        String href = element.getAttribute("href");
        Path target = resolveHref(baseDirectory, href);
        TopicInfo topicInfo = readTopicInfo(
                element,
                target,
                sourceFile,
                elementPath,
                summaryOnly);
        List<DitamapTreeNode> children = readTopicChildren(
                element,
                baseDirectory,
                sourceFile,
                elementPath,
                level + 1,
                visitedMaps,
                summaryOnly);

        if(children.isEmpty()
                && target != null
                && isDitamap(target)
                && Files.isRegularFile(target)){
            Path realTarget = target.toRealPath();

            if(isUnderAllowedRoot(realTarget)
                    && visitedMaps.add(realTarget)){
                Document referencedMap = parseXml(target);
                children = readTopicChildren(
                        referencedMap.getDocumentElement(),
                        target.getParent(),
                        target,
                        "",
                        level + 1,
                        visitedMaps,
                        summaryOnly);
            }
        }

        return new DitamapTreeNode(
                topicInfo.title(),
                level,
                topicInfo.attributeValue(),
                topicInfo.attributeName(),
                topicInfo.fileName(),
                topicInfo.filePath(),
                topicInfo.sourceFilePath(),
                topicInfo.elementPath(),
                href,
                children);
    }

    private boolean isTopicRefLike(Element element) {
        String tagName = element.getTagName();
        String className = element.getAttribute("class");

        /*
         * DITA map의 data/image도 href를 가질 수 있지만, topicmeta 내부 href는
         * 실제 목차 항목이 아니라 WebHelp 타일 이미지 같은 메타 리소스다.
         * href만 보고 topicref로 분류하면 메타 리소스가 트리에 섞이므로 제외한다.
         */
        if(isInsideTopicMeta(element)){
            return false;
        }

        return tagName.equals("topicref")
                || tagName.equals("chapter")
                || tagName.equals("appendix")
                || tagName.equals("topichead")
                || tagName.equals("mapref")
                || className.contains("map/topicref")
                || element.hasAttribute("href");
    }

    private boolean isTopicMetaElement(Element element) {
        return "topicmeta".equals(element.getTagName());
    }

    private boolean isInsideTopicMeta(Element element) {
        Node parent = element.getParentNode();

        while(parent instanceof Element parentElement){
            if(isTopicMetaElement(parentElement)){
                return true;
            }

            parent = parent.getParentNode();
        }

        return false;
    }

    private Path resolveHref(Path baseDirectory, String href)
            throws IOException {

        if(baseDirectory == null
                || href == null
                || href.isBlank()
                || href.startsWith("http:")
                || href.startsWith("https:")){
            return null;
        }

        String pathOnly = href.split("#", 2)[0];

        if(pathOnly.isBlank()){
            return null;
        }

        String decoded = URLDecoder.decode(
                pathOnly,
                StandardCharsets.UTF_8);
        Path target = baseDirectory.resolve(
                decoded.replace('/', java.io.File.separatorChar))
                .normalize();

        if(Files.exists(target)
                && !isUnderAllowedRoot(target.toRealPath())){
            return null;
        }

        return target;
    }

    private TopicInfo readTopicInfo(
            Element topicRef,
            Path target,
            Path sourceFile,
            String elementPath)
            throws IOException, ParserConfigurationException, SAXException {
        return readTopicInfo(
                topicRef,
                target,
                sourceFile,
                elementPath,
                false);
    }

    private TopicInfo readTopicInfo(
            Element topicRef,
            Path target,
            Path sourceFile,
            String elementPath,
            boolean summaryOnly)
            throws IOException, ParserConfigurationException, SAXException {
        String title = topicRef.getAttribute("navtitle");
        String attributeValue = "";
        String attributeName = "";
        String fileName = target == null
                ? ""
                : target.getFileName().toString();
        String filePath = "";
        String sourceFilePath = "";
        String editableElementPath = elementPath;

        if(sourceFile != null && Files.exists(sourceFile)){
            sourceFilePath = toAllowedRelativePath(sourceFile);
        }

        if(title.isBlank()){
            title = firstDirectChildText(topicRef, "title");
        }

        if(title.isBlank()){
            title = firstTopicMetaText(topicRef, "navtitle");
        }

        if(title.isBlank()){
            title = firstTopicMetaText(topicRef, "linktext");
        }

        if(target != null
                && Files.isRegularFile(target)
                && !isDitamap(target)){
            if(title.isBlank() || !summaryOnly){
                Document topicDocument = parseXml(target);
                Element topicRoot = topicDocument.getDocumentElement();
                title = title.isBlank()
                        ? firstDirectChildText(topicRoot, "title")
                        : title;

                if(!summaryOnly){
                    AttributeInfo attributeInfo = findAttributeValue(topicRoot);
                    attributeValue = attributeInfo.value();
                    attributeName = attributeInfo.name();
                }
            }

            if(!summaryOnly && isEditableTopicFile(target)){
                filePath = toAllowedRelativePath(target);
            }
        }else if(!summaryOnly && isEditableDitamapElement(topicRef)){
            AttributeInfo attributeInfo = findAttributeValue(topicRef);
            attributeValue = attributeInfo.value();
            attributeName = attributeInfo.name();
        }

        if(title.isBlank()){
            title = fileName.isBlank()
                    ? "(제목 없음)"
                    : fileName.replaceFirst("(?i)\\.(dita|ditamap)$", "");
        }

        return new TopicInfo(
                title,
                attributeValue,
                attributeName,
                fileName,
                filePath,
                sourceFilePath,
                editableElementPath);
    }

    private AttributeInfo findAttributeValue(Element element) {

        for(String name : ATTRIBUTE_CANDIDATES){
            if(element.hasAttribute(name)){
                return new AttributeInfo(
                        name,
                        element.getAttribute(name));
            }
        }

        return new AttributeInfo("veh-legalid", "");
    }

    private void updateAttribute(
            DitamapAttributeUpdate update)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerException {

        if(update == null
                || ((update.filePath() == null
                        || update.filePath().isBlank())
                    && (update.sourceFilePath() == null
                        || update.sourceFilePath().isBlank()
                        || update.elementPath() == null
                        || update.elementPath().isBlank()))){
            throw new IllegalArgumentException(
                    "수정할 파일 정보가 없습니다.");
        }

        if(update.filePath() == null || update.filePath().isBlank()){
            updateDitamapElementAttribute(update);
            return;
        }

        Path target = resolveAllowedRelativePath(update.filePath());
        Path realAllowedRoot = findAllowedRoot(target.toRealPath());

        if(!Files.exists(target)
                || !target.toRealPath().startsWith(realAllowedRoot)
                || !Files.isRegularFile(target)
                || !isEditableTopicFile(target)){
            throw new IllegalArgumentException(
                    "허용된 DITA/SVG 파일만 수정할 수 있습니다.");
        }

        Document document = parseXml(target);
        Element root = document.getDocumentElement();
        String attributeName = normalizeAttributeName(
                update.attributeName(),
                root);
        String attributeValue = update.attributeValue() == null
                ? ""
                : update.attributeValue().trim();

        if(attributeValue.isBlank()){
            root.removeAttribute(attributeName);
        }else{
            root.setAttribute(attributeName, attributeValue);
        }

        writeXml(document, target);
    }

    private void updateDitamapElementAttribute(
            DitamapAttributeUpdate update)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerException {
        Path target = resolveAllowedRelativePath(update.sourceFilePath());
        Path realAllowedRoot = findAllowedRoot(target.toRealPath());

        if(!Files.exists(target)
                || !target.toRealPath().startsWith(realAllowedRoot)
                || !Files.isRegularFile(target)
                || !isDitamap(target)){
            throw new IllegalArgumentException(
                    "허용된 DITAMAP 파일만 수정할 수 있습니다.");
        }

        Document document = parseXml(target);
        Element element = findElementByPath(
                document.getDocumentElement(),
                update.elementPath());
        String attributeName = normalizeAttributeName(
                update.attributeName(),
                element);
        String attributeValue = update.attributeValue() == null
                ? ""
                : update.attributeValue().trim();

        if(attributeValue.isBlank()){
            element.removeAttribute(attributeName);
        }else{
            element.setAttribute(attributeName, attributeValue);
        }

        writeXml(document, target);
    }

    private DitamapAttributeUpdate createLegalAttributeUpdate(
            DitamapLegalRow row) {
        return new DitamapAttributeUpdate(
                row.filePath(),
                row.sourceFilePath(),
                row.elementPath(),
                row.attributeName(),
                row.attributeValue());
    }

    private void applyLegalRowAttribute(
            DitamapLegalRow row,
            Element element) {
        /*
         * filePath가 있는 row는 속성값이 topic 파일 루트에 저장되는 항목이다.
         * 이 경우 DITAMAP clone에 속성을 쓰면 실제 저장 대상과 화면 표시가 어긋나므로,
         * updateAttribute에서 topic 파일에만 반영하게 둔다.
         */
        if(row.filePath() != null && !row.filePath().isBlank()){
            return;
        }

        String attributeName = normalizeAttributeName(
                row.attributeName(),
                element);
        String attributeValue = row.attributeValue() == null
                ? ""
                : row.attributeValue().trim();

        if(attributeValue.isBlank()){
            element.removeAttribute(attributeName);
        }else{
            element.setAttribute(attributeName, attributeValue);
        }
    }

    private void removeTopicRefChildren(Element parent) {
        NodeList children = parent.getChildNodes();

        for(int index = children.getLength() - 1; index >= 0; index--){
            Node child = children.item(index);

            if(!(child instanceof Element element)){
                continue;
            }

            if(isTopicRefLike(element)){
                parent.removeChild(element);
            }else{
                removeTopicRefChildren(element);
            }
        }
    }

    private void removeWhitespaceOnlyTextNodes(Node parent) {
        NodeList children = parent.getChildNodes();

        for(int index = children.getLength() - 1; index >= 0; index--){
            Node child = children.item(index);

            if(child.getNodeType() == Node.TEXT_NODE
                    && child.getTextContent() != null
                    && child.getTextContent().trim().isEmpty()){
                parent.removeChild(child);
                continue;
            }

            if(child.hasChildNodes()){
                removeWhitespaceOnlyTextNodes(child);
            }
        }
    }

    private void appendLegalRows(
            Element root,
            List<Element> elements,
            List<DitamapLegalRow> rows) {
        /*
         * rows는 화면에 보이는 flat 목록이고 level 값만 부모/자식 관계를 표현한다.
         * parents[level-1] 위치를 현재 부모로 사용해서 L2/L3/L4 구조를 다시 만든다.
         */
        List<Element> parents = new ArrayList<>();
        parents.add(root);

        for(int index = 0; index < elements.size(); index++){
            DitamapLegalRow row = rows.get(index);
            Element element = elements.get(index);
            int level = Math.max(1, row.level());

            while(parents.size() > level){
                parents.remove(parents.size() - 1);
            }

            Element parent = parents.get(parents.size() - 1);
            parent.appendChild(element);
            parents.add(element);
        }
    }

    private Element findElementByPath(Element root, String elementPath) {
        Node current = root;

        for(String part : elementPath.split("/")){
            if(part.isBlank()){
                continue;
            }

            int index;

            try{
                index = Integer.parseInt(part);
            }catch(NumberFormatException exception){
                throw new IllegalArgumentException(
                        "수정할 DITAMAP 요소 경로가 올바르지 않습니다.");
            }

            NodeList children = current.getChildNodes();

            if(index < 0 || index >= children.getLength()
                    || !(children.item(index) instanceof Element)){
                throw new IllegalArgumentException(
                        "수정할 DITAMAP 요소를 찾지 못했습니다.");
            }

            current = children.item(index);
        }

        if(!(current instanceof Element element)){
            throw new IllegalArgumentException(
                    "수정할 DITAMAP 요소를 찾지 못했습니다.");
        }

        return element;
    }

    private String normalizeAttributeName(
            String requestedName,
            Element root) {

        if("veh-legalid".equals(requestedName)){
            return requestedName;
        }

        for(String name : ATTRIBUTE_CANDIDATES){
            if(root.hasAttribute(name)){
                return name;
            }
        }

        return "veh-legalid";
    }

    private boolean isEditableTopicFile(Path target) {
        String fileName = target.getFileName()
                .toString()
                .toLowerCase();

        return fileName.endsWith(".dita")
                || fileName.endsWith(".svg");
    }

    private boolean isEditableDitamapElement(Element element) {
        return element.hasAttribute("href")
                && !element.getTagName().equals("topicref")
                && !element.getTagName().equals("chapter")
                && !element.getTagName().equals("appendix")
                && !element.getTagName().equals("mapref");
    }

    private String appendElementPath(String parentElementPath, int childIndex) {
        if(parentElementPath == null || parentElementPath.isBlank()){
            return Integer.toString(childIndex);
        }

        return parentElementPath + "/" + childIndex;
    }

    private void writeXml(
            Document document,
            Path target)
            throws TransformerException, IOException {
        writeXml(document, target, false);
    }

    private void writeXml(
            Document document,
            Path target,
            boolean prettyPrint)
            throws TransformerException, IOException {
        TransformerFactory factory =
                TransformerFactory.newInstance();
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                "");
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                "");

        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(
                OutputKeys.INDENT,
                prettyPrint ? "yes" : "no");

        if(prettyPrint){
            removeWhitespaceOnlyTextNodes(document);
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount",
                    "3");
        }

        DocumentType documentType = document.getDoctype();

        if(documentType != null){
            if(documentType.getPublicId() != null){
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_PUBLIC,
                        documentType.getPublicId());
            }

            if(documentType.getSystemId() != null){
                transformer.setOutputProperty(
                        OutputKeys.DOCTYPE_SYSTEM,
                        documentType.getSystemId());
            }
        }

        try(OutputStream output = Files.newOutputStream(target)){
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(output));
        }
    }

    private String createReadableXmlError(Exception exception) {
        String message = exception.getMessage() == null
                ? ""
                : exception.getMessage();

        if(message.contains("DOCTYPE is disallowed")){
            return "DITAMAP에 DOCTYPE 선언이 있어 읽기 설정을 확인해야 합니다.";
        }

        if(message.contains("External Entity")
                || message.contains("accessExternalDTD")
                || message.contains("External DTD")){
            return "DITAMAP의 외부 DTD는 읽지 않고 무시합니다. 파일 내부 XML 구조를 확인해 주세요.";
        }

        return "DITAMAP을 읽지 못했습니다. 파일 경로와 XML 형식을 확인해 주세요. "
                + message;
    }

    private String firstDirectChildText(
            Element parent,
            String tagName) {
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(child instanceof Element element
                    && element.getTagName().equals(tagName)){
                return element.getTextContent()
                        .trim()
                        .replaceAll("\\s+", " ");
            }
        }

        return "";
    }

    private String firstTopicMetaText(
            Element topicRef,
            String tagName) {
        NodeList children = topicRef.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)
                    || !isTopicMetaElement(element)){
                continue;
            }

            String value = firstDirectChildText(element, tagName);

            if(!value.isBlank()){
                return value;
            }
        }

        return "";
    }

    private record TopicInfo(
            String title,
            String attributeValue,
            String attributeName,
            String fileName,
            String filePath,
            String sourceFilePath,
            String elementPath) {
    }

    private record AttributeInfo(
            String name,
            String value) {
    }
}
