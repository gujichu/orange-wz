package orange.wz.provider.tools;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.WzXmlFile;
import orange.wz.provider.properties.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static orange.wz.provider.tools.XmlExport.escapeText;
import static orange.wz.provider.tools.XmlImport.unescapeText;

@Slf4j
public final class XmlPropertyTransfer {

    private static final int INDENT = 4;
    private static final MediaExportType ME_TYPE = MediaExportType.BASE64;

    private XmlPropertyTransfer() {
        // Private constructor to prevent instantiation
    }

    /**
     * 将单个 WzImageProperty 节点导出为 XML 字符串
     * @param property 要导出的节点
     * @return XML 字符串
     */
    public static String exportToXml(WzImageProperty property) {
        StringWriter writer = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(writer);
        
        try {
            // 写入 XML 头部
            bufferedWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            bufferedWriter.newLine();
            bufferedWriter.write("<root>");
            bufferedWriter.newLine();
            
            // 写入属性
            writeProperty(bufferedWriter, property, "", 1);
            
            bufferedWriter.write("</root>");
            bufferedWriter.flush();
        } catch (IOException e) {
            log.error("导出 XML 失败", e);
            throw new RuntimeException("导出 XML 失败", e);
        }
        
        return writer.toString();
    }

    /**
     * 将多个 WzImageProperty 节点导出为 XML 字符串
     * @param properties 要导出的节点列表
     * @return XML 字符串
     */
    public static String exportToXml(List<WzImageProperty> properties) {
        StringWriter writer = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(writer);
        
        try {
            // 写入 XML 头部
            bufferedWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            bufferedWriter.newLine();
            bufferedWriter.write("<root>");
            bufferedWriter.newLine();
            
            // 写入所有属性
            for (WzImageProperty property : properties) {
                writeProperty(bufferedWriter, property, "", 1);
            }
            
            bufferedWriter.write("</root>");
            bufferedWriter.flush();
        } catch (IOException e) {
            log.error("导出 XML 失败", e);
            throw new RuntimeException("导出 XML 失败", e);
        }
        
        return writer.toString();
    }

    /**
     * 递归写入属性到 XML
     */
    private static void writeProperty(BufferedWriter writer, WzImageProperty property, String mediaFilename, int indentLevel) throws IOException {
        // 写入缩进
        writeIndent(writer, indentLevel);
        
        switch (property) {
            case WzCanvasProperty prop -> {
                String etName = escapeText(prop.getName());
                String width = String.valueOf(prop.getWidth());
                String height = String.valueOf(prop.getHeight());
                String format = String.valueOf(prop.getFormat().getValue());
                String scale = String.valueOf(prop.getScale());
                
                String context = "<canvas name=\"" + etName + "\" width=\"" + width + "\" height=\"" + height + "\" format=\"" + format + "\" scale=\"" + scale + "\"";
                
                if (ME_TYPE == MediaExportType.BASE64) {
                    context = context + " basedata=\"" + Base64Tool.coverBytesToBase64(prop.getImageBytes(false)) + "\"";
                }
                
                if (prop.getChildren() == null || prop.getChildren().isEmpty()) {
                    context += "/>";
                    writer.write(context);
                    writer.newLine();
                } else {
                    context += ">";
                    writer.write(context);
                    writer.newLine();
                    for (WzImageProperty subProperty : prop.getChildren()) {
                        writeProperty(writer, subProperty, mediaFilename + prop.getName() + ".", indentLevel + 1);
                    }
                    writeIndent(writer, indentLevel);
                    writer.write("</canvas>");
                    writer.newLine();
                }
            }
            
            case WzConvexProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<extended name=\"" + etName + "\"");
                
                if (prop.getChildren() == null || prop.getChildren().isEmpty()) {
                    writer.write("/>");
                    writer.newLine();
                } else {
                    writer.write(">");
                    writer.newLine();
                    for (WzImageProperty subProperty : prop.getChildren()) {
                        writeProperty(writer, subProperty, mediaFilename + prop.getName() + ".", indentLevel + 1);
                    }
                    writeIndent(writer, indentLevel);
                    writer.write("</extended>");
                    writer.newLine();
                }
            }
            
            case WzDoubleProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<double name=\"" + etName + "\" value=\"" + prop.getValue() + "\"/>");
                writer.newLine();
            }
            
            case WzFloatProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<float name=\"" + etName + "\" value=\"" + prop.getValue() + "\"/>");
                writer.newLine();
            }
            
            case WzIntProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<int name=\"" + etName + "\" value=\"" + prop.getValue() + "\"/>");
                writer.newLine();
            }
            
            case WzListProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<imgdir name=\"" + etName + "\"");
                
                if (prop.getChildren() == null || prop.getChildren().isEmpty()) {
                    writer.write("/>");
                    writer.newLine();
                } else {
                    writer.write(">");
                    writer.newLine();
                    for (WzImageProperty subProperty : prop.getChildren()) {
                        writeProperty(writer, subProperty, mediaFilename + prop.getName() + ".", indentLevel + 1);
                    }
                    writeIndent(writer, indentLevel);
                    writer.write("</imgdir>");
                    writer.newLine();
                }
            }
            
            case WzLongProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<long name=\"" + etName + "\" value=\"" + prop.getValue() + "\"/>");
                writer.newLine();
            }
            
            case WzNullProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<null name=\"" + etName + "\"/>");
                writer.newLine();
            }
            
            case WzShortProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<short name=\"" + etName + "\" value=\"" + prop.getValue() + "\"/>");
                writer.newLine();
            }
            
            case WzSoundProperty prop -> {
                String etName = escapeText(prop.getName());
                String context = "<sound name=\"" + etName + "\"";
                
                if (ME_TYPE == MediaExportType.BASE64) {
                    String basehead = Base64Tool.coverBytesToBase64(prop.getHeader());
                    String basedata = Base64Tool.coverBytesToBase64(prop.getSoundBytes(false));
                    context = context + " length=\"" + prop.getLenMs() + "\" basehead=\"" + basehead + "\" basedata=\"" + basedata + "\"/>";
                } else {
                    context += "/>";
                }
                
                writer.write(context);
                writer.newLine();
            }
            
            case WzStringProperty prop -> {
                String etName = escapeText(prop.getName());
                String etValue = escapeText(prop.getValue());
                writer.write("<string name=\"" + etName + "\" value=\"" + etValue + "\"/>");
                writer.newLine();
            }
            
            case WzUOLProperty prop -> {
                String etName = escapeText(prop.getName());
                String etValue = escapeText(prop.getValue());
                writer.write("<uol name=\"" + etName + "\" value=\"" + etValue + "\"/>");
                writer.newLine();
            }
            
            case WzVectorProperty prop -> {
                String etName = escapeText(prop.getName());
                writer.write("<vector name=\"" + etName + "\" x=\"" + prop.getX() + "\" y=\"" + prop.getY() + "\"/>");
                writer.newLine();
            }
            
            default -> log.error("未知的节点类型: {}", property.getName());
        }
    }

    /**
     * 写入指定级别的缩进
     */
    private static void writeIndent(BufferedWriter writer, int level) throws IOException {
        if (level <= 0) return;
        int spaces = INDENT * level;
        char[] buffer = new char[spaces];
        Arrays.fill(buffer, ' ');
        writer.write(buffer);
    }

    /**
     * 从 XML 字符串导入单个 WzImageProperty 节点
     * @param xml XML 字符串
     * @param parent 父节点
     * @param wzImage WzImage 对象
     * @return 导入的节点
     */
    public static WzImageProperty importFromXml(String xml, WzObject parent, WzImage wzImage) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            Document doc = builder.parse(input);

            Element root = doc.getDocumentElement();
            if (!root.getNodeName().equals("root")) {
                log.error("XML 根节点必须是 root");
                return null;
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node instanceof Element element) {
                    return readProperty(element, parent, wzImage, "");
                }
            }

            return null;
        } catch (Exception e) {
            log.error("解析 XML 失败", e);
            return null;
        }
    }

    /**
     * 从 XML 字符串导入多个 WzImageProperty 节点
     * @param xml XML 字符串
     * @param parent 父节点
     * @param wzImage WzImage 对象
     * @return 导入的节点列表
     */
    public static List<WzImageProperty> importFromXmlToList(String xml, WzObject parent, WzImage wzImage) {
        List<WzImageProperty> result = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            Document doc = builder.parse(input);

            Element root = doc.getDocumentElement();
            if (!root.getNodeName().equals("root")) {
                log.error("XML 根节点必须是 root");
                return result;
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node instanceof Element element) {
                    WzImageProperty prop = readProperty(element, parent, wzImage, "");
                    if (prop != null) {
                        result.add(prop);
                    }
                }
            }

            return result;
        } catch (Exception e) {
            log.error("解析 XML 失败", e);
            return result;
        }
    }

    /**
     * 从 XML 元素读取属性
     */
    private static WzImageProperty readProperty(Element e, WzObject parent, WzImage wzImage, String mediaFileName) {
        String name = unescapeText(e.getAttribute("name"));

        return switch (e.getNodeName()) {
            case "imgdir" -> {
                WzListProperty list = new WzListProperty(name, parent, wzImage);
                readChildren(e, list, wzImage, mediaFileName + name + ".");
                yield list;
            }

            case "canvas" -> {
                int width = 0;
                int height = 0;
                int format = 2;
                int scale = 0;
                try {
                    width = Integer.parseInt(e.getAttribute("width"));
                    height = Integer.parseInt(e.getAttribute("height"));
                    if (e.hasAttribute("format")) {
                        format = Integer.parseInt(e.getAttribute("format"));
                    }
                    if (e.hasAttribute("scale")) {
                        scale = Integer.parseInt(e.getAttribute("scale"));
                    }
                } catch (Exception ex) {
                    log.warn("CanvasNode: {} Error: {}", name, ex.getMessage());
                }

                byte[] imageBytes = null;
                if (e.hasAttribute("basedata")) {
                    imageBytes = Base64Tool.coverBase64ToBytes(e.getAttribute("basedata"));
                }

                // 使用与 XmlImport 相同的方式创建 Canvas 节点
                WzCanvasProperty canvas = new WzCanvasProperty(name, width, height, format, scale, imageBytes, parent, wzImage);
                readChildren(e, canvas, wzImage, mediaFileName + name + ".");
                yield canvas;
            }
            
            case "extended" -> {
                WzConvexProperty convex = new WzConvexProperty(name, parent, wzImage);
                readChildren(e, convex, wzImage, mediaFileName + name + ".");
                yield convex;
            }
            
            case "int" -> new WzIntProperty(name, Integer.parseInt(e.getAttribute("value")), parent, wzImage);
            
            case "short" -> new WzShortProperty(name, Short.parseShort(e.getAttribute("value")), parent, wzImage);
            
            case "long" -> new WzLongProperty(name, Long.parseLong(e.getAttribute("value")), parent, wzImage);
            
            case "float" -> new WzFloatProperty(name, Float.parseFloat(e.getAttribute("value")), parent, wzImage);
            
            case "double" -> new WzDoubleProperty(name, Double.parseDouble(e.getAttribute("value")), parent, wzImage);
            
            case "string" -> new WzStringProperty(name, unescapeText(e.getAttribute("value")), parent, wzImage);
            
            case "uol" -> new WzUOLProperty(name, unescapeText(e.getAttribute("value")), parent, wzImage);
            
            case "vector" -> {
                int x = Integer.parseInt(e.getAttribute("x"));
                int y = Integer.parseInt(e.getAttribute("y"));
                yield new WzVectorProperty(name, x, y, parent, wzImage);
            }
            
            case "sound" -> {
                int length = 0;
                byte[] header = null;
                byte[] mp3 = null;
                try {
                    if (e.hasAttribute("basedata")) {
                        if (e.hasAttribute("length")) {
                            length = Integer.parseInt(e.getAttribute("length"));
                        }
                        header = Base64Tool.coverBase64ToBytes(e.getAttribute("basehead"));
                        mp3 = Base64Tool.coverBase64ToBytes(e.getAttribute("basedata"));
                    }
                } catch (Exception ex) {
                    log.warn("SoundNode: {} Error: {}", name, ex.getMessage());
                }
                
                yield new WzSoundProperty(name, length, header, mp3, parent, wzImage);
            }
            
            case "null" -> new WzNullProperty(name, parent, wzImage);
            
            default -> {
                log.error("未知节点类型: {}", e.getNodeName());
                yield null;
            }
        };
    }

    /**
     * 读取子元素
     */
    private static void readChildren(Element parentXml, WzImageProperty parentProp, WzImage wzImage, String mediaFileName) {
        NodeList children = parentXml.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element) {
                WzImageProperty child = readProperty(element, parentProp, wzImage, mediaFileName);
                if (child != null) {
                    parentProp.addChild(child, true);
                }
            }
        }
    }

    /**
     * 检查字符串是否看起来像是我们导出的 XML
     */
    public static boolean isPropertyXml(String content) {
        if (content == null || content.isBlank()) return false;
        
        String trimmed = content.trim();
        return trimmed.startsWith("<?xml") && trimmed.contains("<root>") && 
               (trimmed.contains("<canvas") || trimmed.contains("<imgdir") || 
                trimmed.contains("<int") || trimmed.contains("<short") ||
                trimmed.contains("<long") || trimmed.contains("<float") ||
                trimmed.contains("<double") || trimmed.contains("<string") ||
                trimmed.contains("<uol") || trimmed.contains("<vector") ||
                trimmed.contains("<sound") || trimmed.contains("<null") ||
                trimmed.contains("<extended"));
    }
}
