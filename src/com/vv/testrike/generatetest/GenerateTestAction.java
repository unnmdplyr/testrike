package com.vv.testrike.generatetest;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateTestAction extends AnAction {

    private TestLibraryAvailable testLibrary;

    @Override
    public void actionPerformed(AnActionEvent e) {
        PsiClass psiClass = getPsiClassFromContext(e);
        if (psiClass == null)
            return;

        Project project = e.getData(LangDataKeys.PROJECT);
        testLibrary = getExistingExternalLibraries(project);
        try {
            WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<Void, IncorrectOperationException>) () -> {
                PsiJavaFile psiJavaTestFile = getTestFile(e, project, psiClass);
                PsiClass psiTestClass = psiJavaTestFile.getClasses()[0];
                OpenSourceUtil.navigate(psiTestClass);

                addInjectMocksField(project, psiClass, psiTestClass);
                addMockFields(project, psiClass, psiTestClass);
                addMockFieldsFromConstructor(project, psiClass, psiTestClass);
                addMethodsTests(project, psiClass, psiTestClass);

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiTestClass);
                JavaCodeStyleManager.getInstance(project).optimizeImports(psiTestClass.getContainingFile());
                CodeStyleManager.getInstance(project).reformat(psiTestClass);
                return null;
            });

        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private TestLibraryAvailable getExistingExternalLibraries(Project project) {
        LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        final LibraryTable.ModifiableModel projectLibraryModel = projectLibraryTable.getModifiableModel();

        if (isExternalLibraryInTheProject(projectLibraryModel, "org.junit.jupiter:junit-jupiter-api:")) {
            return TestLibraryAvailable.JUNIT_JUPITER;
        }

        if (isExternalLibraryInTheProject(projectLibraryModel, "junit:junit:4")) {
            return TestLibraryAvailable.JUNIT_4;
        }

        if (isExternalLibraryInTheProject(projectLibraryModel, "org.testng:testng:")) {
            return TestLibraryAvailable.TESTNG;
        }

        if (isExternalLibraryInTheProject(projectLibraryModel, "junit:junit:3")) {
            return TestLibraryAvailable.JUNIT_3;
        }

        return TestLibraryAvailable.NON;
    }

    private boolean isExternalLibraryInTheProject(@NotNull LibraryTable.ModifiableModel projectLibraryModel, String libraryId) {
        return Stream.of(projectLibraryModel.getLibraries())
                .map(Library::getName)
                .anyMatch(name -> name.contains(libraryId));
    }

    private String getTestAnnotationFqnString() {
        String fqnString = "";

        switch (testLibrary) {
            case JUNIT_JUPITER:
                fqnString = "org.junit.jupiter.api.Test";
                break;
            case JUNIT_4:
                fqnString = "org.junit.Test";
                break;
            case TESTNG:
                fqnString = "org.testng.annotations.Test";
                break;
        }
        return fqnString;
    }

    private void addMethodsTests(Project project, @NotNull PsiClass psiClass, PsiClass psiTestClass) {
        Stream.of(psiClass.getMethods())
                .filter(method -> !method.isConstructor() && isPublicOrProtectedOrPackagePrivate(method))
                .forEach(method -> addMethodTests(project, method, psiTestClass));
    }

    private boolean isPublicOrProtectedOrPackagePrivate(@NotNull PsiMethod method) {
        PsiModifierList modifierList = method.getModifierList();

        return modifierList.hasExplicitModifier("public")
                || modifierList.hasExplicitModifier("protected")
                || !modifierList.hasExplicitModifier("private");
    }

    private void addMethodTests(Project project, @NotNull PsiMethod method, PsiClass psiTestClass) {
        PsiMethod createdMethod = createMethodFromText(project, method, psiTestClass);
        addGivenStatements(project, method, createdMethod);
        addWhenStatement(project, method, createdMethod, psiTestClass);
    }

    private PsiMethod createMethodFromText(Project project, @NotNull PsiMethod method, PsiClass psiTestClass) {
        String methodName = method.getName();
        String capitalizedMethodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        String methodText = "public void test" + capitalizedMethodName + "_Should_When() {}";

        PsiMethod psiMethod = JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText, psiTestClass);
        psiMethod.getModifierList().addAnnotation(getTestAnnotationFqnString());
        return (PsiMethod) psiTestClass.add(psiMethod);
    }

    private void addGivenStatements(Project project, @NotNull PsiMethod method, @NotNull PsiMethod createdTestMethod) {
        WalkingVisitor visitor = new WalkingVisitor();
        method.accept(visitor);

        PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
        List<PsiStatement> psiGivenStatements = visitor.getGivenStatements().stream()
                .map(givenStatement -> psiElementFactory.createStatementFromText(givenStatement, createdTestMethod.getBody()))
                .collect(Collectors.toList());

        PsiComment commentGiven = psiElementFactory.createCommentFromText("//  given", createdTestMethod.getBody());

        if (!psiGivenStatements.isEmpty()) {
            PsiElement firstChild = psiGivenStatements.get(0).getFirstChild();
            psiGivenStatements.get(0).addBefore(commentGiven, firstChild);
        }
        psiGivenStatements.forEach(createdTestMethod.getBody()::add);
    }

    private void addWhenStatement(Project project, @NotNull PsiMethod method, @NotNull PsiMethod createdTestMethod, @NotNull PsiClass psiTestClass) {
        PsiField injectMocksField = getInjectMocksField(psiTestClass);
        String methodCall = injectMocksField.getName() + "." + method.getName() + "(" + createParameterListText(method) + ");";

        PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
        PsiStatement whenStatement = psiElementFactory.createStatementFromText(methodCall, createdTestMethod.getBody());

        PsiComment commentWhen = psiElementFactory.createCommentFromText("//  when", createdTestMethod.getBody());

        PsiElement firstChild = whenStatement.getFirstChild();
        whenStatement.addBefore(commentWhen, firstChild);

        createdTestMethod.getBody().add(whenStatement);
    }

    @NotNull
    private String createParameterListText(@NotNull PsiMethod method) {
        DefaultValue defaultValue = new DefaultValue();
        StringBuilder stringBuilder = new StringBuilder();

        Stream.of(method.getParameterList().getParameters())
                .forEach(psiParameter -> stringBuilder.append(defaultValue.getDefaultReturnType(psiParameter.getType())).append(", "));

        if (stringBuilder.length() > 0)
            stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());

        return stringBuilder.toString();
    }

    @NotNull
    private PsiField getInjectMocksField(@NotNull PsiClass psiTestClass) {
        return Stream.of(psiTestClass.getFields())
                .filter(psiField -> psiField.getModifierList().findAnnotation("org.mockito.InjectMocks") != null)
                .findFirst()
                .orElseThrow(() -> new IncorrectOperationException("InjectMocks field should exist.\n"));
    }

    private void addMockFields(Project project, PsiClass psiClass, PsiClass psiTestClass) {
        Stream.of(psiClass.getAllFields())
                .filter(this::containsAutowiredOrInjectAnnotation)
                .forEach(psiField -> addMockField(project, psiTestClass, psiField));
    }

    private void addMockFieldsFromConstructor(Project project, PsiClass psiClass, PsiClass psiTestClass) {
        Stream.of(psiClass.getConstructors())
                .filter(this::containsAutowiredOrInjectAnnotation)
                .forEach(psiMethod -> addMockField(project, psiTestClass, psiMethod));
    }

    private boolean containsAutowiredOrInjectAnnotation(PsiModifierListOwner psiModifierListOwner) {
        return Stream.of(psiModifierListOwner.getAnnotations())
                .anyMatch(this::annotationContainsAutowiredOrInject);
    }

    private boolean annotationContainsAutowiredOrInject(PsiAnnotation annotation) {
        return "org.springframework.beans.factory.annotation.Autowired".equals(annotation.getQualifiedName())
                || "javax.inject.Inject".equals(annotation.getQualifiedName());
    }

    private void addMockField(Project project, PsiClass psiTestClass, PsiField psiField) {
        String accessModifier = getAccessModifier(psiField.getModifierList());
        createMockField(accessModifier, psiField, project, psiTestClass);
    }

    private void addMockField(Project project, PsiClass psiTestClass, PsiMethod psiMethod) {
        Stream.of(psiMethod.getParameterList().getParameters())
                .forEach(parameter -> createMockField("private", parameter, project, psiTestClass));
    }

    private String getAccessModifier(PsiModifierList modifierList) {
        if (Objects.requireNonNull(modifierList).hasExplicitModifier("public")) {
            return "public";
        } else if (Objects.requireNonNull(modifierList).hasExplicitModifier("protected")) {
            return "protected";
        } else if (Objects.requireNonNull(modifierList).hasExplicitModifier("private")) {
            return "private";
        } else {
            return "";
        }
    }

    private void createMockField(String accessModifier, PsiVariable psiVariable, Project project, PsiClass psiTestClass) {
        String fieldDeclaration = accessModifier + (accessModifier.isEmpty() ? "" : " ")
                + psiVariable.getType().getCanonicalText() + " " + psiVariable.getName() + ";";

        createAnnotatedField(fieldDeclaration, project, psiTestClass, "org.mockito.Mock");
    }

    private void addInjectMocksField(Project project, @NotNull PsiClass psiClass, PsiClass psiTestClass) {
        String fieldName = psiClass.getName().substring(0, 1).toLowerCase() + psiClass.getName().substring(1);
        String fieldDeclaration = "private " + psiClass.getName() + " " + fieldName + ";";

        createAnnotatedField(fieldDeclaration, project, psiTestClass, "org.mockito.InjectMocks");
    }

    private void createAnnotatedField(String fieldDeclaration, Project project, PsiClass psiTestClass, String annotation) {
        PsiField psiField = JavaPsiFacade.getElementFactory(project).createFieldFromText(fieldDeclaration, psiTestClass);
        Objects.requireNonNull(psiField.getModifierList()).addAnnotation(annotation);
        psiTestClass.add(psiField);
    }

    @Nullable
    private PsiJavaFile getTestFile(AnActionEvent e, Project project, PsiClass psiClass) {
        String packageName = getPackageName(e);
        PsiDirectory psiDirectory = getTestSourceRootFolderInTheModule(e);
        if (psiDirectory == null)
            return null;

        PsiJavaFile psiJavaTestFile = createTestClass(project, packageName, psiClass);
        String className = psiJavaTestFile.getClasses()[0].getName();

        JavaDirectoryServiceImpl.checkCreateClassOrInterface(psiDirectory, className);

        PsiJavaFile psiJavaTestFile2 = (PsiJavaFile) psiJavaTestFile.setName(className + "." + StdFileTypes.JAVA.getDefaultExtension());
        PsiElement addedElement = psiDirectory.add(psiJavaTestFile2);

        if (addedElement instanceof PsiJavaFile) {
            return (PsiJavaFile) addedElement;
        } else {
            PsiFile containingFile = addedElement.getContainingFile();
            throw new IncorrectOperationException("Selected class file name '" +
                    containingFile.getName() + "' mapped to not java file type '" +
                    containingFile.getFileType().getDescription() + "'");
        }
    }

    private PsiJavaFile createTestClass(Project project, String packageName, @NotNull PsiClass psiClass) {
        String testFileName = psiClass.getName() + "Test." + StdFileTypes.JAVA.getDefaultExtension();
        String content = "public class " + psiClass.getName() + "Test {}";

        PsiFile psiTestFile = PsiFileFactory.getInstance(project).createFileFromText(testFileName, StdFileTypes.JAVA, content);
        if (!(psiTestFile instanceof PsiJavaFile)) {
            throw new IncorrectOperationException("This template did not produce a Java class or an interface\n" + psiTestFile.getText());
        }
        PsiJavaFile psiJavaTestFile = (PsiJavaFile) psiTestFile;
        final PsiClass[] classes = psiJavaTestFile.getClasses();
        if (classes.length == 0) {
            throw new IncorrectOperationException("This template did not produce a Java class or an interface\n" + psiTestFile.getText());
        }
        classes[0].getModifierList().addAnnotation("org.junit.jupiter.api.extension.ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)");

        psiJavaTestFile.setPackageName(packageName);
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
    private PsiDirectory getTestSourceRootFolderInTheModule(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Module module = ModuleUtilCore.findModuleForFile(psiFile);
        List<VirtualFile> testSourceRoots = ModuleRootManagerImpl.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
        if (testSourceRoots.isEmpty()) {
            throw new IncorrectOperationException("Test source folder doesn't exist. It should.\n");
        }

        Project project = module.getProject();
        VirtualFile virtualFile = testSourceRoots.get(0);
        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(virtualFile);
        return getOrCreateDirectoryAccordingToPackageHierarchy(directory, getPackageName(e), project);
    }

    private PsiDirectory getOrCreateDirectoryAccordingToPackageHierarchy(@NotNull PsiDirectory psiDirectory, @NotNull String packageNameChain, Project project) {
        String[] packageNames = packageNameChain.split("\\.");

        PsiDirectory currentPsiDirectory = psiDirectory;
        for (String packageName : packageNames) {
            PsiDirectory subdirectory = currentPsiDirectory.findSubdirectory(packageName);
            currentPsiDirectory = subdirectory == null ? currentPsiDirectory.createSubdirectory(packageName) : subdirectory;
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
