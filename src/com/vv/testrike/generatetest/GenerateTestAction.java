package com.vv.testrike.generatetest;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class GenerateTestAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiClass psiClass = getPsiClassFromContext(e);
        if (psiClass == null)
            return;

        Project project = e.getData(LangDataKeys.PROJECT);
        try {
            PsiJavaFile psiJavaTestFile = getTestFile(e, project, psiClass);
            PsiClass psiTestClass = psiJavaTestFile.getClasses()[0];
            OpenSourceUtil.navigate(psiTestClass);

            addInjectMocksField(project, psiClass, psiTestClass);
            addMockFields(project, psiClass, psiTestClass);
            addMethodsTests(project, psiClass, psiTestClass);

            WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IncorrectOperationException>)() ->
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiTestClass)
            );

        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private void addMethodsTests(Project project, PsiClass psiClass, PsiClass psiTestClass) throws IOException {
        PsiMethod[] methodsInClass = psiClass.getMethods();
        for (PsiMethod aMethodInClass : methodsInClass) {
            PsiModifierList modifierList = aMethodInClass.getModifierList();
            if (isPublicOrProtectedOrPackagePrivate(modifierList)) {
                addMethodTests(project, aMethodInClass, psiClass, psiTestClass);
            }
        }
    }

    private boolean isPublicOrProtectedOrPackagePrivate(PsiModifierList modifierList) {
        return modifierList.hasExplicitModifier("public")
                || modifierList.hasExplicitModifier("protected")
                || !modifierList.hasExplicitModifier("private");
    }

    private void addMethodTests(Project project, PsiMethod method, PsiClass psiClass, PsiClass psiTestClass) throws IOException {
        PsiType returnType = method.getReturnType();
        String methodName = method.getName();
        PsiTypeParameterList typeParameterList = method.getTypeParameterList();
        PsiParameterList parameterList = method.getParameterList();
        PsiCodeBlock body = method.getBody();

        if(body != null) {
            PsiStatement[] statements = body.getStatements();
            for (PsiStatement statement : statements) {

            }
        }

        String capitalizedMethodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        String methodText = "public void test" + capitalizedMethodName + "_Should_When() {}";
        PsiMethod methodFromText = JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText, psiTestClass);
        methodFromText.getModifierList().addAnnotation("org.junit.jupiter.api.Test");

        WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IOException>)() ->
                psiTestClass.add(methodFromText)
        );
    }

    private void addMockFields(Project project, PsiClass psiClass, PsiClass psiTestClass) throws IOException {
        PsiField[] allFields = psiClass.getAllFields();
        for (PsiField psiField : allFields) {
            if (isFieldAutowired(psiField)) {
                addMockField(project, psiTestClass, psiField);
            }
        }
    }

    private boolean isFieldAutowired(PsiField psiField) {
        PsiAnnotation[] annotations = psiField.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            if (annotationContainsAutowiredOrInject(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean annotationContainsAutowiredOrInject(PsiAnnotation annotation) {
        return "org.springframework.beans.factory.annotation.Autowired".equals(annotation.getQualifiedName())
                || "javax.inject.Inject".equals(annotation.getQualifiedName());
    }

    private void addMockField(Project project, PsiClass psiTestClass, PsiField psiField) throws IOException {
        String accessModifier = getAccessModifier(psiField);

        String fieldDeclaration = accessModifier + (accessModifier.isEmpty() ? "" : " ")
                + psiField.getType().getCanonicalText(false) + " " + psiField.getName() + ";";
        PsiField mockField = JavaPsiFacade.getElementFactory(project).createFieldFromText(fieldDeclaration, psiTestClass);
        Objects.requireNonNull(mockField.getModifierList()).addAnnotation("org.mockito.Mock");

        WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IOException>)() ->
                psiTestClass.add(mockField)
        );
    }

    private String getAccessModifier(PsiField psiField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (Objects.requireNonNull(modifierList).hasExplicitModifier("public")) {
            return "public";
        }
        else if (Objects.requireNonNull(modifierList).hasExplicitModifier("protected")) {
            return "protected";
        }
        else if (Objects.requireNonNull(modifierList).hasExplicitModifier("private")) {
            return "private";
        }
        else {
            return "";
        }
    }

    private void addInjectMocksField(Project project, @NotNull PsiClass psiClass, PsiClass psiTestClass) throws IOException {
        String fieldName = psiClass.getName().substring(0, 1).toLowerCase() + psiClass.getName().substring(1);
        String fieldDefinition = "private " + psiClass.getName() + " " + fieldName + ";";
        PsiField injectMocksField = JavaPsiFacade.getElementFactory(project).createFieldFromText(fieldDefinition, psiTestClass);
        injectMocksField.getModifierList().addAnnotation("org.mockito.InjectMocks");

        WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IOException>)() ->
                psiTestClass.add(injectMocksField)
        );
    }

    @Nullable
    private PsiJavaFile getTestFile(AnActionEvent e, Project project, PsiClass psiClass) throws IOException {
        String packageName = getPackageName(e);
        PsiDirectory psiDirectory = getTheFirstPsiDirectoryInTheProject(e, project);
        if (psiDirectory == null)
            return null;

        PsiJavaFile psiJavaTestFile = createTestClass(project, packageName, psiClass);
        String className = psiJavaTestFile.getClasses()[0].getName();

        JavaDirectoryServiceImpl.checkCreateClassOrInterface(psiDirectory, className);

        PsiJavaFile psiJavaTestFile2 = ApplicationManager.getApplication().runWriteAction((ThrowableComputable<PsiJavaFile, IOException>)() ->
            (PsiJavaFile)psiJavaTestFile.setName(className + "." + StdFileTypes.JAVA.getDefaultExtension())
        );

        PsiElement addedElement = WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IOException>)() ->
                psiDirectory.add(psiJavaTestFile2)
        );

        if (addedElement instanceof PsiJavaFile) {
            return (PsiJavaFile)addedElement;
        }
        else {
            PsiFile containingFile = addedElement.getContainingFile();
            throw new IncorrectOperationException("Selected class file name '" +
                    containingFile.getName() +  "' mapped to not java file type '"+
                    containingFile.getFileType().getDescription() + "'");
        }
    }

    private PsiJavaFile createTestClass(Project project, String packageName, @NotNull PsiClass psiClass) throws IOException {
        String testFileName = psiClass.getName() + "Test." + StdFileTypes.JAVA.getDefaultExtension();
        String content = "public class " + psiClass.getName() + "Test {}";

        PsiFile psiTestFile = PsiFileFactory.getInstance(project).createFileFromText(testFileName, StdFileTypes.JAVA, content);
        if (!(psiTestFile instanceof PsiJavaFile)){
            throw new IncorrectOperationException("This template did not produce a Java class or an interface\n" + psiTestFile.getText());
        }
        PsiJavaFile psiJavaTestFile = (PsiJavaFile)psiTestFile;
        final PsiClass[] classes = psiJavaTestFile.getClasses();
        if (classes.length == 0) {
            throw new IncorrectOperationException("This template did not produce a Java class or an interface\n" + psiTestFile.getText());
        }

        WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiElement, IOException>)() -> {
                psiJavaTestFile.setPackageName(packageName);
                return null;
        });

        return psiJavaTestFile;
    }

    @NotNull
    private String getPackageName(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            return javaFile.getPackageName();
        }
        return "";
    }

    @Nullable
    private PsiDirectory getTheFirstPsiDirectoryInTheProject(@NotNull AnActionEvent e, @NotNull Project project) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Module module = ModuleUtilCore.findModuleForFile(psiFile);
        List<VirtualFile> testSourceRoots = ModuleRootManagerImpl.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
        if (testSourceRoots.isEmpty()) {
            throw new IncorrectOperationException("Test source folder doesn't exist. It should.\n");
        }

        VirtualFile virtualFile = testSourceRoots.get(0);
        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(virtualFile);
        return getOrCreateDirectoryAccordingToPackageHierarchy(directory, getPackageName(e), project);
    }

    private PsiDirectory getOrCreateDirectoryAccordingToPackageHierarchy(@NotNull PsiDirectory psiDirectory, @NotNull String packageNameChain, Project project) {
        String[] packageNames = packageNameChain.split("\\.");

        PsiDirectory currentPsiDirectory = psiDirectory;
        for (String packageName: packageNames) {
            PsiDirectory subdirectory = currentPsiDirectory.findSubdirectory(packageName);
            if (subdirectory == null) {
                final PsiDirectory parentDirectory = currentPsiDirectory;
                currentPsiDirectory = WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<PsiDirectory, IncorrectOperationException>)() ->
                        parentDirectory.createSubdirectory(packageName)
                );
            }
            else
                currentPsiDirectory = subdirectory;
        }
        return currentPsiDirectory;
    }

    @Override
    public void update(AnActionEvent e) {
        PsiClass psiClass = getPsiClassFromContext(e);
        e.getPresentation().setEnabled(psiClass != null);
    }

    @Nullable
    private PsiClass getPsiClassFromContext(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    }
}
