package orange.wz.gui.utils;

/**
 * 强力压缩前是否对像素做 libimagequant 调色板量化（DXT/BC7 等会先解码为 ARGB 再进入此路径）。
 */
public enum StrongCompressQuantMode {
    /** 不调 libimagequant，仅按现有格式 + ZLIB 策略处理 */
    NONE("标准（不调色板）"),
    /** 解码 → ARGB8888 → libimagequant → 再按「目标格式」写回 WZ（含 zlib） */
    LIBIMAGEQUANT("libimagequant（调色板量化）");

    private final String displayLabel;

    StrongCompressQuantMode(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
