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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import wicketforge.Constants;
import wicketforge.model.WicketTagRegistry;
import wicketforge.model.tags.WicketTag;
import wicketforge.model.tags.WicketTagAttribute;

/**
 */
public class WicketElementAttributeValueCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(final CompletionParameters p, final CompletionResultSet rs) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                PsiFile f = p.getOriginalFile();
                if (f.getFileType() == StdFileTypes.HTML) {
                    PsiElement psiElement = p.getPosition();
                    if (psiElement instanceof XmlToken) {
                        XmlToken position = (XmlToken) psiElement;
                        if (isWicketElementAttributeValue(position)) {
                            addAttributeValueResults(position, rs);
                        }
                    }
                }
            }
        });
    }

    private void addAttributeValueResults(XmlToken position, CompletionResultSet rs) {
        WicketTag wicketTag = WicketTagRegistry.getTag(getParentElementName(position));
        if (wicketTag == null) {
            return;
        }

        for (WicketTagAttribute attr : wicketTag.getAttributes()) {
            LookupElementBuilder lookupElementBuilder =
                    LookupElementBuilder.create(attr.getName() + "=\"\"")
                            .setPresentableText(attr.getName())
                            .setIcon(Constants.PROPERTIES_ICON);
            rs.addElement(lookupElementBuilder);
        }
    }

    private boolean isWicketElementAttributeValue(XmlToken position) {
        String tagName = getParentElementName(position);
        return tagName != null && tagName.startsWith("wicket");
    }

    private String getParentElementName(XmlToken position) {
        PsiElement element = position.getParent();
        if (!(element instanceof XmlAttributeValue)) {
            return null;
        }

        XmlAttributeValue attributeValue = (XmlAttributeValue) element;
        XmlAttribute attribute = (XmlAttribute) attributeValue.getParent();
        HtmlTag tag = (HtmlTag) attribute.getParent();
        return tag.getName();
    }

    private String getAttributeName(XmlToken position) {
        PsiElement element = position.getParent();
        XmlAttribute attribute = (XmlAttribute) element;
        return attribute.getName();
    }

    private String getAttributeValue(XmlToken position) {
        if (position instanceof XmlAttribute) {
            return null;
        }
        XmlAttributeValue attributeValue = (XmlAttributeValue) position.getParent();
        return attributeValue.getText();
    }

}
