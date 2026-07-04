package orange.wz.provider.tools;

import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.*;

import java.nio.charset.StandardCharsets;

/**
 * 解析 MS / WZ 中的文本 Property 格式（{@code #Property}），参考 WzComparerR2 TextImageReaderV1。
 */
public final class TextImagePropertyReader {
    private static final byte[] SIGNATURE_V1 = "#Property".getBytes(StandardCharsets.US_ASCII);

    private TextImagePropertyReader() {
    }

    public static boolean isTextPropertyV1(byte[] data) {
        if (data == null || data.length < SIGNATURE_V1.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE_V1.length; i++) {
            if (data[i] != SIGNATURE_V1[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean isTextPropertyV1(BinaryReader reader) {
        int pos = reader.getPosition();
        try {
            for (byte sig : SIGNATURE_V1) {
                if (!reader.hasRemaining() || reader.getByte() != sig) {
                    return false;
                }
            }
            return true;
        } finally {
            reader.setPosition(pos);
        }
    }

    public static void parseV1(byte[] data, WzImage image) {
        parseV1(new String(data, StandardCharsets.UTF_8), image);
    }

    public static void parseV1(String text, WzImage image) {
        TextReader reader = new TextReader(text);
        reader.skipLine();
        readProperty(reader, image, image, true);
    }

    private static void readProperty(TextReader reader, WzObject parent, WzImage image, boolean isTopLevel) {
        while (!reader.isEnd()) {
            reader.skipWhitespaceExceptLineEnding();
            String key = reader.readUntilWhitespace();
            if (key.isEmpty()) {
                reader.skipLine();
                continue;
            }
            if ("}".equals(key) && !isTopLevel) {
                if (!reader.skipLineAndCheckEmpty()) {
                    throw new RuntimeException("Incorrect property end line.");
                }
                return;
            }

            reader.skipWhitespaceExceptLineEnding();
            int equalSign = reader.read();
            if (equalSign != '=') {
                throw new RuntimeException("Expect '=' sign but got '" + (char) equalSign + "'.");
            }
            reader.skipWhitespaceExceptLineEnding();
            String stringVal = readValueAfterEquals(reader);

            if (stringVal.isEmpty()) {
                addChild(parent, new WzListProperty(key, parent, image), image);
            } else if ("{".equals(stringVal)) {
                WzListProperty child = new WzListProperty(key, parent, image);
                addChild(parent, child, image);
                readProperty(reader, child, image, false);
            } else {
                addChild(parent, createValueProperty(key, stringVal, parent, image), image);
            }
        }
    }

    private static String readValueAfterEquals(TextReader reader) {
        reader.skipWhitespaceExceptLineEnding();
        if (reader.peek() == '{') {
            reader.read();
            reader.skipRestOfLine();
            return "{";
        }
        String line = reader.readLine();
        if (line == null) {
            return "";
        }
        line = line.stripLeading();
        if (line.isEmpty()) {
            reader.skipWhitespaceExceptLineEnding();
            if (reader.peek() == '{') {
                reader.read();
                reader.skipRestOfLine();
                return "{";
            }
            return "";
        }
        if (line.startsWith("{")) {
            return "{";
        }
        return line;
    }

    private static WzImageProperty createValueProperty(String key, String stringVal, WzObject parent, WzImage image) {
        try {
            return new WzIntProperty(key, Integer.parseInt(stringVal), parent, image);
        } catch (NumberFormatException ignored) {
        }
        try {
            return new WzLongProperty(key, Long.parseLong(stringVal), parent, image);
        } catch (NumberFormatException ignored) {
        }
        try {
            return new WzDoubleProperty(key, Double.parseDouble(stringVal), parent, image);
        } catch (NumberFormatException ignored) {
        }
        return new WzStringProperty(key, stringVal, parent, image);
    }

    private static void addChild(WzObject parent, WzImageProperty child, WzImage image) {
        if (parent instanceof WzImage wzImage) {
            wzImage.addChild(child, true);
        } else if (parent instanceof WzListProperty list) {
            list.addChild(child, true);
        } else {
            throw new RuntimeException("Unexpected parent type: " + parent.getClass().getSimpleName());
        }
    }

    private static final class TextReader {
        private final String text;
        private int pos;

        TextReader(String text) {
            this.text = text == null ? "" : text;
        }

        boolean isEnd() {
            return pos >= text.length();
        }

        int read() {
            if (isEnd()) {
                return -1;
            }
            return text.charAt(pos++);
        }

        int peek() {
            if (isEnd()) {
                return -1;
            }
            return text.charAt(pos);
        }

        void skipLine() {
            skipRestOfLine();
        }

        void skipRestOfLine() {
            while (!isEnd()) {
                int c = read();
                if (c == '\n') {
                    break;
                }
            }
        }

        boolean skipLineAndCheckEmpty() {
            boolean allWhitespace = true;
            while (!isEnd()) {
                int c = read();
                if (c == '\n') {
                    break;
                }
                if (!Character.isWhitespace(c)) {
                    allWhitespace = false;
                }
            }
            return allWhitespace;
        }

        void skipWhitespaceExceptLineEnding() {
            while (!isEnd()) {
                int c = peek();
                if (c == '\n' || !Character.isWhitespace(c)) {
                    break;
                }
                read();
            }
        }

        String readUntilWhitespace() {
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                int c = peek();
                if (Character.isWhitespace(c)) {
                    break;
                }
                sb.append((char) read());
            }
            return sb.toString();
        }

        String readLine() {
            if (isEnd()) {
                return null;
            }
            int start = pos;
            while (!isEnd() && peek() != '\n') {
                read();
            }
            if (!isEnd() && peek() == '\n') {
                read();
            }
            return text.substring(start, pos).stripTrailing();
        }
    }
}
