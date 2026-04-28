package local.filehider.settings;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FileHiderSettingsTest {
    @Test
    public void normalizeRulesTrimsAndDeduplicatesWithinGroup() {
        List<FileHiderRule> normalized = FileHiderSettings.normalizeRules(List.of(
                new FileHiderRule(" .project ", RuleType.FILE),
                new FileHiderRule(".project", RuleType.FILE),
                new FileHiderRule(".project", RuleType.DIR)
        ));

        assertEquals(List.of(
                new FileHiderRule(".project", RuleType.FILE),
                new FileHiderRule(".project", RuleType.DIR)
        ), normalized);
    }

    @Test
    public void snapshotExpandsRuleTypesIntoFileAndDirectorySets() {
        FileHiderSettings.State state = new FileHiderSettings.State();
        state.defaultRules = List.of(
                new FileHiderRule("file-only", RuleType.FILE),
                new FileHiderRule("dir-only", RuleType.DIR),
                new FileHiderRule("both", RuleType.BOTH)
        );

        FileHiderSettings.RuleSnapshot snapshot = FileHiderSettings.RuleSnapshot.from(state);

        assertTrue(snapshot.matches("file-only", false));
        assertFalse(snapshot.matches("file-only", true));
        assertFalse(snapshot.matches("dir-only", false));
        assertTrue(snapshot.matches("dir-only", true));
        assertTrue(snapshot.matches("both", false));
        assertTrue(snapshot.matches("both", true));
    }

    @Test
    public void validatorRejectsPathsGlobsAndDuplicates() {
        assertNull(RuleValidator.validate(List.of(new FileHiderRule(".classpath", RuleType.FILE))));
        assertEquals("Rules cannot be blank or contain /, \\, *, or ?.",
                RuleValidator.validate(List.of(new FileHiderRule("a/b", RuleType.FILE))));
        assertEquals("Rules cannot be blank or contain /, \\, *, or ?.",
                RuleValidator.validate(List.of(new FileHiderRule("*.xml", RuleType.FILE))));
        assertEquals("Duplicate rules are not allowed within the same rule group.",
                RuleValidator.validate(List.of(
                        new FileHiderRule(".project", RuleType.FILE),
                        new FileHiderRule(".project", RuleType.FILE)
                )));
    }
}
