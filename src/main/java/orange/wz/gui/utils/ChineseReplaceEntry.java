package orange.wz.gui.utils;

import lombok.Getter;
import orange.wz.provider.properties.WzStringProperty;

import java.util.Objects;

@Getter
public final class ChineseReplaceEntry {

    public enum Status {
        PENDING("待替换"),
        APPLIED("已替换"),
        ROLLED_BACK("已回滚");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String path;
    private final WzStringProperty target;
    private final String before;
    private final String after;
    private Status status = Status.PENDING;

    public ChineseReplaceEntry(String path, WzStringProperty target, String before, String after) {
        this.path = path;
        this.target = target;
        this.before = before != null ? before : "";
        this.after = after;
    }

    public void apply() {
        if (status != Status.PENDING) {
            return;
        }
        target.setValue(after);
        target.setTempChanged(true);
        if (target.getWzImage() != null) {
            target.getWzImage().setChanged(true);
        }
        status = Status.APPLIED;
    }

    public void rollback() {
        if (status != Status.APPLIED) {
            return;
        }
        target.setValue(before);
        target.setTempChanged(true);
        if (target.getWzImage() != null) {
            target.getWzImage().setChanged(true);
        }
        status = Status.ROLLED_BACK;
    }

    public boolean canRollback() {
        return status == Status.APPLIED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public String operationLabel() {
        return switch (status) {
            case PENDING -> "待执行";
            case APPLIED -> "回滚";
            case ROLLED_BACK -> "—";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChineseReplaceEntry that)) {
            return false;
        }
        return Objects.equals(path, that.path) && target == that.target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, System.identityHashCode(target));
    }
}
