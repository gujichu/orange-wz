package orange.wz.gui.component.imageeditor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

final class ImageEditorPrefs {

    private static final int PALETTE_VERSION = 2;
    private static final int MIN_PALETTE_COLORS = 20;

    private static final Preferences PREFS = Preferences.userNodeForPackage(ImageEditorPrefs.class);

    private static final Color[] DEFAULT_PALETTE = {
            Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.PINK,
            new Color(0x8B4513), new Color(0x808080), new Color(0x404040),
            new Color(0x006400), new Color(0x000080), new Color(0x800080),
            new Color(0xFFD700), new Color(0x00CED1), new Color(0xFF6347), new Color(0x9370DB),
            new Color(0x2E8B57), new Color(0xDC143C), new Color(0x4169E1), new Color(0xFF1493),
            new Color(0x00FA9A), new Color(0xFF4500), new Color(0x1E90FF), new Color(0xADFF2F),
            new Color(0xBA55D3), new Color(0xF0E68C), new Color(0x20B2AA), new Color(0xCD853F),
            new Color(0x778899), new Color(0xB22222), new Color(0x191970), new Color(0x556B2F),
            new Color(0xDDA0DD), new Color(0xF5F5DC), new Color(0xA0522D), new Color(0x708090),
            new Color(0xEEE8AA), new Color(0x98FB98), new Color(0xAFEEEE), new Color(0xD8BFD8)
    };

    int brushSize = 4;
    int fillTolerance = 32;
    int magicWandTolerance = 32;
    int mosaicBlockSize = 8;
    String saveFormat = "png";
    int quality = 90;
    Color foregroundColor = Color.BLACK;
    List<Color> palette = new ArrayList<>();
    int selectedPaletteIndex = 0;

    ImageEditorPrefs() {
        load();
    }

    void load() {
        brushSize = PREFS.getInt("brushSize", 4);
        fillTolerance = PREFS.getInt("fillTolerance", 32);
        magicWandTolerance = PREFS.getInt("magicWandTolerance", 32);
        mosaicBlockSize = PREFS.getInt("mosaicBlockSize", 8);
        saveFormat = PREFS.get("saveFormat", "png");
        quality = PREFS.getInt("quality", 90);
        foregroundColor = parseColor(PREFS.get("foregroundColor", "#000000"), Color.BLACK);
        selectedPaletteIndex = PREFS.getInt("selectedPaletteIndex", 0);

        palette.clear();
        int savedVersion = PREFS.getInt("paletteVersion", 0);
        String raw = PREFS.get("palette", null);
        if (raw == null || raw.isBlank()) {
            palette.addAll(List.of(DEFAULT_PALETTE));
        } else {
            for (String part : raw.split(";")) {
                if (!part.isBlank()) {
                    palette.add(parseColor(part.trim(), Color.GRAY));
                }
            }
        }
        if (palette.isEmpty() || savedVersion < PALETTE_VERSION || palette.size() < MIN_PALETTE_COLORS) {
            palette.clear();
            palette.addAll(List.of(DEFAULT_PALETTE));
            selectedPaletteIndex = 0;
        }
        selectedPaletteIndex = Math.max(0, Math.min(selectedPaletteIndex, palette.size() - 1));
    }

    void save() {
        PREFS.putInt("brushSize", brushSize);
        PREFS.putInt("fillTolerance", fillTolerance);
        PREFS.putInt("magicWandTolerance", magicWandTolerance);
        PREFS.putInt("mosaicBlockSize", mosaicBlockSize);
        PREFS.put("saveFormat", saveFormat);
        PREFS.putInt("quality", quality);
        PREFS.put("foregroundColor", colorToHex(foregroundColor));
        PREFS.putInt("selectedPaletteIndex", selectedPaletteIndex);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < palette.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(colorToHex(palette.get(i)));
        }
        PREFS.put("palette", sb.toString());
        PREFS.putInt("paletteVersion", PALETTE_VERSION);
    }

    static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
