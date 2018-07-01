package com.vv.testrike.comparisonchain;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

public class GenerateAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiClass psiClass = getPsiClassFromContext(e);
        GenerateDialog dlg = new GenerateDialog(psiClass);
        dlg.show();
        if (dlg.isOK()) {
            if (dlg.getFields() != null && !dlg.getFields().isEmpty()) {
                generateComparable(psiClass, dlg.getFields());
            }
        }
    }

    public void generateComparable(PsiClass psiClass, List<PsiField> fields) {
        new WriteCommandAction.Simple(psiClass.getProject(), psiClass.getContainingFile()) {

            @Override
            protected void run() throws Throwable {
                generateCompareTo(psiClass, fields);
                generateImplementsComparable(psiClass);
            }
        }.execute();
    }

    private void generateImplementsComparable(PsiClass psiClass) {
        PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
        for (PsiClassType implementsListType : implementsListTypes) {
            PsiClass resolved = implementsListType.resolve();
            if (resolved != null && "java.lang.Comparable".equals(resolved.getQualifiedName())) {
                return;
            }
        }

        String implementsType = "Comparable<" + psiClass.getName() + ">";
        PsiJavaCodeReferenceElement referenceElement = JavaPsiFacade.getElementFactory(psiClass.getProject()).createReferenceFromText(implementsType, psiClass);
        psiClass.getImplementsList().add(referenceElement);
    }

    private void generateCompareTo(PsiClass psiClass, List<PsiField> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("public int compareTo(").append(psiClass.getName()).append(" that) {\n")
                .append("return com.google.common.collect.ComparisonChain.start()\n");
        for (PsiField field : fields) {
            builder.append(".compare(this.").append(field.getName()).append(", that.").append(field.getName()).append(")\n");
        }
        builder.append(".result();\n}");
        PsiMethod compareTo = JavaPsiFacade.getElementFactory(psiClass.getProject()).createMethodFromText(builder.toString(), psiClass);
        PsiElement method = psiClass.add(compareTo);
        JavaCodeStyleManager.getInstance(psiClass.getProject()).shortenClassReferences(method);
    }

    @Override
    public void update(AnActionEvent e) {
        PsiClass psiClass = getPsiClassFromContext(e);
        e.getPresentation().setEnabled(psiClass != null);
    }

    private PsiClass getPsiClassFromContext(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile ==null || editor == null ) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        PsiClass psiClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
        return psiClass;
    }
}
