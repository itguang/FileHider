package local.filehider.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public final class ShowHiddenFilesAction extends ToggleAction implements DumbAware {
    private static final Key<Boolean> SHOW_HIDDEN_FILES = Key.create("file.hider.show.hidden.files");

    public static boolean isEnabled(Project project) {
        return Boolean.TRUE.equals(project.getUserData(SHOW_HIDDEN_FILES));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        return project != null && isEnabled(project);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        project.putUserData(SHOW_HIDDEN_FILES, state ? Boolean.TRUE : null);
        ProjectView.getInstance(project).refresh();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }
}
