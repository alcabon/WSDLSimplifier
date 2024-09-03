import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class WSDLSimplifier {
    private static Set<String> necessaryTypes = new HashSet<>();
    private static Set<String> analyzedTypes = new HashSet<>();
    private static Map<String, Set<String>> typeHierarchy = new HashMap<>();
    
    public static void main(String[] args) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse("AccountSOAPService.xml");

            firstPass(doc.getDocumentElement());
            secondPass(doc.getDocumentElement());
            thirdPass();
            finalPass(doc.getDocumentElement());

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source = new DOMSource(doc);
            
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
            
            String xmlString = writer.toString();
            String cleanedXml = removeExcessiveWhitespace(xmlString);
            
            Files.write(Paths.get("SimplifiedAccountSOAPService.wsdl"), cleanedXml.getBytes());

            System.out.println("WSDL simplified and cleaned successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String removeExcessiveWhitespace(String xml) {
        StringBuilder cleaned = new StringBuilder();
        String[] lines = xml.split("\n");
        boolean previousLineEmpty = false;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                cleaned.append(line).append("\n");
                previousLineEmpty = false;
            } else if (!previousLineEmpty) {
                cleaned.append("\n");
                previousLineEmpty = true;
            }
        }
        
        return cleaned.toString().trim();
    }


    private static void firstPass(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String nodeName = element.getNodeName();
                if (isKeepFullContentElement(nodeName)) {
                    analyzeContent(element);
                } else if (!nodeName.equals("definitions") && !nodeName.equals("types")) {
                    root.removeChild(element);
                }
            }
        }
    }

    private static void analyzeContent(Element element) {
        NodeList children = element.getElementsByTagName("*");
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            String typeName = child.getAttribute("type");
            if (!typeName.isEmpty()) {
                necessaryTypes.add(typeName.split(":")[1]);
            }
            String elementName = child.getAttribute("element");
            if (!elementName.isEmpty()) {
                necessaryTypes.add(elementName.split(":")[1]);
            }
        }
    }

    private static void secondPass(Element root) {
        NodeList types = root.getElementsByTagName("types");
        if (types.getLength() > 0) {
            Element typesElement = (Element) types.item(0);
            NodeList schemas = typesElement.getElementsByTagName("xsd:schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                Element schema = (Element) schemas.item(i);
                buildTypeHierarchy(schema);
            }
        }
    }

    private static void buildTypeHierarchy(Element schema) {
        NodeList children = schema.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String name = element.getAttribute("name");
                if (!name.isEmpty()) {
                    Set<String> referencedTypes = analyzeTypeContent(element);
                    typeHierarchy.put(name, referencedTypes);
                }
            }
        }
    }

    private static Set<String> analyzeTypeContent(Element element) {
        Set<String> referencedTypes = new HashSet<>();
        NodeList children = element.getElementsByTagName("*");
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            String typeName = child.getAttribute("type");
            if (!typeName.isEmpty()) {
                referencedTypes.add(typeName.split(":")[1]);
            }
            String elementName = child.getAttribute("element");
            if (!elementName.isEmpty()) {
                referencedTypes.add(elementName.split(":")[1]);
            }
        }
        return referencedTypes;
    }

    private static void thirdPass() {
        Set<String> expandedNecessaryTypes = new HashSet<>(necessaryTypes);
        boolean changed;
        do {
            changed = false;
            for (String type : new HashSet<>(expandedNecessaryTypes)) {
                Set<String> referencedTypes = typeHierarchy.get(type);
                if (referencedTypes != null) {
                    for (String referencedType : referencedTypes) {
                        if (expandedNecessaryTypes.add(referencedType)) {
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);
        necessaryTypes = expandedNecessaryTypes;
    }

    private static void finalPass(Element root) {
        NodeList types = root.getElementsByTagName("types");
        if (types.getLength() > 0) {
            Element typesElement = (Element) types.item(0);
            NodeList schemas = typesElement.getElementsByTagName("xsd:schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                Element schema = (Element) schemas.item(i);
                removeUnnecessaryTypes(schema);
            }
        }
    }

    private static void removeUnnecessaryTypes(Element schema) {
        NodeList children = schema.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String name = element.getAttribute("name");
                if (!necessaryTypes.contains(name)) {
                    schema.removeChild(element);
                }
            }
        }
    }

    private static boolean isKeepFullContentElement(String nodeName) {
        return nodeName.equals("message") ||
               nodeName.equals("portType") ||
               nodeName.equals("binding") ||
               nodeName.equals("service");
    }
}
