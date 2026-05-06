package orange.wz.gui.utils;

import lombok.Data;

/**
 * 动画/图片预览面板选项（与 {@link AnimationPreviewConfigIni} 对应）。
 */
@Data
public class AnimationPreviewOptions {

    /** 预览缓存：勾选后 LRU 缓存多个节点的预览数据 */
    private boolean previewCacheEnabled = true;

    /** 非数字、非图标的节点名（如 walk、stand） */
    private boolean includeEnglishNamed = true;

    /** 纯数字节点名 */
    private boolean includeNumericNamed = false;

    /** icon / iconDisabled / iconMouseOver */
    private boolean includeIconNamed = false;

    public static AnimationPreviewOptions defaults() {
        return new AnimationPreviewOptions();
    }

    /**
     * 写入缓存键，使不同过滤条件不会命中错误缓存。
     */
    public String previewFilterCacheSuffix() {
        return "#pf"
                + (includeEnglishNamed ? '1' : '0')
                + (includeNumericNamed ? '1' : '0')
                + (includeIconNamed ? '1' : '0');
    }

    public AnimationPreviewOptions copy() {
        AnimationPreviewOptions c = new AnimationPreviewOptions();
        c.previewCacheEnabled = this.previewCacheEnabled;
        c.includeEnglishNamed = this.includeEnglishNamed;
        c.includeNumericNamed = this.includeNumericNamed;
        c.includeIconNamed = this.includeIconNamed;
        return c;
    }
}
