package local.filehider.settings;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public final class ProjectViewRefreshListener implements FileHiderSettingsListener {
    @Override
    public void settingsChanged() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    ProjectView.getInstance(project).refresh();
                }
            }
        });
    }
}
