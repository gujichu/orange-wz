package orange.wz.gui.video;

/**
 * Canvas#Video 固定播放/导出时间轴常量与安全的数值计算。
 */
public final class VideoPlaybackConstants {

    /** 每帧时长（毫秒），与 FFmpeg 有理数帧率分子一致 */
    public static final int FRAME_INTERVAL_MS = 60;

    /**
     * FFmpeg {@code -framerate} 有理数表达式：每帧 {@link #FRAME_INTERVAL_MS} ms，避免 Java 中 {@code 1000/60} 整除得到 16 的错误。
     */
    public static final String EXPORT_FRAMERATE_EXPR = "1000/60";

    private VideoPlaybackConstants() {
    }

    /**
     * 与 {@link #EXPORT_FRAMERATE_EXPR} 等价的每秒帧数（双精度），仅用于展示或非 FFmpeg 场景。
     */
    public static double exportFramesPerSecondDouble() {
        if (FRAME_INTERVAL_MS <= 0) {
            throw new IllegalStateException("FRAME_INTERVAL_MS 必须为正数");
        }
        return 1000.0 / (double) FRAME_INTERVAL_MS;
    }

    /**
     * 将各帧时长（毫秒）求和，检测 long 溢出并饱和到 {@link Long#MAX_VALUE}，避免后续时间轴循环上界异常。
     */
    public static long sumDelayMillisSafe(int[] delayMillis) {
        if (delayMillis == null || delayMillis.length == 0) {
            return 0L;
        }
        long sum = 0L;
        for (int d : delayMillis) {
            int v = Math.max(0, d);
            long next = sum + v;
            if (next < sum) {
                return Long.MAX_VALUE;
            }
            sum = next;
        }
        return sum;
    }
}
