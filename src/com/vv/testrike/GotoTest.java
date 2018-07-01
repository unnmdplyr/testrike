package com.vv.testrike;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testIntegration.GotoTestOrCodeHandler;
import com.intellij.testIntegration.TestFinderHelper;
import org.jetbrains.annotations.NotNull;

import static com.intellij.icons.AllIcons.Hierarchy.MethodDefined;


public class GotoTest extends BaseCodeInsightAction {

    @NotNull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new GotoTestOrCodeHandler();
    }

    @Override
    public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabledAndVisible(false);
        if (TestFinderHelper.getFinders().length == 0) {
            return;
        }

        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || project == null) return;

        PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (psiFile == null) return;

        PsiElement element = GotoTestOrCodeHandler.getSelectedElement(editor, psiFile);

        if (TestFinderHelper.findSourceElement(element) == null) return;

        presentation.setEnabledAndVisible(true);
        if (TestFinderHelper.isTest(element)) {
            presentation.setText(ActionsBundle.message("action.GotoTestSubject.text"));
            presentation.setDescription(ActionsBundle.message("action.GotoTestSubject.description"));
        } else {
            presentation.setText(ActionsBundle.message("action.GotoTest.text"));
            presentation.setDescription(ActionsBundle.message("action.GotoTest.description"));
        }
    }
}
