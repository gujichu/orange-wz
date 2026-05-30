package orange.wz.gui.component.imageeditor;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

@Getter
public final class ImageEditorLayer {

    private final String name;
    @Setter
    private boolean visible;
    @Setter
    private int offsetX;
    @Setter
    private int offsetY;
    private BufferedImage content;

    public ImageEditorLayer(String name, BufferedImage content, boolean visible) {
        this.name = name;
        this.content = ImageEditorUtil.toArgb(content);
        this.visible = visible;
    }

    void setContent(BufferedImage content) {
        this.content = ImageEditorUtil.toArgb(content);
    }
}
