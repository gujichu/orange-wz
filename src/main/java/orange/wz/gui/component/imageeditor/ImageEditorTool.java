package orange.wz.gui.component.imageeditor;

public enum ImageEditorTool {
    PAN("拖动"),
    SELECT("选择"),
    MAGIC_WAND("魔棒"),
    EYEDROPPER("拾色器"),
    BRUSH("画笔"),
    ERASER("橡皮擦"),
    BUCKET("油漆桶");

    private final String label;

    ImageEditorTool(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
