package com.vv.testrike.comparisonchain;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class GenerateDialog extends DialogWrapper {
    private final LabeledComponent<JPanel> component;
    private final JBList<PsiField> fieldList;

    GenerateDialog(PsiClass psiClass) {
        super(psiClass.getProject());
        setTitle("Select Fields for Comparision Chain");

        CollectionListModel<PsiField> fields = new CollectionListModel<PsiField>(psiClass.getAllFields());
        fieldList = new JBList<>(fields);
        fieldList.setCellRenderer(new DefaultPsiElementCellRenderer());
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(fieldList);
        decorator.disableAddAction();
        decorator.disableRemoveAction();
        JPanel panel = decorator.createPanel();
        component = LabeledComponent.create(panel, "Fields to include in compareTo():");

        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return component;
    }

    public List<PsiField> getFields() {
        return fieldList.getSelectedValuesList();
    }
}
