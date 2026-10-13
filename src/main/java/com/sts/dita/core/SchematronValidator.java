package com.sts.dita.core;

import net.sf.saxon.s9api.*;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.trans.XPathException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.xml.sax.InputSource;

public final class SchematronValidator {
    private final Processor processor = new Processor(false);
    private final List<Rule> rules;

    public SchematronValidator(Path schematronFile) throws Exception {
        // 검사 규칙 안의 doc()/document()도 외부 DTD 없이 안전하게 연결 XML을 읽도록 설정한다.
        configureDocumentResolver();
        // 검사 시작 시 .sch를 내부 Rule 목록으로 읽어 파일마다 schema XML을 재해석하지 않게 한다.
        this.rules = loadRules(schematronFile);
    }

    private void configureDocumentResolver() {
        var configuration = processor.getUnderlyingConfiguration();
        ResourceResolver defaultResolver = configuration.getResourceResolver();
        configuration.setResourceResolver(request -> {
            if (!ResourceRequest.XML_NATURE.equals(request.nature)) {
                return defaultResolver.resolve(request);
            }
            return xmlSourceWithoutExternalDtd(request);
        });
    }

    private static Source xmlSourceWithoutExternalDtd(ResourceRequest request)
            throws XPathException {
        try {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            saxFactory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            var xmlReader = saxFactory.newSAXParser().getXMLReader();
            xmlReader.setEntityResolver(
                    (publicId, systemId) ->
                            new InputSource(new StringReader("")));
            InputSource input = new InputSource(request.uri);
            input.setPublicId(request.publicId);
            return new SAXSource(xmlReader, input);
        } catch (Exception exception) {
            throw new XPathException(
                    "연결 XML 문서를 준비하지 못했습니다: " + request.uri,
                    exception);
        }
    }

    public List<ValidationIssue> validate(Path file, int mapOrder) {
        // 대상 XML의 행 번호를 보존하여 보고서에서 오류 발생 줄을 표시한다.
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            DocumentBuilder builder = processor.newDocumentBuilder();
            builder.setLineNumbering(true);
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var xmlReader = saxFactory.newSAXParser().getXMLReader();
            xmlReader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            InputSource input = new InputSource(file.toUri().toString());
            XdmNode document = builder.build(new SAXSource(xmlReader, input));
            XPathCompiler xpath = processor.newXPathCompiler();
            for (Rule rule : rules) {
                XPathSelector selector = xpath.compile("//" + rule.context()).load();
                selector.setContextItem(document);
                for (XdmItem item : selector) {
                    if (!(item instanceof XdmNode node)) continue;
                    for (Assertion assertion : rule.assertions()) {
                        XPathSelector test = xpath.compile(normalizeXPath(assertion.test())).load();
                        test.setContextItem(node);
                        boolean matched = test.effectiveBooleanValue();
                        // assert는 false일 때, report는 true일 때 결과 항목으로 기록한다.
                        if ((!assertion.report() && !matched) || (assertion.report() && matched)) {
                            issues.add(new ValidationIssue(file, node.getLineNumber(), assertion.severity(), rule.id(),
                                    nodePath(node), assertion.message(), mapOrder));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            issues.add(new ValidationIssue(file, -1, "error", "document-parse-error", "",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage(), mapOrder));
        }
        return issues;
    }

    private static List<Rule> loadRules(Path schema) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        var document = builder.parse(Files.newInputStream(schema));
        NodeList patterns = document.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "pattern");
        List<Rule> result = new ArrayList<>();
        for (int i = 0; i < patterns.getLength(); i++) {
            Element pattern = (Element) patterns.item(i);
            String id = pattern.getAttribute("id");
            NodeList ruleNodes = pattern.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "rule");
            for (int j = 0; j < ruleNodes.getLength(); j++) {
                Element ruleElement = (Element) ruleNodes.item(j);
                List<Assertion> assertions = new ArrayList<>();
                NodeList children = ruleElement.getChildNodes();
                for (int k = 0; k < children.getLength(); k++) {
                    Node child = children.item(k);
                    if (child instanceof Element element
                            && ("assert".equals(element.getLocalName()) || "report".equals(element.getLocalName()))) {
                        assertions.add(new Assertion(
                                element.getAttribute("test"),
                                severity(element.getAttribute("role")),
                                message(element),
                                "report".equals(element.getLocalName())));
                    }
                }
                result.add(new Rule(id, ruleElement.getAttribute("context"), List.copyOf(assertions)));
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

    private record Rule(String id, String context, List<Assertion> assertions) { }
    private record Assertion(String test, String severity, String message, boolean report) { }
}
