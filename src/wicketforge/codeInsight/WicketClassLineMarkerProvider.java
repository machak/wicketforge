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
package wicketforge.codeInsight;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import wicketforge.Constants;
import wicketforge.WicketForgeUtil;
import wicketforge.facet.WicketForgeFacet;

import java.util.Collection;
import java.util.List;

/**
 */
public class WicketClassLineMarkerProvider implements LineMarkerProvider {
    public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiClass) {
            if (WicketForgeFacet.hasFacetOrIsFromLibrary(element)) {
                PsiClass psiClass = (PsiClass) element.getParent();
                if (WicketForgeUtil.isWicketComponentWithAssociatedMarkup(psiClass)) {
                    final PsiFile psiFile = WicketForgeUtil.getMarkupFile(psiClass);
                    if (psiFile != null) {
                        return NavigableLineMarkerInfo.create(element, new PsiElement[] {psiFile}, Constants.TOMARKUPREF, null);
                    }
                }
            }
        }
        return null;
    }

    public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
    }
}
