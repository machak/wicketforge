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
package wicketforge.action;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import wicketforge.action.ui.CreatePageDialog;
import wicketforge.templates.WicketTemplates;

/**
 * WicketPageAction
 */
public class WicketPageAction extends WicketClassBaseAction {

    protected WicketPageAction() {
        super("Wicket Page", "Create a new Wicket Page");
    }

    @NotNull
    protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
        ActionRunnableImpl actionRunnable = new ActionRunnableImpl(project, directory, WicketTemplates.WICKET_PAGE_HTML);
        CreatePageDialog dialog = new CreatePageDialog(project, actionRunnable, getCommandName(), directory);
        dialog.show();
        return actionRunnable.getCreatedElements();
    }

    @Override
    protected String getErrorTitle() {
        return "Cannot create Wicket Page";
    }

    @Override
    protected String getCommandName() {
        return "Create Wicket Page";
    }

    @Override
    protected String getActionName(PsiDirectory directory, String newName) {
        return "Creating Wicket Page " + newName;
    }
}
