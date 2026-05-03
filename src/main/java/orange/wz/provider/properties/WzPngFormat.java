package orange.wz.provider.properties;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum WzPngFormat {
    ARGB4444(1),
    ARGB8888(2),
    FORMAT3(3), // Harepacker: Format3
    ARGB1555(257),
    RGB565(513),
    FORMAT517(517), // Harepacker: Format517
    DXT3(1026),
    DXT5(2050),
    BC7(4098);

    private final int value;

    WzPngFormat(int value) {
        this.value = value;
    }

    public static WzPngFormat getByValue(int value) {
        for (WzPngFormat e : values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        log.warn("未知的图片压缩格式 {}", value);
        throw new RuntimeException("未知的图片压缩格式 " + value);
    }
}
