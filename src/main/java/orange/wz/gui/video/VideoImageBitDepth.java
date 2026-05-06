package orange.wz.gui.video;

/**
 * 导出图片时的像素位深（封装在 PNG/JPG 仍为 8 位通道；ARGB4444 为每通道 4 位量化后再展开存储）。
 */
public enum VideoImageBitDepth {
    ARGB8888,
    ARGB4444
}
