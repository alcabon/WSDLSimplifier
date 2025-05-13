import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * WSDLSimplifier
 * ---------------
 * Simplifies a WSDL file by:
 *  1. Removing unused WSDL elements (message, portType, binding, service).
 *  2. Building a type dependency graph.
 *  3. Pruning XSD types not referenced in the kept elements.
 *  4. Cleaning up extra whitespace.
 */
public class WSDLSimplifier {

    // Set of type names that must be retained in the final WSDL
    private static Set<String> necessaryTypes = new HashSet<>();

    // Helper set to avoid re-processing types (not used in this version but reserved)
    private static Set<String> analyzedTypes = new HashSet<>();

    // Map from a type name to the set of other types it references
    private static Map<String, Set<String>> typeHierarchy = new HashMap<>();

    /**
     * Entry point: loads the original WSDL, performs four passes, writes out the simplified WSDL.
     */
    public static void main(String[] args) {
        try {
            // Parse the input WSDL
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse("AccountSOAPService.xml");

            // Pass 1: strip WSDL elements except those needing full content
            firstPass(doc.getDocumentElement());
            // Pass 2: read XSD schema and build type hierarchy
            secondPass(doc.getDocumentElement());
            // Pass 3: expand necessaryTypes by following references
            thirdPass();
            // Pass 4: remove XSD types not in necessaryTypes
            finalPass(doc.getDocumentElement());

            // Transform DOM back to XML with pretty print
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source = new DOMSource(doc);

            StringWriter writer = new StringWriter();
            transformer.transform(source, new StreamResult(writer));

            // Clean up excess blank lines
            String cleanedXml = removeExcessiveWhitespace(writer.toString());

            // Write the simplified WSDL to disk
            Files.write(Paths.get("SimplifiedAccountSOAPService.wsdl"), cleanedXml.getBytes());
            System.out.println("WSDL simplified and cleaned successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes consecutive empty lines from the XML string.
     */
    private static String removeExcessiveWhitespace(String xml) {
        StringBuilder cleaned = new StringBuilder();
        String[] lines = xml.split("\n");
        boolean previousLineEmpty = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.append(line).append("\n");
                previousLineEmpty = false;
            } else if (!previousLineEmpty) {
                cleaned.append("\n");
                previousLineEmpty = true;
            }
        }
        return cleaned.toString().trim();
    }

    /**
     * First pass: retain only top-level definitions, types, and WSDL elements
     * marked to keep full content. Other elements are removed immediately.
     */
    private static void firstPass(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) child;
            String name = elem.getNodeName();

            if (isKeepFullContentElement(name)) {
                // Analyze content of this element for type references
                analyzeContent(elem);
            } else if (!name.equals("definitions") && !name.equals("types")) {
                // Remove everything except definitions, types, and kept WSDL parts
                root.removeChild(elem);
            }
        }
    }

    /**
     * Gathers type names from 'type' and 'element' attributes under a WSDL element.
     */
    private static void analyzeContent(Element element) {
        NodeList all = element.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element child = (Element) all.item(i);
            String t = child.getAttribute("type");
            if (!t.isEmpty()) {
                necessaryTypes.add(t.split(":")[1]);
            }
            String e = child.getAttribute("element");
            if (!e.isEmpty()) {
                necessaryTypes.add(e.split(":")[1]);
            }
        }
    }

    /**
     * Second pass: find the <xsd:schema> element under <types> and build the type hierarchy.
     */
    private static void secondPass(Element root) {
        NodeList typesList = root.getElementsByTagName("types");
        if (typesList.getLength() == 0) return;
        Element typesElem = (Element) typesList.item(0);
        NodeList schemas = typesElem.getElementsByTagName("xsd:schema");
        for (int i = 0; i < schemas.getLength(); i++) {
            buildTypeHierarchy((Element) schemas.item(i));
        }
    }

    /**
     * Reads each named XSD component and records which other types it references.
     */
    private static void buildTypeHierarchy(Element schema) {
        NodeList children = schema.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            String name = e.getAttribute("name");
            if (!name.isEmpty()) {
                typeHierarchy.put(name, analyzeTypeContent(e));
            }
        }
    }

    /**
     * Extracts referenced type names from within an XSD component.
     */
    private static Set<String> analyzeTypeContent(Element element) {
        Set<String> refs = new HashSet<>();
        NodeList nodes = element.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element c = (Element) nodes.item(i);
            String t = c.getAttribute("type");
            if (!t.isEmpty()) {
                refs.add(t.split(":")[1]);
            }
            String el = c.getAttribute("element");
            if (!el.isEmpty()) {
                refs.add(el.split(":")[1]);
            }
        }
        return refs;
    }

    /**
     * Third pass: recursively include any types referenced by already necessary types.
     */
    private static void thirdPass() {
        Set<String> expanded = new HashSet<>(necessaryTypes);
        boolean changed;
        do {
            changed = false;
            for (String t : new HashSet<>(expanded)) {
                Set<String> deps = typeHierarchy.get(t);
                if (deps != null) {
                    for (String dep : deps) {
                        if (expanded.add(dep)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
        necessaryTypes = expanded;
    }

    /**
     * Final pass: remove any XSD components not in the necessaryTypes set.
     */
    private static void finalPass(Element root) {
        NodeList typesList = root.getElementsByTagName("types");
        if (typesList.getLength() == 0) return;
        Element typesElem = (Element) typesList.item(0);
        NodeList schemas = typesElem.getElementsByTagName("xsd:schema");
        for (int i = 0; i < schemas.getLength(); i++) {
            removeUnnecessaryTypes((Element) schemas.item(i));
        }
    }

    /**
     * Deletes child elements whose 'name' attribute is not in necessaryTypes.
     */
    private static void removeUnnecessaryTypes(Element schema) {
        NodeList children = schema.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            String name = e.getAttribute("name");
            if (!necessaryTypes.contains(name)) {
                schema.removeChild(e);
            }
        }
    }

    /**
     * Defines which WSDL elements are kept intact and fully analyzed.
     */
    private static boolean isKeepFullContentElement(String nodeName) {
        return nodeName.equals("message")
            || nodeName.equals("portType")
            || nodeName.equals("binding")
            || nodeName.equals("service");
    }
}
