/*
 * Copyright (C) 2017. Uber Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.rendecano.uber.rib.kotlin.intellij_plugin.action.rib;

import com.google.common.base.Preconditions;
import com.intellij.ide.IdeView;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.PackageWrapper;
import com.rendecano.uber.rib.kotlin.intellij_plugin.generator.Generator;

import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;

import javax.swing.JOptionPane;

/** Base action to generate Java source and test source files with {@link Generator}. */
public abstract class GenerateAction extends AnAction {

  private DataContext dataContext;

  @Override
  public final void update(AnActionEvent e) {
    this.dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  /**
   * Generates source with given generators.
   *
   * @param mainSourceGenerators generators to use to genertae source in the main source directory.
   */
  protected void generate(
      final List<Generator> mainSourceGenerators, final List<Generator> testSourceGenerators) {
    /**
     * Preconditions have been validated by {@link GenerateRibAction#isAvailable(DataContext)}.
     */
    final Project project = Preconditions.checkNotNull(CommonDataKeys.PROJECT.getData(dataContext));
    final IdeView view = Preconditions.checkNotNull(LangDataKeys.IDE_VIEW.getData(dataContext));
    final PsiDirectory directory = Preconditions.checkNotNull(view.getOrChooseDirectory());
    final Module currentModule =
        Preconditions.checkNotNull(ModuleUtilCore.findModuleForPsiElement(directory));
    final SourceFolder testSourceFolder = suitableTestSourceFolders(project, currentModule);
    if (null == testSourceFolder) {
      JOptionPane.showMessageDialog(
          null, "Cannot create a RIB in a module without a test source set.");
      return;
    }

    final PackageWrapper targetPackage =
        new PackageWrapper(PsiManager.getInstance(project), getPackageName());

    ApplicationManager.getApplication()
        .runWriteAction(
            new Runnable() {
              @Override
              public void run() {
                CommandProcessor.getInstance()
                    .executeCommand(
                        project,
                        new Runnable() {
                          @Override
                          public void run() {
                            for (Generator generator : mainSourceGenerators) {
                              createSourceFile(project, generator, directory);
                            }

//                            PsiDirectory testDirectory =
//                                RefactoringUtil.createPackageDirectoryInSourceRoot(
//                                    targetPackage, testSourceFolder.getFile());
//                            for (Generator generator : testSourceGenerators) {
//                              createSourceFile(project, generator, testDirectory);
//                            }
                          }
                        },
                        "Generate new RIB",
                        null);
              }
            });
  }

  /** @return gets the current package name for an executing action. */
  protected final String getPackageName() {
    /** Preconditions have been validated by {@link GenerateAction#isAvailable(DataContext)}. */
    final Project project = Preconditions.checkNotNull(CommonDataKeys.PROJECT.getData(dataContext));
    final IdeView view = Preconditions.checkNotNull(LangDataKeys.IDE_VIEW.getData(dataContext));
    final PsiDirectory directory = Preconditions.checkNotNull(view.getOrChooseDirectory());
    final PsiPackage psiPackage =
        Preconditions.checkNotNull(JavaDirectoryService.getInstance().getPackage(directory));

    return psiPackage.getQualifiedName();
  }

  /**
   * Checked whether or not this action can be enabled.
   *
   * <p>Requirements to be enabled: * User must be in a Java source folder.
   *
   * @param dataContext to figure out where the user is.
   * @return {@code true} when the action is available, {@code false} when the action is not
   *     available.
   */
  private boolean isAvailable(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || view.getDirectories().length == 0) {
      return false;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory dir : view.getDirectories()) {
      if (projectFileIndex.isUnderSourceRootOfType(
              dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES)
          && checkPackageExists(dir)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if a Java package exists for a directory.
   *
   * @param directory to check.
   * @return {@code true} when a package exists, {@code false} when it does not.
   */
  private boolean checkPackageExists(PsiDirectory directory) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
    if (pkg == null) {
      return false;
    }

    String name = pkg.getQualifiedName();
    return StringUtil.isEmpty(name)
        || PsiNameHelper.getInstance(directory.getProject()).isQualifiedName(name);
  }

  private static SourceFolder suitableTestSourceFolders(Project project, Module module) {
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      List<SourceFolder> testSourceFolders =
          contentEntry.getSourceFolders(JavaSourceRootType.TEST_SOURCE);
      for (SourceFolder testSourceFolder : testSourceFolders) {
        if (testSourceFolder.getFile() != null) {
          if (!JavaProjectRootsUtil.isInGeneratedCode(testSourceFolder.getFile(), project)) {
            return testSourceFolder;
          }
        }
      }
    }

    return null;
  }

  /**
   * Writes a source file generated by a {@link Generator} to disk.
   *
   * @param project to write file in.
   * @param generator to generate file with.
   * @param directory to write file to.
   */
  private static void createSourceFile(
      Project project, Generator generator, PsiDirectory directory) {
    PsiFile file =
        PsiFileFactory.getInstance(project)
            .createFileFromText(
                String.format("%s.kt", generator.getClassName()),
                JavaLanguage.INSTANCE,
                generator.generate());
    directory.add(file);
  }
}