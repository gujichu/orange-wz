package orange.wz.gui.video;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class McvHeader {
    private int headerLength;
    private int fourCc;
    private int width;
    private int height;
    private int frameCount;
    private int dataFlags;
    private long frameDelayUnit;
    private int defaultDelay;
    private McvFrameInfo[] frames;
}
