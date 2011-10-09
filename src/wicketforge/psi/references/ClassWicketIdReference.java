/*
 * Copyright 2010 The WicketForge-Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wicketforge.psi.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import wicketforge.WicketForgeUtil;
import wicketforge.psi.hierarchy.AttributeItem;
import wicketforge.psi.hierarchy.WicketClassHierarchy;
import wicketforge.psi.hierarchy.WicketMarkupHierarchy;

/**
 */
public class ClassWicketIdReference implements PsiReference {
    private PsiLiteralExpression wicketIdExpression;
    private PsiClass psiClass;
    private TextRange textRange;

    public ClassWicketIdReference(@NotNull PsiLiteralExpression wicketIdExpression, @NotNull PsiClass psiClass) {
        this.wicketIdExpression = wicketIdExpression;
        this.psiClass = psiClass;
        textRange = new TextRange(0, wicketIdExpression.getTextLength()); // issue 62: text range from 0 -> need also parentheses 
    }

    public PsiElement getElement() {
        return wicketIdExpression;
    }

    public TextRange getRangeInElement() {
        return textRange;
    }

    public PsiElement resolve() {
        PsiFile markupFile = WicketForgeUtil.getMarkupFile(psiClass);
        if (markupFile != null) {
            String path = WicketClassHierarchy.findPathOf(psiClass, wicketIdExpression, false, false);
            if (path != null) {
                WicketMarkupHierarchy hierarchy = WicketMarkupHierarchy.create((XmlFile) markupFile);
                AttributeItem item = hierarchy.getWicketIdPathMap().get(path);
                if (item != null) {
                    return item.getAttributeValue();
                }
            }
        }
        return null;
    }

    public String getCanonicalText() {
        return textRange.substring(wicketIdExpression.getText());
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        ElementManipulator manipulator = ElementManipulators.getManipulator(wicketIdExpression);
        if (manipulator instanceof StringLiteralManipulator) {
            return ((StringLiteralManipulator) manipulator).handleContentChange(wicketIdExpression, newElementName);
        }
        return null;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        return null;
    }

    public boolean isReferenceTo(PsiElement element) {
        return wicketIdExpression.getManager().areElementsEquivalent(resolve(), element);
    }

    @NotNull
    public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
        return true;
    }
}
