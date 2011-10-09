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
package wicketforge.psi.hierarchy;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wicketforge.Constants;
import wicketforge.WicketForgeUtil;

import java.util.*;

/**
 */
public class WicketClassHierarchy {
    private static final Logger LOG = Logger.getInstance("#wicketforge.psi.hierarchy.WicketClassHierarchy");

    private Map<String, ClassItem> wicketIdPathMap;
    private ClassItem root;

    @NotNull
    public static WicketClassHierarchy create(@NotNull PsiClass psiClass) {
        return new WicketClassHierarchy(psiClass);
    }

    private WicketClassHierarchy(@NotNull final PsiClass psiClass) {
        this.wicketIdPathMap = new HashMap<String, ClassItem>();
        this.root = new ClassItem("");
        this.wicketIdPathMap.put("", root);

        final Map<PsiElement, List<PsiNewExpression>> workMap = new HashMap<PsiElement, List<PsiNewExpression>>(); // Key: PsiClass or PsiNewExpression reference from a WicketMarkup component
        final Map<PsiElement, List<PsiNewExpression>> workMapReplaceWith = new HashMap<PsiElement, List<PsiNewExpression>>();
        psiClass.accept(new JavaRecursiveElementVisitor() {
            private MarkupReferences markupReferences = new MarkupReferences();

            @Override
            public void visitClass(PsiClass aClass) {
                if (!aClass.equals(psiClass) && WicketForgeUtil.isWicketComponentWithAssociatedMarkup(aClass)) {
                    return; // we do not visit inner classes that have own markup
                }
                if (!(aClass instanceof PsiAnonymousClass) && WicketForgeUtil.isMarkupContainer(aClass)) {
                    markupReferences.pushCurrent(new SmartList<PsiElement>(aClass));
                    super.visitClass(aClass);
                    markupReferences.popCurrent();
                } else {
                    super.visitClass(aClass);
                }
            }

            @Override
            public void visitNewExpression(PsiNewExpression expression) {
                PsiClass aClass = expression.getAnonymousClass();
                if (aClass != null && WicketForgeUtil.isMarkupContainer(aClass)) {
                    markupReferences.pushCurrent(new SmartList<PsiElement>(expression));
                    super.visitNewExpression(expression);
                    markupReferences.popCurrent();
                } else {
                    super.visitNewExpression(expression);
                }
            }

            @Override
            public void visitMethod(PsiMethod method) {
                // if we have 'populateItem' method () -> we add parameter variable to our var stack, so item.add(...)
                // could be resolved to ListView/Loop hierarchy.
                // We dont need to check if its populateItem from a specific class (ex ListView) because first visitCallExpression
                // checks if add is from a MarkupContainer -> then he tries to resolve variable. If we have a variable in our stack
                // that is not from wicket populateItem, this does not matter.
                // (Normally we would check this but AbstractRepeater has only a onPopulate method. Only Loop and ListView have populateItem
                // these are not inherited. So we dont make this (security) check for now...)
                if ("populateItem".equals(method.getName())) {
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    if (parameters.length > 0) {
                        markupReferences.put(parameters[0], markupReferences.getCurrent());
                        markupReferences.pushCurrent(null); // enhancement 80 -> we have no currentMarkupReference inside populateItem -> should use item.add that can be resolved...
                        super.visitMethod(method);
                        markupReferences.popCurrent();
                        return; // in this case super already done...
                    }
                }
                // todo mm -> check if other subclasses from AbstractRepeater need also to be handled here...
                super.visitMethod(method);
            }

            @Override
            public void visitCallExpression(PsiCallExpression callExpression) {
                // first super, so assignement adds could be resolved, ex: add(link = new Link(...)) // todo mm -> check if super of other visits should be also called first 
                super.visitCallExpression(callExpression);

                if (!(callExpression instanceof PsiMethodCallExpression)) {
                    return;
                }
                PsiMethod method = callExpression.resolveMethod();
                if (method == null) {
                    return;
                }
                PsiClass methodCallClass = method.getContainingClass();
                if (methodCallClass == null) {
                    return;
                }
                String methodName = method.getName();

                Map<PsiElement, List<PsiNewExpression>> addMap;
                if (("add".equals(methodName) || "addOrReplace".equals(methodName) || "autoAdd".equals(methodName) || "replace".equals(methodName)) && WicketForgeUtil.isMarkupContainer(methodCallClass)) {
                    addMap = workMap;
                } else if ("replaceWith".equals(methodName) && WicketForgeUtil.isWicketComponent(methodCallClass)) {
                    addMap = workMapReplaceWith;
                } else {
                    return;
                }

                // the markupReference class for the given add(...) etc...
                List<? extends PsiElement> markupReferenceList = null;
                // if call expression has a reference we got to search for it. ex: link.add(...) or MyPage.this.add(...)
                PsiReferenceExpression callMethodReference = PsiTreeUtil.getRequiredChildOfType(callExpression, PsiReferenceExpression.class);
                PsiElement element = PsiTreeUtil.getChildOfAnyType(callMethodReference, PsiReferenceExpression.class, PsiThisExpression.class);
                if (element instanceof PsiReferenceExpression) {
                    // ex: link.add(...)
                    element = ((PsiReferenceExpression) element).resolve();
                    if (element instanceof PsiVariable) {
                        markupReferenceList = new SmartList<PsiElement>(markupReferences.get((PsiVariable) element));
                        if (addMap != workMapReplaceWith) {
                            for (Iterator<? extends PsiElement> iterator = markupReferenceList.iterator(); iterator.hasNext();) {
                                PsiElement markupReference = iterator.next();
                                if (markupReference instanceof PsiNewExpression) { // check instanceOf,  markupReference can also be PsiClass (issue 67)
                                    // this one will be our markupReference
                                    PsiClass classToCreate = resolveClassFromNewExpression((PsiNewExpression) markupReference);
                                    // just to be sure our markupReference is not one with own markup (ex: someone could add components to an instance of an inner panel, bad practice but possible)
                                    if (classToCreate != null && !classToCreate.equals(psiClass) && WicketForgeUtil.isWicketComponentWithAssociatedMarkup(classToCreate)) {
                                        iterator.remove();
                                    }
                                }
                            }
                        }
                    }
                } else if (element instanceof PsiThisExpression) {
                    // ex: MyPage.this.add(...)
                    element = ((PsiThisExpression) element).getQualifier();
                    if (element == null) {
                        // this.add(...) -> current
                        markupReferenceList = markupReferences.getCurrent();
                    } else {
                        // MyPage.this.add(...) -> resolve PsiClass references by this...
                        element = ((PsiJavaCodeReferenceElement) element).resolve();
                        if (element instanceof PsiClass) {
                            markupReferenceList = new SmartList<PsiElement>(element);
                        }
                    }
                } else {
                    // no reference -> so add to our current
                    markupReferenceList = markupReferences.getCurrent();
                }

                // no markupReference to add -> return
                if (markupReferenceList == null || markupReferenceList.isEmpty()) {
                    return;
                }

                // go thru all call argument expressions
                PsiExpressionList callExpressionList = callExpression.getArgumentList();
                if (callExpressionList != null) {
                    for (PsiElement markupReference : markupReferenceList) {
                        List<PsiNewExpression> addList = addMap.get(markupReference);
                        for (PsiExpression callParameterExpression : callExpressionList.getExpressions()) {
                            // resolve expressions for new wicket component
                            List<PsiNewExpression> newExpressions = resolveExpressionNewWicketComponent(callParameterExpression);
                            if (newExpressions.size() > 0) {
                                // and add to its
                                if (addList == null) {
                                    addList = newExpressions;
                                    addMap.put(markupReference, addList);
                                } else {
                                    addList.addAll(newExpressions);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                // if field has an initializer...
                PsiExpression initializer = field.getInitializer();
                if (initializer != null) {
                    // put this into our var container
                    markupReferences.put(field, resolveExpressionNewWicketComponent(initializer));
                }
            }

            @Override
            public void visitDeclarationStatement(PsiDeclarationStatement statement) {
                super.visitDeclarationStatement(statement);
                for (PsiElement element : statement.getDeclaredElements()) {
                    // if is variable and has an initializer...
                    if (element instanceof PsiVariable) {
                        PsiExpression initializer = ((PsiVariable) element).getInitializer();
                        if (initializer != null) {
                            // put this into our var container
                            markupReferences.put((PsiVariable) element, resolveExpressionNewWicketComponent(initializer));
                        }
                    }
                }
            }

            @Override
            public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                PsiExpression leftExpression = expression.getLExpression();
                if (leftExpression instanceof PsiReference) {
                    PsiElement resolvedElement = ((PsiReference) leftExpression).resolve();
                    if (resolvedElement instanceof PsiVariable) {
                        PsiExpression initializer  = expression.getRExpression();
                        if (initializer != null) {
                            // put assigned expression to our var container
                            markupReferences.put((PsiVariable) resolvedElement, resolveExpressionNewWicketComponent(initializer));
                        }
                    }
                }
            }

            /**
             *
             * @param expression
             * @return              referenced PsiNewExpression's (if they are Wicket Components) 
             */
            @NotNull
            private List<PsiNewExpression> resolveExpressionNewWicketComponent(@Nullable PsiExpression expression) {
                List<PsiNewExpression> list = new SmartList<PsiNewExpression>();
                if (expression instanceof PsiConditionalExpression) {
                    List<PsiNewExpression> newExpression = resolveExpressionNewWicketComponentInternal(((PsiConditionalExpression) expression).getThenExpression());
                    if (newExpression != null) {
                        list.addAll(newExpression);
                    }
                    newExpression = resolveExpressionNewWicketComponentInternal(((PsiConditionalExpression) expression).getElseExpression());
                    if (newExpression != null) {
                        list.addAll(newExpression);
                    }
                } else {
                    List<PsiNewExpression> newExpression = resolveExpressionNewWicketComponentInternal(expression);
                    if (newExpression != null) {
                        list.addAll(newExpression);
                    }
                }
                return list;
            }

            /**
             *
             */
            @Nullable
            private List<PsiNewExpression> resolveExpressionNewWicketComponentInternal(@Nullable PsiExpression expression) {
                // get new Expression/Variable from method chaining ex: new Label(...).setOutputMarkupId(true).setEnabled(true);
                if (expression instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExpression = null;
                    while (expression instanceof PsiMethodCallExpression) {
                        methodCallExpression = (PsiMethodCallExpression) expression;
                        PsiElement element = expression.getFirstChild();
                        if (element instanceof PsiReferenceExpression) {
                            element = element.getFirstChild();
                            if (element instanceof PsiExpression) {
                                expression = (PsiExpression) element;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    // check if chaining-method returns wicketcomponent (issue 69)
                    if (expression instanceof PsiReferenceExpression) {
                        PsiClass returnClass = getMethodReturnClass(methodCallExpression.resolveMethod());
                        if (returnClass == null || !WicketForgeUtil.isWicketComponent(returnClass)) {
                            return null;
                        }
                    }
                }
                // get Variable from assignement ex: add(link = new Link(...))
                if (expression instanceof PsiAssignmentExpression) {
                    expression = ((PsiAssignmentExpression) expression).getLExpression();
                }

                // resolve
                if (expression instanceof PsiReference) {
                    // if it's a reference -> find possibly newExpression from our var stack
                    PsiElement resolvedElement = ((PsiReference) expression).resolve();
                    if (resolvedElement instanceof PsiVariable) {
                        List<PsiNewExpression> result = new SmartList<PsiNewExpression>();
                        for (PsiElement element : markupReferences.get((PsiVariable) resolvedElement)) {
                            if (element instanceof PsiNewExpression) {
                                result.add((PsiNewExpression) element);
                            }
                        }
                        return result;
                    }
                } else if (expression instanceof PsiNewExpression) {
                    // check if its a new wicket component
                    PsiClass classToCreate = resolveClassFromNewExpression((PsiNewExpression) expression);
                    if (classToCreate != null && WicketForgeUtil.isWicketComponent(classToCreate)) {
                        return new SmartList<PsiNewExpression>((PsiNewExpression) expression);
                    }
                }
                return null;
            }

            /**
             * @param newExpression
             * @return              PsiAnonymousClass or referenced PsiClass or null
             *
             * This is *not* equal to PsiNewExpression.getClassOrAnonymousClassReference()
             */
            @Nullable
            private PsiClass resolveClassFromNewExpression(@NotNull PsiNewExpression newExpression) {
                // first check if referenced var is a anonymous class, then we have our result
                PsiClass result = newExpression.getAnonymousClass();
                if (result == null) {
                    // if not anonymous -> resolve concrete class as result
                    PsiJavaCodeReferenceElement referenceElement = newExpression.getClassReference();
                    if (referenceElement != null) {
                        PsiElement resolvedElement = referenceElement.resolve();
                        if (resolvedElement != null && resolvedElement instanceof PsiClass) {
                            result = (PsiClass) resolvedElement;
                        }
                    }
                }
                return result;
            }
        });

        // merge all workMapReplaceWith into WorkMap
        for (Map.Entry<PsiElement, List<PsiNewExpression>> entry : workMapReplaceWith.entrySet()) {
            // we need newExpression
            PsiElement key = entry.getKey();
            if (key instanceof PsiNewExpression) {
                PsiNewExpression newExpression = (PsiNewExpression) key;
                for (List<PsiNewExpression> list : workMap.values()) {
                    if (list.contains(newExpression)) {
                        list.addAll(entry.getValue());
                    }
                }
            }
        }

        // put all new wicket component expressions to a list as NewComponentReference
        Map<PsiNewExpression, ClassItem.NewComponentReference> newComponentReferenceMap = new HashMap<PsiNewExpression, ClassItem.NewComponentReference>();
        for (List<PsiNewExpression> list : workMap.values()) {
            for (PsiNewExpression newExpression : list) {
                if (!newComponentReferenceMap.containsKey(newExpression)) {
                    ClassItem.NewComponentReference newComponentReference = ClassItem.NewComponentReference.create(newExpression);
                    if (newComponentReference != null) {
                        newComponentReferenceMap.put(newExpression, newComponentReference);
                    }
                }
            }
        }

        List<PsiNewExpression> addedComponents = workMap.get(psiClass);
        if (addedComponents != null) {
            addRecursive(workMap, newComponentReferenceMap, new StringBuilder(), root, psiClass, addedComponents, 0);
        }
    }

    private void addRecursive(@NotNull Map<PsiElement, List<PsiNewExpression>> workMap,
                              @NotNull Map<PsiNewExpression, ClassItem.NewComponentReference> newComponentReferenceMap,
                              @NotNull StringBuilder path,
                              @NotNull ClassItem parent,
                              @NotNull PsiElement parentElement,
                              @Nullable List<PsiNewExpression> addedComponents,
                              int depth) {
        if (depth++ > 50) {
            LOG.error("Deep addRecursive", path.toString());
            return;
        }
        
        if (addedComponents != null) {
            for (PsiNewExpression newExpression : addedComponents) {
                ClassItem.NewComponentReference newComponentReference = newComponentReferenceMap.get(newExpression);
                if (newComponentReference != null) {

                    int length = path.length();
                    try {
                        path.append(Constants.HIERARCHYSEPARATOR).append(newComponentReference.getWicketId());

                        ClassItem child = findOrCreateChild(path, parent, newComponentReference.getWicketId());

                        child.getReferences().add(newComponentReference);

                        addRecursive(workMap, newComponentReferenceMap, path, child, newExpression, workMap.get(newExpression), depth);
                    } finally {
                        path.setLength(length);
                    }
                }
            }
        }
        // add also components of superclass (if any... ex: MyPage.java -> inner class MyMarkupContainer -> label myMarkupContainer )
        PsiClass superClass = null;
        if (parentElement instanceof PsiClass) {
            superClass = ((PsiClass) parentElement).getSuperClass();
        } else if (parentElement instanceof PsiNewExpression) {
            PsiNewExpression newExpression = (PsiNewExpression) parentElement;
            ClassItem.NewComponentReference newComponentReference = newComponentReferenceMap.get(newExpression);
            if (newComponentReference != null) {
                superClass = newComponentReference.getBaseClassToCreate();
            }
        }
        if (superClass != null) {
            List<PsiNewExpression> superAddedComponents = workMap.get(superClass);
            if (superAddedComponents != null) {
                addRecursive(workMap, newComponentReferenceMap, path, parent, superClass, superAddedComponents, depth);
            }
        }
    }

    @NotNull
    private ClassItem findOrCreateChild(@NotNull StringBuilder path, @NotNull ClassItem parent, @NotNull String wicketId) {
        ClassItem child = parent.findChild(wicketId);
        if (child == null) {
            child = new ClassItem(wicketId);
            parent.addChild(child);
            wicketIdPathMap.put(path.toString(), child);
        }
        return child;
    }

    /**
     * @return PsiClass of method return or null
     */
    @Nullable
    private PsiClass getMethodReturnClass(@Nullable PsiMethod method) {
        if (method != null) {
            PsiType type = method.getReturnType();
            if (type instanceof PsiClassType) {
                return ((PsiClassType) type).resolve();
            }
        }
        return null;
    }

    @NotNull
    public Map<String, ClassItem> getWicketIdPathMap() {
        return wicketIdPathMap;
    }

    @NotNull
    public ClassItem getRoot() {
        return root;
    }

    @Nullable
    public static String findPathOf(@NotNull PsiClass psiClass, @NotNull PsiExpression wicketIdExpression, boolean parent, boolean incomplete) {
        WicketClassHierarchy hierarchy = create(psiClass);
        for (Map.Entry<String, ClassItem> entry : hierarchy.getWicketIdPathMap().entrySet()) {
            for (ClassItem.NewComponentReference newComponentReference : entry.getValue().getReferences()) {
                if (wicketIdExpression.equals(newComponentReference.getWicketIdExpression())) {
                    String path = entry.getKey();
                    return parent ? path.substring(0, path.lastIndexOf(Constants.HIERARCHYSEPARATOR)) : path;
                }
            }
        }
        if (incomplete) {
            // ok, wicket expression is not added yet so we dont know hierarchy of our component
            // lets try to find textual position in hierarchy, not best method but we have no other option
            // If component gets added later, correct hierarchy gets checked...
            final TextRange wicketIdTextRange = wicketIdExpression.getTextRange();
            String bestPath = "";
            TextRange bestTextRange = psiClass.getTextRange();
            // go thru all new references
            for (Map.Entry<String, ClassItem> entry : hierarchy.getWicketIdPathMap().entrySet()) {
                for (ClassItem.NewComponentReference newComponentReference : entry.getValue().getReferences()) {
                    TextRange textRange = newComponentReference.getNewExpression().getTextRange();
                    // if wicketId is in new-references-textRange and this is inner of current best...
                    if (textRange.contains(wicketIdTextRange) && bestTextRange.contains(textRange)) {
                        // then we have a better candidate  
                        bestTextRange = textRange;
                        bestPath = entry.getKey();
                    }
                }
            }
            return parent ? bestPath : bestPath + Constants.HIERARCHYSEPARATOR + WicketForgeUtil.getWicketIdFromExpression(wicketIdExpression);
        }
        return null;
    }

    private static final class MarkupReferences {
        private Stack<List<? extends PsiElement>> currentStack = new Stack<List<? extends PsiElement>>();
        private Map<PsiVariable, List<? extends PsiElement>> variableMap = new HashMap<PsiVariable, List<? extends PsiElement>>();

        /**
         * Get PsiClass or PsiNewExpression as MarkupContainer of WicketComponent from variable.
         *
         * @param variable  PsiVariable
         * @return          PsiClass, PsiNewExpression of WicketComponent or null.
         */
        @NotNull
        private List<? extends PsiElement> get(@NotNull PsiVariable variable) {
            List<? extends PsiElement> result = variableMap.get(variable);
            return result == null ? Collections.<PsiElement>emptyList() : result;
        }

        /**
         * Put assigned PsiClass or PsiNewExpression of a WicketComponent to variable into our variableMap.
         *
         * @param variable      PsiVariable
         * @param elements       PsiClass or PsiNewExpression
         */
        private void put(@NotNull PsiVariable variable, @Nullable List<? extends PsiElement> elements) {
            if (elements == null || elements.isEmpty()) {
                // remove variable
                variableMap.remove(variable);
            } else {
                // ...add to our stack
                variableMap.put(variable, elements);
            }
        }

        /**
         * current is -> the current reference for add(...) -> PsiClass or PsiNewExpression (for anonymous classes)
         * @param current
         */
        private void pushCurrent(@Nullable List<? extends PsiElement> current) {
            currentStack.push(current);
        }

        @Nullable
        private List<? extends PsiElement> popCurrent() {
            return currentStack.pop();
        }

        @Nullable
        private List<? extends PsiElement> getCurrent() {
            return currentStack.isEmpty() ? null : currentStack.peek();
        }
    }
}
