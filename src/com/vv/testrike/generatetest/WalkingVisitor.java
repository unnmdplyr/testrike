package com.vv.testrike.generatetest;

import com.intellij.psi.*;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

class WalkingVisitor extends JavaRecursiveElementWalkingVisitor {

    private final List<String> givenStatements;
    private final DefaultValue defaultValue;

    WalkingVisitor() {
        givenStatements = new ArrayList<>();
        defaultValue = new DefaultValue();
    }

    @Contract(pure = true)
    List<String> getGivenStatements() {
        return givenStatements;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        PsiType returnType = methodCallExpression.resolveMethod().getReturnType();

        if (qualifierExpression != null && !PsiType.VOID.equals(returnType)) {
            String givenStatement = "org.mockito.Mockito.when(" + methodCallExpression.getText() +
                    ").thenReturn(" + defaultValue.getDefaultReturnType(returnType) + ");";
            givenStatements.add(givenStatement);
        }
    }
}
