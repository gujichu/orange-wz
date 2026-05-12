package orange.wz.gui.component.dialog;

import orange.wz.provider.properties.WzPngFormat;

import javax.swing.*;
import java.awt.Component;
import java.util.Enumeration;

/**
 * 「从视频生成同级 Canvas 节点」前选择目标图片压缩格式。
 */
public final class VideoGenerateFrameNodesFormatDialog {

    private VideoGenerateFrameNodesFormatDialog() {
    }

    /**
     * 与 {@link orange.wz.provider.properties.WzPngProperty} 中从 {@link java.awt.image.BufferedImage} 写回 raw 的分支一致。
     */
    private static final WzPngFormat[] CHOICES = {
            WzPngFormat.ARGB8888,
            WzPngFormat.DXT5,
            WzPngFormat.DXT3,
            WzPngFormat.BC7,
            WzPngFormat.ARGB4444,
            WzPngFormat.ARGB1555,
            WzPngFormat.RGB565,
    };

    public record Result(WzPngFormat format) {
    }

    /**
     * @return 用户确认后的格式；取消则 {@code null}
     */
    public static Result open(Component parent) {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel hint = new JLabel("请选择生成的图片格式（单选）：");
        body.add(hint);
        body.add(Box.createVerticalStrut(10));

        ButtonGroup group = new ButtonGroup();
        for (WzPngFormat f : CHOICES) {
            String text = switch (f) {
                case BC7 -> "BC7（当前编码实现会按 DXT5 写入）";
                default -> f.name();
            };
            JRadioButton rb = new JRadioButton(text);
            rb.setActionCommand(f.name());
            if (f == WzPngFormat.ARGB8888) {
                rb.setSelected(true);
            }
            group.add(rb);
            body.add(rb);
            body.add(Box.createVerticalStrut(2));
        }

        int option = JOptionPane.showConfirmDialog(
                parent,
                body,
                "生成子节点",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (option != JOptionPane.OK_OPTION) {
            return null;
        }
        WzPngFormat selected = null;
        for (Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements(); ) {
            AbstractButton b = e.nextElement();
            if (b.isSelected()) {
                selected = WzPngFormat.valueOf(b.getActionCommand());
                break;
            }
        }
        if (selected == null) {
            return null;
        }
        return new Result(selected);
    }
}
