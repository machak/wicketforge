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
package wicketforge.action.ui;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wicketforge.WicketForgeUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class ExtractHtmlTextDialog extends DialogWrapper {
    private JPanel contentPane;
    private JTextField propertyKeyTextField;
    private JTextArea propertyValueTextArea;
    private JComboBox propertiesFileComboBox;
    private JCheckBox chooseDifferentDestinationFolderCheckBox;
    private JPanel chooseDifferentDestinationFolderPanel;

    private Project project;
    private Module module;
    private ActionRunnable actionRunnable;
    private PsiClass componentClass;
    private PsiDirectory destinationDirectory;
    private PsiPackage psiPackage;

    public ExtractHtmlTextDialog(@NotNull Project project, @NotNull ActionRunnable actionRunnable, @NotNull String title, @NotNull PsiClass componentClass, @NotNull PsiDirectory directory, @NotNull String text) {
        super(project, false);

        this.project = project;
        this.actionRunnable = actionRunnable;
        this.componentClass = componentClass;
        this.destinationDirectory = directory;
        this.module = ModuleUtil.findModuleForPsiElement(directory);
        this.psiPackage = JavaDirectoryService.getInstance().getPackage(directory);

        setResizable(true);
        setModal(true);
        setTitle(title);

        propertyValueTextArea.setText(text);

        addPropertiesFilesOptions();
        init();
    }

    @Override
    protected void init() {
        super.init();

        // set a simple key from text value
        StringBuilder sb = new StringBuilder();
        for (String word : StringUtil.getWordsIn(propertyValueTextArea.getText())) {
            if (word.length() > 1 && StringUtil.containsAlphaCharacters(word)) {
                sb.append(StringUtil.capitalize(word));
                if (sb.length() > 100) {
                    break;
                }
            }
        }
        propertyKeyTextField.setText(sb.toString());

        // if we have only 1 destination, we dont offer a different folder selection
        if (WicketForgeUtil.getResourceRoots(module).length < 2) {
            chooseDifferentDestinationFolderPanel.setVisible(false);
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return propertyKeyTextField;
    }

    private void addPropertiesFilesOptions() {
        List<Object> data = new ArrayList<Object>();

        { // find component class properties file
            PsiFile propertiesFile = WicketForgeUtil.getPropertiesFile(componentClass);
            if (propertiesFile == null) {
                data.add(componentClass);
            } else {
                data.add(propertiesFile);
            }
        }

        { // find and add application properties file
            PsiClass appClass = WicketForgeUtil.findWicketApplicationClass(project);
            if (appClass != null) {
                PsiFile propertiesFile = WicketForgeUtil.getPropertiesFile(appClass);
                if (propertiesFile != null) {
                    data.add(propertiesFile);
                }
            }
        }

        propertiesFileComboBox.setModel(new DefaultComboBoxModel(data.toArray(new Object[data.size()])));
        propertiesFileComboBox.setRenderer(new PropertiesFileComboBoxRenderer());
        propertiesFileComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // if a properties file is selected, then there is nothing to create so no different destination selectable...
                chooseDifferentDestinationFolderCheckBox.setEnabled(!(propertiesFileComboBox.getSelectedItem() instanceof PropertiesFile));
            }
        });
        chooseDifferentDestinationFolderCheckBox.setEnabled(!(propertiesFileComboBox.getSelectedItem() instanceof PropertiesFile));
    }

    private boolean validateInput() {
        String key = propertyKeyTextField.getText();
        if (key == null || key.length() == 0) {
            setErrorText("Property key cannot be null");
            return false;
        }

        String value = propertyValueTextArea.getText();
        if (value == null || value.length() == 0) {
            setErrorText("Property value cannot be null");
            return false;
        }

        Object o = propertiesFileComboBox.getSelectedItem();
        if (o instanceof PropertiesFile) {
            for (IProperty prop : ((PropertiesFile) o).getProperties()) {
                if (key.equals(prop.getKey())) {
                    setErrorText(String.format("Property key '%s' already in use", key));
                    return false;
                }
            }
        }

        setErrorText(null);
        return true;
    }

    @Override
    protected void doOKAction() {
        Object o = propertiesFileComboBox.getSelectedItem();
        PropertiesFile propertiesFile = o instanceof PropertiesFile ? (PropertiesFile) o : null;

        // if we dont have any properties file (we will create one) -> ask for destination
        if (module != null && psiPackage != null && propertiesFile == null && chooseDifferentDestinationFolderCheckBox.isSelected()) {
            PsiDirectory directory = WicketForgeUtil.selectTargetDirectory(psiPackage.getQualifiedName(), project, module);
            if (directory == null) {
                return; // aborted
            }
            destinationDirectory = directory;
        }

        if (validateInput() && actionRunnable.run(propertiesFile, destinationDirectory, propertyKeyTextField.getText(), propertyValueTextArea.getText())) {
            super.doOKAction();
        }
    }

    private static class PropertiesFileComboBoxRenderer extends JLabel implements ListCellRenderer {
        public Component getListCellRendererComponent(JList jList, Object value, int index, boolean isSelected, boolean hasFocus) {
            if (value instanceof PropertiesFile) {
                setText(((PropertiesFile) value).getName());
            } else if (value instanceof PsiClass) {
                setText(((PsiClass) value).getName() + "   [new]");
            } else {
                setText("");
            }
            return this;
        }
    }

    public static interface ActionRunnable {
        boolean run(@Nullable PropertiesFile propertiesFile, @NotNull PsiDirectory destinationDirectory, @NotNull String key, @NotNull String value);
    }
}
