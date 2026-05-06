package orange.wz.provider.properties;

import java.util.zip.Deflater;

/**
 * WZ PNG 像素块 zlib 压缩时可选的策略；与 {@link Deflater#setStrategy(int)} 对应。
 * {@link #BRUTE_SMALLEST} 会对多种策略逐一遍历取最小 zlib 输出（再套 listWz 等封装），体积通常最好但更慢。
 */
public enum WzPngZlibCompressMode {
    /** 默认策略，适用面广 */
    DEFAULT(Deflater.DEFAULT_STRATEGY, false, "默认策略（均衡）"),
    /** 对渐变、平滑区域往往更省体积 */
    FILTERED(Deflater.FILTERED, false, "滤波优先（渐变图常更小）"),
    /** 部分随机/高熵数据可能更小 */
    HUFFMAN_ONLY(Deflater.HUFFMAN_ONLY, false, "仅哈夫曼"),
    /** zlib 策略值 3；部分 JDK 未提供 {@code Deflater.RLE} 字段 */
    RLE(3, false, "RLE（大块同色）"),
    /** 依次尝试 DEFAULT/FILTERED/HUFFMAN_ONLY/RLE，取 zlib 最小（最慢、通常最省） */
    BRUTE_SMALLEST(0, true, "极限体积（多策略择优，慢）");

    private final int deflaterStrategy;
    private final boolean brutePickSmallest;
    private final String displayLabel;

    WzPngZlibCompressMode(int deflaterStrategy, boolean brutePickSmallest, String displayLabel) {
        this.deflaterStrategy = deflaterStrategy;
        this.brutePickSmallest = brutePickSmallest;
        this.displayLabel = displayLabel;
    }

    public int deflaterStrategy() {
        return deflaterStrategy;
    }

    public boolean brutePickSmallest() {
        return brutePickSmallest;
    }

    public String displayLabel() {
        return displayLabel;
    }

    /** BRUTE 模式下参与比大小的策略集合 */
    static int[] strategiesForBrute() {
        return new int[]{
                Deflater.DEFAULT_STRATEGY,
                Deflater.FILTERED,
                Deflater.HUFFMAN_ONLY,
                3,
        };
    }
}
