package orange.wz.gui.video;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class McvFrameInfo {
    private long dataOffset;
    private int dataCount;
    private long alphaDataOffset = -1;
    private int alphaDataCount;
    private long delayNanoseconds;
    private long startTimeNanoseconds;
}
