package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** DITAMAP Builder의 법규 DITAMAP 생성 배치 흐름을 실행한다. */
@Service
public class DitamapLegalHashService {

    private static final String RESOURCE_ROOT = "revision-tool";
    private static final String LEGAL_DITAMAP_NAME = "LM-ditamap.ditamap";
    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] TOOL_FILES = {
            "24_make-legal-hash.bat",
            "25-linear-olm-merge.bat",
            "26_make-legal-ditamap.bat",
            "17_merge.bat",
            "lib/saxon-ee-10.0.jar",
            "lib/saxon-license.lic",
            "lib/xml-resolver-1.2.jar",
            "xsl/65-make-legal-hash.xsl",
            "xsl/66-make-legal-ditamap.xsl",
            "xsl/LM-template.xml",
            "xsl/catalog.xml",
            "xsl/map.dtd",
            "xsl/concept.dtd",
            "xsl/task.dtd",
            "xsl/reference.dtd",
            "xsl/bookmap.dtd",
            "xsl/09-doctype-remove.xsl",
            "xsl/10-namespace-remove.xsl",
            "xsl/11-toc-create.xsl",
            "xsl/13-topic-merge2.xsl",
            "xsl/57-merge-beautify.xsl",
            "xsl/indentation1.xsl",
            "xsl/dummy.xml"
    };

    private final Path configuredToolDirectory;
    private final boolean enabled;

    public DitamapLegalHashService(
            @Value("${ditamap.builder.legal-hash.enabled:true}") boolean enabled,
            @Value("${ditamap.builder.legal-tool-dir:}") String toolDirectory) {
        this.enabled = enabled;
        this.configuredToolDirectory = toolDirectory == null
                || toolDirectory.isBlank()
                        ? null
                        : Path.of(toolDirectory)
                                .toAbsolutePath()
                                .normalize();
    }

    public Path run(Path ditamapFile) throws IOException, InterruptedException {
        if(!enabled){
            return ditamapFile;
        }

        if(ditamapFile == null || !Files.isRegularFile(ditamapFile)){
            throw new IllegalArgumentException(
                    "legal DITAMAP을 생성할 기준 DITAMAP 파일을 찾지 못했습니다.");
        }

        Path topicsDirectory = ditamapFile.toAbsolutePath()
                .normalize()
                .getParent();

        if(topicsDirectory == null || !Files.isDirectory(topicsDirectory)){
            throw new IllegalArgumentException(
                    "법규 DITAMAP을 넣을 topics 폴더를 찾지 못했습니다.");
        }

        Path toolDirectory = prepareToolDirectory();

        try{
            Path toolTopicsDirectory = toolDirectory.resolve("topics");
            Path toolTempDirectory = toolDirectory.resolve("temp");
            Path template = toolDirectory.resolve("xsl")
                    .resolve("LM-template.xml");

            if(!Files.isRegularFile(template)){
                throw new IllegalArgumentException(
                        "법규 DITAMAP 템플릿을 찾지 못했습니다: " + template);
            }

            deleteDirectory(toolTopicsDirectory);
            deleteDirectory(toolTempDirectory);
            copyDirectory(topicsDirectory, toolTopicsDirectory);
            Files.createDirectories(toolTempDirectory);
            deleteDitamaps(toolTopicsDirectory);
            Files.copy(
                    template,
                    toolTopicsDirectory.resolve("LM-template.xml"),
                    StandardCopyOption.REPLACE_EXISTING);
            syncLegalTemplateWithCurrentLegalIds(
                    toolTopicsDirectory,
                    toolTopicsDirectory.resolve("LM-template.xml"));

            runBatch(toolDirectory, "25-linear-olm-merge.bat");
            runBatch(toolDirectory, "26_make-legal-ditamap.bat");

            Path generatedLegalMap = toolTempDirectory.resolve(LEGAL_DITAMAP_NAME);

            if(!Files.isRegularFile(generatedLegalMap)){
                throw new IllegalArgumentException(
                        "생성된 법규 DITAMAP을 찾지 못했습니다: " + generatedLegalMap);
            }

            Files.copy(
                    generatedLegalMap,
                    toolTopicsDirectory.resolve(LEGAL_DITAMAP_NAME),
                    StandardCopyOption.REPLACE_EXISTING);
            copyLegalTopicsTo(toolTopicsDirectory);

            Path targetLegalMap = topicsDirectory.resolve(LEGAL_DITAMAP_NAME);
            Files.copy(
                    generatedLegalMap,
                    targetLegalMap,
                    StandardCopyOption.REPLACE_EXISTING);
            copyLegalTopicsTo(topicsDirectory);

            return targetLegalMap;
        }finally{
            if(configuredToolDirectory == null){
                deleteDirectory(toolDirectory);
            }
        }
    }

    private void runBatch(Path toolDirectory, String batchName)
            throws IOException, InterruptedException {
        Path batchFile = toolDirectory.resolve(batchName);

        if(!Files.isRegularFile(batchFile)){
            throw new IllegalArgumentException(
                    "법규 DITAMAP 배치 파일을 찾지 못했습니다: " + batchFile);
        }

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "echo. | call \"" + batchFile + "\"")
                .directory(toolDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        List<String> logs;

        try(BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        Charset.defaultCharset()))){
            logs = reader.lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }

        boolean finished = process.waitFor(
                Duration.ofMinutes(10).toMillis(),
                TimeUnit.MILLISECONDS);

        if(!finished){
            process.destroyForcibly();
            throw new IllegalArgumentException(
                    batchName + " 실행 시간이 초과되었습니다.");
        }

        if(process.exitValue() != 0){
            throw new IllegalArgumentException(
                    batchName + " 실행에 실패했습니다. "
                    + String.join(" / ", logs));
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);

        try(Stream<Path> stream = Files.walk(source)){
            for(Path sourcePath : stream.toList()){
                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath)
                        .normalize();

                if(!targetPath.startsWith(target)){
                    throw new IllegalArgumentException(
                            "복사 대상 경로가 올바르지 않습니다: " + sourcePath);
                }

                if(Files.isDirectory(sourcePath)){
                    Files.createDirectories(targetPath);
                }else if(Files.isRegularFile(sourcePath)){
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(
                            sourcePath,
                            targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void syncLegalTemplateWithCurrentLegalIds(
            Path topicsDirectory,
            Path templateFile) throws IOException {
        try{
            Map<String, String> legalIdsByTitle =
                    collectUniqueLegalIdsByTitle(topicsDirectory);

            if(legalIdsByTitle.isEmpty()){
                return;
            }

            Document document = parseXml(templateFile);
            int updatedCount = updateLegalTemplateTopicRefs(
                    document.getDocumentElement(),
                    legalIdsByTitle);

            if(updatedCount > 0){
                writeXml(document, templateFile);
            }
        }catch(ParserConfigurationException | SAXException
                | TransformerException exception){
            throw new IllegalArgumentException(
                    "법규 DITAMAP 템플릿의 legal 값을 최신 속성값으로 보정하지 못했습니다. "
                    + exception.getMessage(),
                    exception);
        }
    }

    private Map<String, String> collectUniqueLegalIdsByTitle(
            Path topicsDirectory)
            throws IOException, ParserConfigurationException, SAXException {
        Map<String, String> legalIdsByTitle = new HashMap<>();
        Set<String> duplicatedTitles = new HashSet<>();

        try(Stream<Path> stream = Files.walk(topicsDirectory)){
            for(Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(this::isDita)
                    .toList()){
                Document document = parseXml(path);
                Element root = document.getDocumentElement();
                String legalId = root.getAttribute("veh-legalid")
                        .trim();
                String title = firstDirectChildText(root, "title");

                if(title.isBlank() || legalId.isBlank()){
                    continue;
                }

                String previous = legalIdsByTitle.putIfAbsent(title, legalId);

                if(previous != null && !previous.equals(legalId)){
                    duplicatedTitles.add(title);
                }
            }
        }

        /*
         * LM-template.xml은 제목으로 legal 값을 보정한다.
         * 같은 제목이 여러 DITA에 있으면 다른 항목으로 잘못 연결될 수 있으므로
         * 중복 제목은 자동 보정 대상에서 제외한다.
         */
        duplicatedTitles.forEach(legalIdsByTitle::remove);
        return legalIdsByTitle;
    }

    private int updateLegalTemplateTopicRefs(
            Element parent,
            Map<String, String> legalIdsByTitle) {
        int updatedCount = 0;

        if("topicref".equals(parent.getTagName())
                && parent.hasAttribute("legal")){
            String title = firstTopicMetaText(parent, "linktext");

            if(title.isBlank()){
                title = firstTopicMetaText(parent, "navtitle");
            }

            if(title.isBlank()){
                title = firstDirectChildText(parent, "title");
            }

            String currentLegalId = legalIdsByTitle.get(title);

            if(currentLegalId != null
                    && !currentLegalId.equals(parent.getAttribute("legal"))){
                parent.setAttribute("legal", currentLegalId);
                updatedCount++;
            }
        }

        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(child instanceof Element element){
                updatedCount += updateLegalTemplateTopicRefs(
                        element,
                        legalIdsByTitle);
            }
        }

        return updatedCount;
    }

    private void deleteDitamaps(Path topicsDirectory) throws IOException {
        try(Stream<Path> stream = Files.walk(topicsDirectory)){
            for(Path path : stream
                    .filter(Files::isRegularFile)
                    .filter(this::isDitamap)
                    .toList()){
                Files.deleteIfExists(path);
            }
        }
    }

    private void copyLegalTopicsTo(Path topicsDirectory) throws IOException {
        Resource legalRoot = new ClassPathResource(RESOURCE_ROOT + "/legal");

        if(!legalRoot.exists()){
            throw new IllegalArgumentException(
                    "JAR 내부 legal 리소스 폴더를 찾지 못했습니다.");
        }

        Resource[] resources =
                new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + RESOURCE_ROOT + "/legal/*");

        Files.createDirectories(topicsDirectory);

        for(Resource resource : resources){
            if(!resource.exists() || !resource.isReadable()){
                continue;
            }

            String fileName = resource.getFilename();

            if(fileName == null || fileName.isBlank()){
                continue;
            }

            Path target = topicsDirectory.resolve(fileName)
                    .normalize();

            if(!target.startsWith(topicsDirectory)){
                throw new IllegalArgumentException(
                        "legal 리소스 경로가 올바르지 않습니다: " + fileName);
            }

            try(var input = resource.getInputStream()){
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isDitamap(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".ditamap");
    }

    private boolean isDita(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".dita");
    }

    private Path prepareToolDirectory() throws IOException {
        if(configuredToolDirectory != null){
            return configuredToolDirectory;
        }

        Path toolDirectory = Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "000-" + RUN_ID_FORMAT.format(LocalDateTime.now())
                + "-revision-tool").toAbsolutePath().normalize();

        for(String toolFile : TOOL_FILES){
            Resource resource = new ClassPathResource(
                    RESOURCE_ROOT + "/" + toolFile);

            if(!resource.exists()){
                throw new IllegalArgumentException(
                        "JAR 내부 legal DITAMAP 리소스를 찾지 못했습니다: "
                        + toolFile);
            }

            Path target = toolDirectory.resolve(toolFile)
                    .normalize();
            Files.createDirectories(target.getParent());

            try(var input = resource.getInputStream()){
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.createDirectories(toolDirectory.resolve("temp"));
        return toolDirectory;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if(directory == null || !Files.exists(directory)){
            return;
        }

        try(Stream<Path> stream = Files.walk(directory)){
            for(Path path : stream
                    .sorted(Comparator.reverseOrder())
                    .toList()){
                Files.deleteIfExists(path);
            }
        }
    }

    private Document parseXml(Path path)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                "");
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        try(InputStream input = Files.newInputStream(path)){
            return builder.parse(input);
        }
    }

    private void writeXml(Document document, Path target)
            throws IOException, TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                "");
        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(
                OutputKeys.ENCODING,
                "UTF-8");
        transformer.setOutputProperty(
                OutputKeys.INDENT,
                "yes");

        try(OutputStream output = Files.newOutputStream(target)){
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(output));
        }
    }

    private String firstDirectChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(child instanceof Element element
                    && tagName.equals(element.getTagName())){
                return element.getTextContent()
                        .trim();
            }
        }

        return "";
    }

    private String firstTopicMetaText(Element topicRef, String tagName) {
        NodeList children = topicRef.getChildNodes();

        for(int index = 0; index < children.getLength(); index++){
            Node child = children.item(index);

            if(!(child instanceof Element element)
                    || !"topicmeta".equals(element.getTagName())){
                continue;
            }

            String text = firstDirectChildText(element, tagName);

            if(!text.isBlank()){
                return text;
            }
        }

        return "";
    }
}
