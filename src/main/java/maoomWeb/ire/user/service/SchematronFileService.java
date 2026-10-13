package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.SchematronItem;
import static maoomWeb.ire.user.dto.DitaValidationDtos.SchematronChoice;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import net.sf.saxon.s9api.Processor;

/**
 * 배포 PC의 외부 Schematron 폴더를 관리한다.
 * DOCS/PUB 접두사의 가장 높은 숫자 버전을 선택하고 실행 전에 XML·XPath 유효성을 검사한다.
 */
@Service
public class SchematronFileService {
    private static final String NAMESPACE = "http://purl.oclc.org/dsdl/schematron";
    private static final List<String> DEFAULT_SCHEMAS = List.of(
            "DOCS_CHECK_v1.0.5.sch", "PUB_CHECK_v1.0.1.sch");
    private static final List<SchemaKind> SCHEMA_KINDS = List.of(
            new SchemaKind("DOCS", "원고", Pattern.compile("^DOCS_CHECK_v(\\d+)\\.(\\d+)\\.(\\d+)\\.sch$")),
            new SchemaKind("PUB", "퍼블리싱", Pattern.compile("^PUB_CHECK_v(\\d+)\\.(\\d+)\\.(\\d+)\\.sch$")));
    private static final DateTimeFormatter MODIFIED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path schematronRoot;

    public SchematronFileService(@Value("${dita.validation.schematron-dir:./schematron}") String directory) {
        this.schematronRoot = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<SchematronItem> listFiles() throws Exception {
        initializeDirectory();
        try (var paths = Files.list(schematronRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".sch"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(this::item)
                    .toList();
        }
    }

    public List<SchematronChoice> latestChoices() throws Exception {
        initializeDirectory();
        return SCHEMA_KINDS.stream().map(this::latestChoice).toList();
    }

    public Path resolveLatest(String type) throws Exception {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("검사 유형을 선택해 주세요.");
        SchemaKind kind = SCHEMA_KINDS.stream()
                .filter(value -> value.type().equalsIgnoreCase(type.trim()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("올바른 검사 유형이 아닙니다."));
        SchematronChoice choice = latestChoice(kind);
        if (!choice.available()) {
            throw new IllegalArgumentException(kind.label() + " Schematron을 사용할 수 없습니다. "
                    + choice.validationMessage());
        }
        return resolveAndValidate(choice.fileName());
    }

    public Path resolveAndValidate(String fileName) throws Exception {
        initializeDirectory();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Schematron 파일을 선택해 주세요.");
        }
        String trimmed = fileName.trim();
        if (!trimmed.toLowerCase().endsWith(".sch") || !Path.of(trimmed).getFileName().toString().equals(trimmed)) {
            throw new IllegalArgumentException("올바른 Schematron 파일명이 아닙니다.");
        }
        // 파일명만 허용하여 ../ 등을 이용한 Schematron 폴더 밖 접근을 차단한다.
        Path path = schematronRoot.resolve(trimmed).normalize();
        if (!path.getParent().equals(schematronRoot) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("선택한 Schematron 파일을 찾을 수 없습니다.");
        }
        validate(path);
        return path;
    }

    public void validate(Path path) throws Exception {
        // 외부 엔티티를 끄고 schema 구조와 모든 context/test XPath를 실행 전에 컴파일해 본다.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        var document = builder.parse(path.toFile());
        Element root = document.getDocumentElement();
        if (root == null || !NAMESPACE.equals(root.getNamespaceURI()) || !"schema".equals(root.getLocalName())) {
            throw new IllegalArgumentException("Schematron schema 네임스페이스가 올바르지 않습니다.");
        }
        NodeList patterns = document.getElementsByTagNameNS(NAMESPACE, "pattern");
        if (patterns.getLength() == 0) throw new IllegalArgumentException("Schematron pattern이 없습니다.");
        Processor processor = new Processor(false);
        var xpath = processor.newXPathCompiler();
        int assertionCount = 0;
        for (int i = 0; i < patterns.getLength(); i++) {
            Element pattern = (Element) patterns.item(i);
            if (pattern.getAttribute("id").isBlank()) throw new IllegalArgumentException("pattern id가 비어 있습니다.");
            NodeList rules = pattern.getElementsByTagNameNS(NAMESPACE, "rule");
            for (int j = 0; j < rules.getLength(); j++) {
                Element rule = (Element) rules.item(j);
                String context = rule.getAttribute("context");
                if (context.isBlank()) throw new IllegalArgumentException("rule context가 비어 있습니다.");
                xpath.compile("//" + context);
                NodeList children = rule.getChildNodes();
                for (int k = 0; k < children.getLength(); k++) {
                    Node child = children.item(k);
                    if (child instanceof Element assertion && NAMESPACE.equals(assertion.getNamespaceURI())
                            && "assert".equals(assertion.getLocalName())) {
                        String test = assertion.getAttribute("test");
                        if (test.isBlank()) throw new IllegalArgumentException("assert test가 비어 있습니다.");
                        xpath.compile(normalizeXPath(test));
                        assertionCount++;
                    } else if (child instanceof Element report && NAMESPACE.equals(report.getNamespaceURI())
                            && "report".equals(report.getLocalName())) {
                        String test = report.getAttribute("test");
                        if (test.isBlank()) throw new IllegalArgumentException("report test가 비어 있습니다.");
                        xpath.compile(normalizeXPath(test));
                        assertionCount++;
                    }
                }
            }
        }
        if (assertionCount == 0) throw new IllegalArgumentException("Schematron assert가 없습니다.");
    }

    public String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public Path copySnapshot(Path source, Path targetDirectory) throws Exception {
        // 검사 당시 규칙을 이력 폴더에 보존하여 이후 원본 .sch가 바뀌어도 실행 근거를 남긴다.
        Files.createDirectories(targetDirectory);
        Path target = targetDirectory.resolve(source.getFileName().toString()).normalize();
        if (!target.getParent().equals(targetDirectory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Schematron 스냅샷 경로가 올바르지 않습니다.");
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        validate(target);
        return target;
    }

    Path root() {
        return schematronRoot;
    }

    private SchematronItem item(Path path) {
        String validationMessage = null;
        try { validate(path); }
        catch (Exception ex) { validationMessage = message(ex); }
        try {
            String modified = Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.systemDefault())
                    .format(MODIFIED_FORMAT);
            return new SchematronItem(path.getFileName().toString(), Files.size(path), modified,
                    validationMessage == null, validationMessage);
        } catch (Exception ex) {
            return new SchematronItem(path.getFileName().toString(), 0, "", false, message(ex));
        }
    }

    private SchematronChoice latestChoice(SchemaKind kind) {
        // 문자열 정렬이 아니라 major/minor/patch 숫자를 비교하므로 v1.0.10도 올바르게 선택된다.
        Candidate latest = null;
        try (var paths = Files.list(schematronRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Matcher matcher = kind.pattern().matcher(path.getFileName().toString());
                if (!matcher.matches()) continue;
                Version version = new Version(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                if (latest == null || version.compareTo(latest.version()) > 0) latest = new Candidate(path, version);
            }
        } catch (Exception ex) {
            return new SchematronChoice(kind.type(), kind.label(), null, null, false, message(ex));
        }
        if (latest == null) {
            return new SchematronChoice(kind.type(), kind.label(), null, null, false,
                    kind.pattern().pattern() + " 형식의 파일이 없습니다.");
        }
        try {
            validate(latest.path());
            return new SchematronChoice(kind.type(), kind.label(), latest.path().getFileName().toString(),
                    latest.version().toString(), true, null);
        } catch (Exception ex) {
            return new SchematronChoice(kind.type(), kind.label(), latest.path().getFileName().toString(),
                    latest.version().toString(), false, message(ex));
        }
    }

    private synchronized void initializeDirectory() throws Exception {
        // 최초 배포에서만 내장 기본 규칙을 복사하고, 운영자가 관리하는 기존 파일은 덮어쓰지 않는다.
        boolean firstCreation = Files.notExists(schematronRoot);
        Files.createDirectories(schematronRoot);
        if (!firstCreation) return;
        for (String defaultSchema : DEFAULT_SCHEMAS) {
            ClassPathResource bundled = new ClassPathResource("schematron/" + defaultSchema);
            if (!bundled.exists()) continue;
            try (InputStream input = bundled.getInputStream()) {
                Files.copy(input, schematronRoot.resolve(defaultSchema), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String normalizeXPath(String expression) {
        return expression.replaceAll("(?<![\\w:-])document\\s*\\(", "doc(");
    }

    private record SchemaKind(String type, String label, Pattern pattern) { }
    private record Candidate(Path path, Version version) { }
    private record Version(int major, int minor, int patch) implements Comparable<Version> {
        @Override public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            if (result == 0) result = Integer.compare(minor, other.minor);
            if (result == 0) result = Integer.compare(patch, other.patch);
            return result;
        }
        @Override public String toString() { return "v" + major + "." + minor + "." + patch; }
    }
}
