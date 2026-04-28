package local.filehider.tree;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import local.filehider.actions.ShowHiddenFilesAction;
import local.filehider.settings.FileHiderSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FileHiderTreeStructureProvider implements TreeStructureProvider, DumbAware {
    private static final String PROJECT_FILES_PANE_ID = "ProjectFilesPane";

    @Override
    public @NotNull Collection<AbstractTreeNode<?>> modify(
            @NotNull AbstractTreeNode<?> parent,
            @NotNull Collection<AbstractTreeNode<?>> children,
            ViewSettings settings
    ) {
        Project project = parent.getProject();
        if (project == null
                || ShowHiddenFilesAction.isEnabled(project)
                || isProjectFilesPane(settings)
                || children.isEmpty()) {
            return children;
        }

        FileHiderSettings.RuleSnapshot snapshot = FileHiderSettings.getInstance().getSnapshot();
        if (!snapshot.enabled()) {
            return children;
        }

        List<AbstractTreeNode<?>> filtered = null;
        for (AbstractTreeNode<?> child : children) {
            if (shouldHide(project, child, snapshot)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(children.size());
                    for (AbstractTreeNode<?> previous : children) {
                        if (previous == child) {
                            break;
                        }
                        filtered.add(previous);
                    }
                }
            } else if (filtered != null) {
                filtered.add(child);
            }
        }
        return filtered == null ? children : filtered;
    }

    private static boolean shouldHide(
            Project project,
            AbstractTreeNode<?> node,
            FileHiderSettings.RuleSnapshot snapshot
    ) {
        Object value = node.getValue();
        if (!(value instanceof PsiFileSystemItem psiItem)) {
            return false;
        }
        if (!(value instanceof PsiFile) && !(value instanceof PsiDirectory)) {
            return false;
        }

        VirtualFile virtualFile = psiItem.getVirtualFile();
        if (virtualFile == null || isWhitelistedNode(project, node, virtualFile)) {
            return false;
        }

        boolean directory = virtualFile.isDirectory();
        return snapshot.matches(virtualFile.getName(), directory);
    }

    private static boolean isWhitelistedNode(Project project, AbstractTreeNode<?> node, VirtualFile virtualFile) {
        String className = node.getClass().getName();
        if (className.endsWith(".ProjectViewProjectNode")
                || className.endsWith(".ProjectViewModuleNode")
                || className.endsWith(".NamedLibraryElementNode")) {
            return true;
        }

        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        if (!fileIndex.isInContent(virtualFile) || fileIndex.isInLibrary(virtualFile)) {
            return true;
        }

        if (virtualFile.isDirectory()) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
                    if (virtualFile.equals(root)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isProjectFilesPane(ViewSettings settings) {
        String paneId = paneId(settings);
        return PROJECT_FILES_PANE_ID.equals(paneId);
    }

    private static String paneId(ViewSettings settings) {
        if (settings == null) {
            return null;
        }
        for (String methodName : List.of("getPaneId", "getViewPaneId", "getId")) {
            try {
                Method method = settings.getClass().getMethod(methodName);
                Object value = method.invoke(settings);
                if (value instanceof String stringValue) {
                    return stringValue;
                }
            } catch (ReflectiveOperationException ignored) {
                // The exact settings implementation is platform-version specific.
            }
        }
        return null;
    }
}
