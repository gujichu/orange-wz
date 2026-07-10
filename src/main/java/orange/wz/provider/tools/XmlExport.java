package orange.wz.provider.tools;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.form.data.ExportXmlData.ExportVersion;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzXmlFile;
import orange.wz.provider.properties.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
public final class XmlExport {
    private final WzImage image;
    private final int indent;
    private final boolean linux;
    private final MediaExportType meType;
    private final ExportVersion version;

    private BufferedWriter writer;
    private Path mediaFolder;
    private int curIndent = 0;

    public XmlExport(WzImage image, int indent, boolean linux, MediaExportType meType, ExportVersion version) {
        this.image = image;
        this.indent = indent;
        this.linux = linux;
        this.meType = meType;
        this.version = version;
    }

    private boolean isLegacyStyle() {
        return version == ExportVersion.V125 || version == ExportVersion.GMS265;
    }

    private boolean isGms265Style() {
        return version == ExportVersion.GMS265;
    }

    private int effectiveIndent() {
        return isGms265Style() ? 4 : indent;
    }

    private String selfClose() {
        return isGms265Style() ? " />" : "/>";
    }

    private void writeLineSeparator() throws IOException {
        if (effectiveIndent() <= 0) return;
        writer.write(linux ? "\n" : "\r\n");
    }

    private void writeIndent() throws IOException {
        int effectiveIndent = effectiveIndent();
        if (effectiveIndent <= 0 || curIndent <= 0) return;
        int spaces = effectiveIndent * curIndent;
        char[] buffer = new char[spaces];
        Arrays.fill(buffer, ' ');
        writer.write(buffer);
    }

    public boolean export(Path filePath) {
        String imgName = image instanceof WzXmlFile xml ? xml.getImgName() : image.getName();
        mediaFolder = filePath.getParent().resolve("media").resolve(imgName);
        if (meType == MediaExportType.FILE) {
            try {
                FileTool.deleteDirectory(mediaFolder);
                FileTool.createDirectory(mediaFolder);
            } catch (Exception e) {
                log.error("media 目录被占用: {} {}", mediaFolder, e.getMessage());
                return false;
            }
        }

        try (FileOutputStream fos = new FileOutputStream(filePath.toString())) {

            writer = new BufferedWriter(
                    new OutputStreamWriter(fos, StandardCharsets.UTF_8),
                    64 * 1024 // 64KB buffer（可以调大）
            );

            writer.write(isGms265Style()
                    ? "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>"
                    : "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            writeLineSeparator();

            String rootTag;
            if (isLegacyStyle()) {
                rootTag = "<imgdir name=\"" + imgName + "\">";
            } else {
                rootTag = "<imgdir name=\"" + imgName + "\" indent=\"" + indent + "\" media=\"" + meType.name() + "\">";
            }
            writer.write(rootTag);
            writeLineSeparator();
            curIndent++;
            image.getChildren().forEach(prop -> writeProp(prop, ""));
            curIndent--;
            writer.write("</imgdir>");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private void writeProp(WzImageProperty property, String mediaFilename) {
        try {
            writeIndent();
            switch (property) {
                case WzCanvasProperty prop -> {
                    String etName = escapeText(prop.getName());
                    String width = String.valueOf(prop.getWidth());
                    String height = String.valueOf(prop.getHeight());
                    String context;
                    if (isLegacyStyle()) {
                        context = "<canvas name=\"" + etName + "\" width=\"" + width + "\" height=\"" + height + "\"";
                    } else {
                        String format = String.valueOf(prop.getFormat().getValue());
                        String scale = String.valueOf(prop.getScale());
                        context = "<canvas name=\"" + etName + "\" width=\"" + width + "\" height=\"" + height + "\" format=\"" + format + "\" scale=\"" + scale + "\"";
                    }

                    if (meType == MediaExportType.BASE64)
                        context = context + " basedata=\"" + Base64Tool.coverBytesToBase64(prop.getImageBytes(false)) + "\"";
                    else if (meType == MediaExportType.FILE) {
                        String filename = FileTool.safeFileName(mediaFilename + prop.getName() + ".png");
                        Path p = mediaFolder.resolve(filename);
                        FileTool.saveFile(p, prop.getImageBytes(false));
                    }

                    List<WzImageProperty> children = prop.getChildren();
                    if (children.isEmpty()) {
                        context += selfClose();
                        writer.write(context);
                        writeLineSeparator();
                    } else {
                        context += ">";
                        writer.write(context);
                        writeLineSeparator();
                        curIndent++;
                        children.forEach(subProperty -> writeProp(subProperty, mediaFilename + prop.getName() + "."));
                        curIndent--;
                        writeIndent();
                        writer.write("</canvas>");
                        writeLineSeparator();
                    }
                }
                case WzConvexProperty prop -> {
                    writer.write("<extended name=\"" + escapeText(prop.getName()) + "\"");
                    List<WzImageProperty> children = prop.getChildren();
                    if (children.isEmpty()) {
                        writer.write(selfClose());
                        writeLineSeparator();
                    } else {
                        writer.write(">");
                        writeLineSeparator();
                        curIndent++;
                        children.forEach(subProperty -> writeProp(subProperty, mediaFilename + prop.getName() + "."));
                        curIndent--;
                        writeIndent();
                        writer.write("</extended>");
                        writeLineSeparator();
                    }
                }
                case WzDoubleProperty prop -> {
                    writer.write("<double name=\"" + escapeText(prop.getName()) + "\" value=\"" + prop.getValue() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzFloatProperty prop -> {
                    writer.write("<float name=\"" + escapeText(prop.getName()) + "\" value=\"" + prop.getValue() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzIntProperty prop -> {
                    writer.write("<int name=\"" + escapeText(prop.getName()) + "\" value=\"" + prop.getValue() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzListProperty prop -> {
                    writer.write("<imgdir name=\"" + escapeText(prop.getName()) + "\"");
                    List<WzImageProperty> children = prop.getChildren();
                    if (children.isEmpty()) {
                        if (isLegacyStyle()) {
                            writer.write("></imgdir>");
                        } else {
                            writer.write(selfClose());
                        }
                        writeLineSeparator();
                    } else {
                        writer.write(">");
                        writeLineSeparator();
                        curIndent++;
                        children.forEach(subProperty -> writeProp(subProperty, mediaFilename + prop.getName() + "."));
                        curIndent--;
                        writeIndent();
                        writer.write("</imgdir>");
                        writeLineSeparator();
                    }
                }
                case WzLongProperty prop -> {
                    writer.write("<long name=\"" + escapeText(prop.getName()) + "\" value=\"" + prop.getValue() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzNullProperty prop -> {
                    writer.write("<null name=\"" + escapeText(prop.getName()) + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzShortProperty prop -> {
                    writer.write("<short name=\"" + escapeText(prop.getName()) + "\" value=\"" + prop.getValue() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzSoundProperty prop -> {
                    String context = "<sound name=\"" + escapeText(prop.getName()) + "\"";

                    if (meType == MediaExportType.BASE64) {
                        String basehead = Base64Tool.coverBytesToBase64(prop.getHeader());
                        String basedata = Base64Tool.coverBytesToBase64(prop.getSoundBytes(false));
                        context = context + " length=\"" + prop.getLenMs() + "\" basehead=\"" + basehead + "\" basedata=\"" + basedata + "\"" + selfClose();
                    } else if (meType == MediaExportType.FILE) {
                        String basehead = Base64Tool.coverBytesToBase64(prop.getHeader());
                        context = context + " length=\"" + prop.getLenMs() + "\" basehead=\"" + basehead + "\"" + selfClose();

                        String filename = FileTool.safeFileName(mediaFilename + prop.getName() + ".mp3");
                        Path p = mediaFolder.resolve(filename);
                        FileTool.saveFile(p, prop.getSoundBytes(false));
                    } else {
                        context = context + selfClose();
                    }

                    writer.write(context);
                    writeLineSeparator();
                }
                case WzStringProperty prop -> {
                    writer.write("<string name=\"" + escapeText(prop.getName()) + "\" value=\"" + escapeText(prop.getValue()) + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzUOLProperty prop -> {
                    writer.write("<uol name=\"" + escapeText(prop.getName()) + "\" value=\"" + escapeText(prop.getValue()) + "\"" + selfClose());
                    writeLineSeparator();
                }
                case WzVectorProperty prop -> {
                    writer.write("<vector name=\"" + escapeText(prop.getName()) + "\" x=\"" + prop.getX() + "\" y=\"" + prop.getY() + "\"" + selfClose());
                    writeLineSeparator();
                }
                case null, default -> log.error("未知的节点类型: {}", property.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String escapeText(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length() + 10);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("&quot;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                default:
                    sb.append(c);
            }
        }

        return sb.toString();
    }

}
