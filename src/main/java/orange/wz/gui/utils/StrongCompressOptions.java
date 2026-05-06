package orange.wz.gui.utils;

import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzPngZlibCompressMode;

import java.util.Objects;

/**
 * 节点下批量「强力压缩」参数（目标格式、ZLIB、压缩算法模式、块缩放、抖动与跳过策略）。
 */
public record StrongCompressOptions(
        WzPngFormat targetFormat,
        int zlibLevel,
        WzPngZlibCompressMode zlibMode,
        int pngScale,
        boolean floydSteinbergDither,
        boolean skipIfNotSmaller
) {
    public StrongCompressOptions {
        zlibLevel = Math.max(1, Math.min(9, zlibLevel));
        pngScale = Math.max(0, Math.min(2, pngScale));
        zlibMode = Objects.requireNonNullElse(zlibMode, WzPngZlibCompressMode.DEFAULT);
    }
}
