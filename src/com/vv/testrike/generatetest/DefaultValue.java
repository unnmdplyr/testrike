package com.vv.testrike.generatetest;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

class DefaultValue {

    @NotNull
    String getDefaultReturnType(PsiType returnType) {
        if (PsiType.BOOLEAN.equals(returnType)) {
            return "false";
        } else if (PsiType.BYTE.equals(returnType) || PsiType.SHORT.equals(returnType) || PsiType.INT.equals(returnType)) {
            return "0";
        } else if (PsiType.LONG.equals(returnType)) {
            return "0L";
        } else if (PsiType.FLOAT.equals(returnType)) {
            return "0.0f";
        } else if (PsiType.DOUBLE.equals(returnType)) {
            return "0.0d";
        } else if (PsiType.CHAR.equals(returnType)) {
            return "\u0000";
        } else {
            return "new " + returnType.getPresentableText() + "()";
        }
    }
}
