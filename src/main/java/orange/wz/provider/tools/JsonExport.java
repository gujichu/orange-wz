package orange.wz.provider.tools;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.properties.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按 TMS273 {@code WZ_JSON_TW} 风格导出 JSON。
 * <p>
 * 服务端 JSON 不含客户端图片/动画资源，需过滤 canvas、{@code _outlink}、
 * {@code _Canvas} 外链及 canvas 动画帧上的 vector {@code origin} 等。
 */
@Slf4j
public final class JsonExport {
    private static final Pattern FRAME_INDEX = Pattern.compile("^\\d{1,3}$");
    private static final Pattern EFFECT_CONTAINER = Pattern.compile("^effect\\d*$", Pattern.CASE_INSENSITIVE);

    /** 过滤后为空 sub 时直接省略的纯 UI 图标槽位 */
    private static final Set<String> OMIT_WHEN_EMPTY = Set.of(
            "icon", "iconMouseOver", "iconDisabled"
    );

    private final WzImage image;
    private final int indent;

    public JsonExport(WzImage image, int indent) {
        this.image = image;
        this.indent = Math.max(0, indent);
    }

    public static String resolveJsonFileName(String imgName) {
        if (imgName == null || imgName.isEmpty()) {
            return ".json";
        }
        if (imgName.endsWith(".img")) {
            return imgName.substring(0, imgName.length() - 4) + ".json";
        }
        if (imgName.endsWith(".xml")) {
            return imgName.substring(0, imgName.length() - 4) + ".json";
        }
        return imgName + ".json";
    }

    public static String resolveImageBaseName(String imgName) {
        if (imgName == null || imgName.isEmpty()) {
            return imgName;
        }
        if (imgName.endsWith(".img")) {
            return imgName.substring(0, imgName.length() - 4);
        }
        if (imgName.endsWith(".xml")) {
            return imgName.substring(0, imgName.length() - 4);
        }
        return imgName;
    }

    /**
     * 导出根目录名：去掉 {@code .wz}/{@code .ms} 后缀，并截断第一个 {@code _} 及其后内容。
     * 例如 {@code Skill_00000.ms}、{@code Skill_00001.wz} → {@code Skill}。
     */
    public static String resolveExportRootFolderName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String base = name.replaceAll("(?i)\\.(wz|ms)$", "");
        int underscore = base.indexOf('_');
        if (underscore >= 0) {
            base = base.substring(0, underscore);
        }
        return base.isEmpty() ? name.replaceAll("(?i)\\.(wz|ms)$", "") : base;
    }

    public boolean export(Path filePath) {
        try {
            Map<String, Object> root = buildImageRoot();
            String json = toJson(root);
            return FileTool.saveFile(filePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("JSON 导出失败 {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }

    String toJson(Map<String, Object> root) {
        StringBuilder sb = new StringBuilder(4096);
        writeValue(sb, root, 0);
        if (indent > 0) {
            sb.append('\n');
        }
        return sb.toString();
    }

    Map<String, Object> buildImageRootForTest() {
        return buildImageRoot();
    }

    private Map<String, Object> buildImageRoot() {
        List<WzImageProperty> children = image.getChildren();
        Map<String, Object> root = new LinkedHashMap<>();
        if (children == null || children.isEmpty()) {
            root.put("_dirType", "sub");
            return root;
        }
        for (WzImageProperty prop : children) {
            Map<String, Object> converted = convertProperty(prop, null, null, isSpineResourceNode(prop.getName()));
            if (converted != null) {
                root.put(prop.getName(), converted);
            }
        }
        return root;
    }

    private Map<String, Object> convertProperty(WzImageProperty property, String parentNodeName, String containerNodeName,
                                                boolean insideSpineResource) {
        if (shouldSkipProperty(property, parentNodeName, containerNodeName)) {
            return null;
        }

        Map<String, Object> node = switch (property) {
            case WzListProperty prop -> buildSubNode(prop.getChildren(), prop.getName(), parentNodeName, insideSpineResource);
            case WzStringProperty prop -> valueNode("string", prop.getValue());
            case WzIntProperty prop -> valueNode("int", String.valueOf(prop.getValue()));
            case WzShortProperty prop -> valueNode("short", String.valueOf(prop.getValue()));
            case WzLongProperty prop -> valueNode("long", String.valueOf(prop.getValue()));
            case WzFloatProperty prop -> valueNode("float", formatFloat(prop.getValue()));
            case WzDoubleProperty prop -> valueNode("double", formatDouble(prop.getValue()));
            case WzUOLProperty prop -> valueNode("uol", prop.getValue());
            case WzVectorProperty prop -> {
                Map<String, Object> vector = new LinkedHashMap<>();
                vector.put("_dirType", "vector");
                vector.put("_x", prop.getX());
                vector.put("_y", prop.getY());
                yield vector;
            }
            case WzNullProperty ignored -> {
                Map<String, Object> nullNode = new LinkedHashMap<>();
                nullNode.put("_dirType", "null");
                yield nullNode;
            }
            case WzSoundProperty prop -> {
                Map<String, Object> sound = new LinkedHashMap<>();
                sound.put("_dirType", "sound");
                sound.put("_length", resolveSoundLength(prop, insideSpineResource));
                yield sound;
            }
            case WzVideoProperty prop -> {
                Map<String, Object> video = new LinkedHashMap<>();
                video.put("_dirType", "wz_video");
                video.put("_value", "WzComparerR2.WzLib.Wz_Video");
                appendChildren(video, prop.getChildren(), prop.getName(), parentNodeName, insideSpineResource);
                yield video;
            }
            case WzLuaProperty prop -> valueNode("string", prop.getString());
            case null, default -> {
                log.error("未知的节点类型: {}", property != null ? property.getName() : "null");
                Map<String, Object> unknown = new LinkedHashMap<>();
                unknown.put("_dirType", "sub");
                yield unknown;
            }
        };

        return finalizeSubNode(node, property.getName(), parentNodeName);
    }

    private Map<String, Object> buildSubNode(List<WzImageProperty> children, String nodeName) {
        return buildSubNode(children, nodeName, null, isSpineResourceNode(nodeName));
    }

    private Map<String, Object> buildSubNode(List<WzImageProperty> children, String nodeName, String parentNodeName,
                                             boolean insideSpineResource) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("_dirType", "sub");
        boolean childInsideSpineResource = insideSpineResource || isSpineResourceNode(nodeName);
        appendChildren(node, children, nodeName, parentNodeName, childInsideSpineResource);
        return finalizeSubNode(node, nodeName, parentNodeName);
    }

    private void appendChildren(Map<String, Object> node, List<WzImageProperty> children, String nodeName,
                                String parentNodeName, boolean insideSpineResource) {
        if (children == null) {
            return;
        }
        for (WzImageProperty child : children) {
            Map<String, Object> converted = convertProperty(child, nodeName, parentNodeName, insideSpineResource);
            if (converted != null) {
                node.put(child.getName(), converted);
            }
        }
    }

    private Map<String, Object> finalizeSubNode(Map<String, Object> node, String nodeName) {
        return finalizeSubNode(node, nodeName, null);
    }

    private Map<String, Object> finalizeSubNode(Map<String, Object> node, String nodeName, String parentNodeName) {
        if (node == null) {
            return null;
        }
        if (node.size() > 1) {
            return node;
        }
        if (OMIT_WHEN_EMPTY.contains(nodeName)) {
            return null;
        }
        return node;
    }

    private boolean shouldSkipProperty(WzImageProperty property, String parentNodeName, String containerNodeName) {
        if (property == null) {
            return true;
        }
        if ("_outlink".equals(property.getName())) {
            return true;
        }
        if (isVisualOrigin(property, parentNodeName, containerNodeName)) {
            return true;
        }
        if ("delay".equals(property.getName()) && isVisualAnimationFrame(parentNodeName, containerNodeName)) {
            return true;
        }
        if (isZeroZInEffectAnimationFrame(property, parentNodeName, containerNodeName)) {
            return true;
        }
        return switch (property) {
            case WzCanvasProperty ignored -> true;
            case WzConvexProperty ignored -> true;
            case WzRawDataProperty ignored -> true;
            case WzUOLProperty prop -> isCanvasResourceLink(prop.getValue());
            case WzStringProperty prop -> isCanvasResourceLink(prop.getValue()) && "_outlink".equals(prop.getName());
            default -> false;
        };
    }

    private boolean isVisualOrigin(WzImageProperty property, String parentNodeName, String containerNodeName) {
        return property instanceof WzVectorProperty
                && "origin".equals(property.getName())
                && (isVisualAnimationFrame(parentNodeName, containerNodeName)
                || isIconResourceSlot(parentNodeName));
    }

    private boolean isVisualAnimationFrame(String parentNodeName, String containerNodeName) {
        return parentNodeName != null && FRAME_INDEX.matcher(parentNodeName).matches()
                && containerNodeName != null && EFFECT_CONTAINER.matcher(containerNodeName).matches();
    }

    private boolean isIconResourceSlot(String parentNodeName) {
        return parentNodeName != null && OMIT_WHEN_EMPTY.contains(parentNodeName);
    }

    private boolean isZeroZInEffectAnimationFrame(WzImageProperty property, String parentNodeName, String containerNodeName) {
        if (!"z".equals(property.getName()) || !isVisualAnimationFrame(parentNodeName, containerNodeName)) {
            return false;
        }
        if (property instanceof WzIntProperty intProp) {
            return intProp.getValue() == 0;
        }
        if (property instanceof WzShortProperty shortProp) {
            return shortProp.getValue() == 0;
        }
        return false;
    }

    private static boolean isSpineResourceNode(String nodeName) {
        return nodeName != null && nodeName.toLowerCase().contains("spine");
    }

    private static String resolveSoundLength(WzSoundProperty prop, boolean insideSpineResource) {
        if (!insideSpineResource) {
            return String.valueOf(prop.getLenMs());
        }
        byte[] bytes = prop.getSoundBytes(false);
        return String.valueOf(bytes != null ? bytes.length : prop.getLenMs());
    }

    static boolean isCanvasResourceLink(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.contains("_Canvas/") || value.contains("_Canvas\\");
    }

    private Map<String, Object> valueNode(String dirType, String value) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("_dirType", dirType);
        node.put("_value", value != null ? value : "");
        return node;
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        String text = Float.toString(value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        String text = Double.toString(value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private void writeValue(StringBuilder sb, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            writeObject(sb, (Map<String, Object>) map, depth);
        } else if (value instanceof Number number) {
            sb.append(number);
        } else if (value instanceof Boolean bool) {
            sb.append(bool);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private void writeObject(StringBuilder sb, Map<String, Object> map, int depth) {
        sb.append('{');
        if (map.isEmpty()) {
            sb.append('}');
            return;
        }

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
                if (indent > 0) {
                    sb.append('\n');
                }
            } else {
                first = false;
                if (indent > 0) {
                    sb.append('\n');
                }
            }

            if (indent > 0) {
                sb.append(" ".repeat(indent * (depth + 1)));
            }

            writeString(sb, entry.getKey());
            sb.append(indent > 0 ? ": " : ":");
            writeValue(sb, entry.getValue(), depth + 1);
        }

        if (indent > 0) {
            sb.append('\n');
            sb.append(" ".repeat(indent * depth));
        }
        sb.append('}');
    }

    private void writeString(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
