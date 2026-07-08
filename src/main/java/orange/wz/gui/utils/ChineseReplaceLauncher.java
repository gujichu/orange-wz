package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.dialog.ChineseReplaceDialog;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.WzObject;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ChineseReplaceLauncher {

    private ChineseReplaceLauncher() {
    }

    public static void openFromSelection(Component parent, EditPane toPane, List<WzObject> toRoots) {
        if (toRoots == null || toRoots.isEmpty()) {
            return;
        }
        EditPane fromPane = MainFrame.getInstance().getCenterPane().getAnotherPane(toPane);
        List<ChineseReplaceEntry> entries = new ArrayList<>();
        int missing = 0;

        for (WzObject to : toRoots) {
            if (to == null) {
                continue;
            }
            WzObject from = fromPane.findWzObjectInTreeByPath(to.getPath());
            if (from == null) {
                missing++;
                log.error("汉化替换：找不到对照节点 {}", to.getPath());
                continue;
            }
            try {
                ChineseUtil.collectReplacements(from, to, entries);
            } catch (RuntimeException ex) {
                return;
            }
        }

        if (entries.isEmpty()) {
            String message = missing > 0
                    ? "未找到对照节点，或路径匹配下没有可替换的 String 属性。"
                    : "没有可替换的 String 属性（内容已相同或来源为空）。";
            JMessageUtil.info(parent, "汉化替换", message);
            return;
        }

        ChineseReplaceDialog.show(parent, entries, missing);
    }
}
