package com.sts.dita.core;

import net.sf.saxon.s9api.*;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.trans.XPathException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.xml.sax.InputSource;

public final class SchematronValidator {
    private static final String MAOOM_NAMESPACE = "urn:maoom:schematron";
    private final Processor processor = new Processor(false);
    private final List<CompiledRule> rules;
    private final Path allowedProjectRoot;
    private final Map<String, Long> ruleNanos = new ConcurrentHashMap<>();
    private final AtomicInteger externalDocumentCount = new AtomicInteger();
    private final AtomicInteger parseFailureCount = new AtomicInteger();
    private volatile String currentRuleId = "";

    public SchematronValidator(Path schematronFile) throws Exception {
        this(schematronFile, schematronFile.toAbsolutePath().normalize().getParent());
    }

    public SchematronValidator(Path schematronFile, Path allowedProjectRoot) throws Exception {
        this.allowedProjectRoot = allowedProjectRoot.toRealPath();
        // doc()/document()는 현재 검사 프로젝트 내부 로컬 XML만 읽을 수 있다.
        configureDocumentResolver();
        // 검사 시작 시 .sch를 내부 Rule 목록으로 읽어 파일마다 schema XML을 재해석하지 않게 한다.
        this.rules = loadRules(schematronFile);
    }

    private void configureDocumentResolver() {
        var configuration = processor.getUnderlyingConfiguration();
        configuration.setResourceResolver(request -> {
            if (!ResourceRequest.XML_NATURE.equals(request.nature))
                throw new XPathException("Schematron 규칙의 XML 이외 외부 리소스 접근은 허용되지 않습니다: " + request.uri);
            return restrictedXmlSource(request);
        });
    }

    private Source restrictedXmlSource(ResourceRequest request) throws XPathException {
        try {
            URI uri = URI.create(request.uri);
            if (uri.getScheme() != null && !"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("HTTP/HTTPS 등 네트워크 URI는 허용되지 않습니다.");
            }
            Path requested = (uri.getScheme() == null ? allowedProjectRoot.resolve(request.uri) : Path.of(uri))
                    .toRealPath();
            if (!requested.startsWith(allowedProjectRoot)) {
                throw new IllegalArgumentException("허용된 검사 프로젝트 폴더 밖의 XML입니다.");
            }
            String name = requested.getFileName().toString().toLowerCase();
            if (!(name.endsWith(".xml") || name.endsWith(".dita") || name.endsWith(".ditamap"))) {
                throw new IllegalArgumentException("XML, DITA, DITAMAP 파일만 읽을 수 있습니다.");
            }
            InputSource input = new InputSource(requested.toUri().toString());
            externalDocumentCount.incrementAndGet();
            input.setPublicId(request.publicId);
            return new SAXSource(SecureXml.newXmlReader(), input);
        } catch (Exception exception) {
            throw new XPathException(
                    "Schematron doc() 접근이 차단되었습니다: " + request.uri + " (" + exception.getMessage() + ")",
                    exception);
        }
    }

    public List<ValidationIssue> validate(Path file, int mapOrder) {
        return validate(file, file.getFileName().toString(), List.of(), mapOrder, 0, ValidationCancellation.NONE);
    }

    public List<ValidationIssue> validate(Path file, int mapOrder, ValidationCancellation cancellation) {
        return validate(file, file.getFileName().toString(), List.of(), mapOrder, 0, cancellation);
    }

    public List<ValidationIssue> validate(Path file, String title, int mapOrder,
                                          ValidationCancellation cancellation) {
        return validate(file, title, List.of(), mapOrder, 0, cancellation);
    }

    public List<ValidationIssue> validate(Path file, String title, List<String> parentTitles, int mapOrder, int level,
                                          ValidationCancellation cancellation) {
        // 대상 XML의 행 번호를 보존하여 보고서에서 오류 발생 줄을 표시한다.
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            DocumentBuilder builder = processor.newDocumentBuilder();
            builder.setLineNumbering(true);
            InputSource input = new InputSource(file.toUri().toString());
            XdmNode document = builder.build(new SAXSource(SecureXml.newXmlReader(), input));
            // 리포트 타이틀은 DITAMAP의 축약 navtitle보다 실제 대상 문서의 title을 우선한다.
            String reportTitle = documentTitle(document, title, file);
            for (CompiledRule rule : rules) {
                currentRuleId = rule.id();
                long ruleStarted = System.nanoTime();
                cancellation.check();
                XPathSelector selector = rule.context().load();
                selector.setContextItem(document);
                for (XdmItem item : selector) {
                    cancellation.check();
                    if (!(item instanceof XdmNode node)) continue;
                    for (CompiledAssertion assertion : rule.assertions()) {
                        cancellation.check();
                        XPathSelector test = assertion.test().load();
                        test.setContextItem(node);
                        boolean matched = test.effectiveBooleanValue();
                        // assert는 false일 때, report는 true일 때 결과 항목으로 기록한다.
                        if ((!assertion.report() && !matched) || (assertion.report() && matched)) {
                            issues.add(new ValidationIssue(file, reportTitle, parentTitles, node.getLineNumber(),
                                    assertion.severity(), assertion.category(), rule.id(), nodePath(node),
                                    assertion.message(), mapOrder, level));
                        }
                    }
                }
                ruleNanos.merge(rule.id(), System.nanoTime() - ruleStarted, Long::sum);
                currentRuleId = "";
            }
        } catch (Exception ex) {
            // XML 읽기나 XPath 평가 중 interrupt가 발생한 경우 일반 파싱 오류로 바꾸지 않고 취소를 전달한다.
            cancellation.check();
            parseFailureCount.incrementAndGet();
            issues.add(new ValidationIssue(file, displayTitle(title, file), parentTitles, -1, "error", "system",
                    "document-parse-error", "", ex.getMessage() == null ? ex.toString() : ex.getMessage(), mapOrder, level));
        }
        return issues;
    }

    public Map<String, Long> ruleNanos() { return Map.copyOf(ruleNanos); }
    public int externalDocumentCount() { return externalDocumentCount.get(); }
    public int parseFailureCount() { return parseFailureCount.get(); }
    public String currentRuleId() { return currentRuleId; }

    private List<CompiledRule> loadRules(Path schema) throws Exception {
        var builder = SecureXml.newDocumentBuilder();
        var document = builder.parse(Files.newInputStream(schema));
        NodeList patterns = document.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "pattern");
        List<CompiledRule> result = new ArrayList<>();
        XPathCompiler xpath = processor.newXPathCompiler();
        for (int i = 0; i < patterns.getLength(); i++) {
            Element pattern = (Element) patterns.item(i);
            String id = pattern.getAttribute("id");
            NodeList ruleNodes = pattern.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "rule");
            for (int j = 0; j < ruleNodes.getLength(); j++) {
                Element ruleElement = (Element) ruleNodes.item(j);
                List<CompiledAssertion> assertions = new ArrayList<>();
                NodeList children = ruleElement.getChildNodes();
                for (int k = 0; k < children.getLength(); k++) {
                    Node child = children.item(k);
                    if (child instanceof Element element
                            && ("assert".equals(element.getLocalName()) || "report".equals(element.getLocalName()))) {
                        assertions.add(new CompiledAssertion(
                                xpath.compile(normalizeXPath(element.getAttribute("test"))),
                                severity(element.getAttribute("role")),
                                category(element),
                                message(element),
                                "report".equals(element.getLocalName())));
                    }
                }
                XPathExecutable context = xpath.compile("//" + ruleElement.getAttribute("context"));
                result.add(new CompiledRule(id, context, List.copyOf(assertions)));
            }
        }
        return List.copyOf(result);
    }

    private static String message(Element assertion) {
        StringBuilder result = new StringBuilder();
        NodeList children = assertion.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) result.append(child.getNodeValue());
            else if (child instanceof Element element && "value-of".equals(element.getLocalName())) {
                result.append(" {").append(element.getAttribute("select")).append("}");
            }
        }
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    private static String severity(String role) {
        if (role == null || role.isBlank()) return "none";
        return role.trim().toLowerCase();
    }

    private static String category(Element assertion) {
        String category = assertion.getAttributeNS(MAOOM_NAMESPACE, "category");
        if (category == null || category.isBlank()) category = assertion.getAttribute("category");
        return category == null || category.isBlank() ? "uncategorized" : category.trim().toLowerCase();
    }

    private static String displayTitle(String title, Path file) {
        return title == null || title.isBlank() ? file.getFileName().toString() : title.trim();
    }

    private static String documentTitle(XdmNode document, String fallbackTitle, Path file) {
        XdmSequenceIterator<XdmNode> documentChildren = document.axisIterator(Axis.CHILD);
        while (documentChildren.hasNext()) {
            XdmNode root = documentChildren.next();
            if (root.getNodeKind() != XdmNodeKind.ELEMENT) continue;
            XdmSequenceIterator<XdmNode> rootChildren = root.axisIterator(Axis.CHILD);
            while (rootChildren.hasNext()) {
                XdmNode child = rootChildren.next();
                QName name = child.getNodeName();
                if (child.getNodeKind() == XdmNodeKind.ELEMENT && name != null
                        && "title".equals(name.getLocalName())) {
                    String value = child.getStringValue().trim().replaceAll("\\s+", " ");
                    if (!value.isBlank()) return value;
                }
            }
            break;
        }
        return displayTitle(fallbackTitle, file);
    }

    private static String normalizeXPath(String expression) {
        // Saxon s9api XPath에서는 Schematron의 document() 호출을 XPath 2.0 doc()로 실행한다.
        return expression.replaceAll("(?<![\\w:-])document\\s*\\(", "doc(");
    }

    private static String nodePath(XdmNode node) {
        Deque<String> parts = new ArrayDeque<>();
        XdmNode current = node;
        while (current != null && current.getNodeKind() == XdmNodeKind.ELEMENT) {
            QName name = current.getNodeName();
            int position = 1;
            var siblings = current.axisIterator(Axis.PRECEDING_SIBLING);
            while (siblings.hasNext()) {
                XdmNode sibling = siblings.next();
                if (sibling.getNodeKind() == XdmNodeKind.ELEMENT && name.equals(sibling.getNodeName())) position++;
            }
            parts.addFirst(name + "[" + position + "]");
            current = current.getParent();
        }
        return "/" + String.join("/", parts);
    }

    private record CompiledRule(String id, XPathExecutable context, List<CompiledAssertion> assertions) { }
    private record CompiledAssertion(
            XPathExecutable test, String severity, String category, String message, boolean report) { }
}
