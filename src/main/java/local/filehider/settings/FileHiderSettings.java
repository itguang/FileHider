package local.filehider.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service(Service.Level.APP)
@State(name = "FileHiderSettings", storages = @Storage("fileHider.xml"))
public final class FileHiderSettings implements PersistentStateComponent<FileHiderSettings.State> {
    public static final Topic<FileHiderSettingsListener> TOPIC =
            Topic.create("FileHiderSettingsChanged", FileHiderSettingsListener.class);

    private volatile State state = State.withFactoryDefaults();
    private volatile RuleSnapshot snapshot = RuleSnapshot.from(state);

    public static FileHiderSettings getInstance() {
        return ApplicationManager.getApplication().getService(FileHiderSettings.class);
    }

    public static List<FileHiderRule> factoryDefaultRules() {
        return List.of(
                new FileHiderRule(".classpath", RuleType.FILE),
                new FileHiderRule(".factorypath", RuleType.FILE),
                new FileHiderRule(".project", RuleType.FILE),
                new FileHiderRule("flattened-pom.xml", RuleType.FILE)
        );
    }

    @Override
    public State getState() {
        return state.copy();
    }

    @Override
    public void loadState(@NotNull State loadedState) {
        if (loadedState.defaultRules == null || loadedState.defaultRules.isEmpty()) {
            loadedState.defaultRules = copyRules(factoryDefaultRules());
        }
        if (loadedState.userRules == null) {
            loadedState.userRules = new ArrayList<>();
        }
        replaceState(loadedState);
    }

    public RuleSnapshot getSnapshot() {
        return snapshot;
    }

    public void update(State newState) {
        if (newState.defaultRules == null || newState.defaultRules.isEmpty()) {
            newState.defaultRules = copyRules(factoryDefaultRules());
        }
        if (newState.userRules == null) {
            newState.userRules = new ArrayList<>();
        }
        replaceState(newState);
    }

    public void resetDefaultsToFactory() {
        State next = state.copy();
        next.defaultRules = copyRules(factoryDefaultRules());
        update(next);
    }

    private void replaceState(State next) {
        State normalized = next.copy();
        normalized.defaultRules = normalizeRules(normalized.defaultRules);
        normalized.userRules = normalizeRules(normalized.userRules);
        state = normalized;
        snapshot = RuleSnapshot.from(normalized);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    public static List<FileHiderRule> copyRules(List<FileHiderRule> source) {
        List<FileHiderRule> copy = new ArrayList<>();
        if (source != null) {
            for (FileHiderRule rule : source) {
                if (rule != null) {
                    copy.add(rule.copy());
                }
            }
        }
        return copy;
    }

    public static List<FileHiderRule> normalizeRules(List<FileHiderRule> source) {
        List<FileHiderRule> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) {
            return result;
        }
        for (FileHiderRule rule : source) {
            if (rule == null || rule.name == null) {
                continue;
            }
            String name = rule.name.trim();
            RuleType type = rule.type == null ? RuleType.BOTH : rule.type;
            if (!RuleValidator.isValidName(name)) {
                continue;
            }
            FileHiderRule normalized = new FileHiderRule(name, type);
            if (seen.add(normalized.key())) {
                result.add(normalized);
            }
        }
        return result;
    }

    public static final class State {
        public boolean enabled = true;
        public List<FileHiderRule> defaultRules = new ArrayList<>();
        public List<FileHiderRule> userRules = new ArrayList<>();

        public static State withFactoryDefaults() {
            State state = new State();
            state.defaultRules = copyRules(factoryDefaultRules());
            return state;
        }

        public State copy() {
            State copy = new State();
            copy.enabled = enabled;
            copy.defaultRules = copyRules(defaultRules);
            copy.userRules = copyRules(userRules);
            return copy;
        }
    }

    public record RuleSnapshot(boolean enabled, Set<String> files, Set<String> directories) {
        static RuleSnapshot from(State state) {
            Set<String> files = new LinkedHashSet<>();
            Set<String> directories = new LinkedHashSet<>();
            addRules(files, directories, state.defaultRules);
            addRules(files, directories, state.userRules);
            return new RuleSnapshot(state.enabled, Set.copyOf(files), Set.copyOf(directories));
        }

        public boolean matches(String name, boolean directory) {
            if (!enabled) {
                return false;
            }
            return directory ? directories.contains(name) : files.contains(name);
        }

        private static void addRules(Set<String> files, Set<String> directories, List<FileHiderRule> rules) {
            for (FileHiderRule rule : normalizeRules(rules)) {
                if (rule.type == RuleType.FILE || rule.type == RuleType.BOTH) {
                    files.add(rule.name);
                }
                if (rule.type == RuleType.DIR || rule.type == RuleType.BOTH) {
                    directories.add(rule.name);
                }
            }
        }
    }
}
