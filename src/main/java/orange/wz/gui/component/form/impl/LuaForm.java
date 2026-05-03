package orange.wz.gui.component.form.impl;

import orange.wz.gui.component.form.data.StringFormData;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.WzObject;

import javax.swing.*;
import java.awt.*;

public class LuaForm extends AbstractValueForm {

    protected final JTextArea valueInput = new JTextArea();
    protected final JScrollPane scrollPane = new JScrollPane(valueInput);

    public LuaForm() {
        super();

        valueInput.setLineWrap(true);          // 自动换行
        valueInput.setWrapStyleWord(true);     // 按单词换行

        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        valuePane.add(scrollPane, BorderLayout.CENTER);
    }

    public void setData(String name, String type, String value, WzObject wzObject, EditPane editPane) {
        super.setData(name, type, wzObject, editPane);
        valueInput.setText(value);
        valueInput.setCaretPosition(0);
    }

    @Override
    protected boolean isPropertyMeaningLookupEnabled() {
        return true;
    }

    @Override
    public StringFormData getData() {
        return new StringFormData(
                nameInput.getText(),
                typeInput.getText(),
                valueInput.getText()
        );
    }
}