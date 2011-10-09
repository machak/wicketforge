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
package wicketforge.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import wicketforge.WicketForgeUtil;
import wicketforge.visitor.CompletionResult;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class PropertiesCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(final CompletionParameters p, final CompletionResultSet rs) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                PsiFile f = p.getOriginalFile();
                if (f.getFileType() == StdFileTypes.JAVA && p.getPosition() instanceof PsiJavaToken) {
                    PsiJavaToken position = (PsiJavaToken) p.getPosition();
                    if (isWicketResourceModel(position)) {
                        PsiJavaFile jf = (PsiJavaFile) f;
                        PsiClass[] classes = jf.getClasses();

                        for (PsiClass c : classes) {
                            if (WicketForgeUtil.isWicketComponent(c)) {
                                addPropertiesToResult(c, rs);
                            }
                        }
                    }
                }
                else if (f.getFileType() == StdFileTypes.HTML) {
                    PsiElement psiElement = p.getPosition();
                    if (psiElement instanceof XmlToken) {
                        XmlToken position = (XmlToken) psiElement;
                        if (isWicketAttribute(position)) {
                            PsiClass c = WicketForgeUtil.getMarkupClass(f);
                            if (c != null) {
                                addPropertiesToResult(c, rs);
                            }
                        }
                    }
                }
            }
        });
    }

    private void addPropertiesToResult(PsiClass c, CompletionResultSet rs) {
        PropertiesFile properties = (PropertiesFile) WicketForgeUtil.getPropertiesFile(c);
        if (properties != null) {
            List<CompletionResult> references = new ArrayList<CompletionResult>();
            for (IProperty property : properties.getProperties()) {
                references.add(new CompletionResult(property.getKey(), property.getValue()));
            }

            addReferencesToResult(references, rs);
            if (references.size() > 0) {
                rs.stopHere();
            }
        }
    }

    private void addReferencesToResult(List<CompletionResult> references, CompletionResultSet rs) {
        if (references != null && !references.isEmpty()) {
            for (CompletionResult s : references) {
                LookupElementBuilder lookupElementBuilder =
                        LookupElementBuilder.create(s.getKey())
                                .setIcon(StdFileTypes.PROPERTIES.getIcon())
                                .setTypeText(".properties")
                                .setTailText("  " + s.getDescription(), true);
                rs.addElement(lookupElementBuilder);
            }
        }
    }

    private boolean isWicketResourceModel(PsiJavaToken position) {
        if (!(position.getParent() instanceof PsiLiteralExpression)) {
            return false;
        }

        PsiLiteralExpression expression = (PsiLiteralExpression) position.getParent();
        if (!(expression.getParent() instanceof PsiExpressionList)) {
            return false;
        }

        PsiExpressionList expressionList = (PsiExpressionList) expression.getParent();
        if (!(expressionList.getParent() instanceof PsiNewExpression)) {
            return false;
        }

        PsiNewExpression newExpression = (PsiNewExpression) expressionList.getParent();
        PsiMethod constructor = newExpression.resolveConstructor();
        if (constructor == null || constructor.getContainingFile().isPhysical()) {
            return false;
        }

        PsiClass psiClass = constructor.getContainingClass();
        if (psiClass == null) {
            return false;
        }

        if (WicketForgeUtil.isWicketResourceModel(psiClass)) {
            PsiExpressionList constructorArgs = newExpression.getArgumentList();
            if (constructorArgs == null) {
                return false;
            }
        }
        return true;
    }

    private boolean isWicketAttribute(XmlToken position) {
        if (!(position.getParent() instanceof XmlAttributeValue)) {
            return false;
        }
        XmlAttributeValue attributeValue = (XmlAttributeValue) position.getParent();
        if (!(attributeValue.getParent() instanceof XmlAttribute)) {
            return false;
        }
        XmlAttribute attribute = (XmlAttribute) attributeValue.getParent();
        String name = attribute.getName();
        return "key".equalsIgnoreCase(name);
    }
}
