package orange.wz.gui.component.form.impl;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.form.data.StringFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzVideoProperty;

import javax.swing.*;

@Slf4j
public class UolVideoForm extends VideoForm {
    protected final JTextArea valueInput = new JTextArea(1, defaultColumns);

    public UolVideoForm() {
        super();
        addRow("UOL:", valueInput);
    }

    public void setData(String name, String type, String value, WzVideoProperty video, WzObject wzObject, EditPane editPane) {
        valueInput.setText(value);
        byte[] bytes;
        if (video == null) {
            log.warn("UOL 指向的视频节点为空");
            bytes = new byte[0];
        } else {
            bytes = video.getBytes(false);
        }
        applyVideoPayload(name, type, bytes, wzObject, editPane);
    }

    public StringFormData getUolData() {
        return new StringFormData(
                nameInput.getText(),
                typeInput.getText(),
                valueInput.getText()
        );
    }
}
