package orange.wz.gui.component.form.data;

import lombok.Getter;

@Getter
public class BatchDeleteNodeFormData extends NodeFormData {
    private final boolean deleteOdd;
    private final boolean deleteEven;

    public BatchDeleteNodeFormData(String name, boolean deleteOdd, boolean deleteEven) {
        super(name, "List");
        this.deleteOdd = deleteOdd;
        this.deleteEven = deleteEven;
    }

    public boolean isParityMode() {
        return deleteOdd || deleteEven;
    }
}
