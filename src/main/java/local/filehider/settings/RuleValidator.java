package local.filehider.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RuleValidator {
    private RuleValidator() {
    }

    public static boolean isValidName(String name) {
        return name != null
                && !name.trim().isEmpty()
                && name.indexOf('/') < 0
                && name.indexOf('\\') < 0
                && name.indexOf('*') < 0
                && name.indexOf('?') < 0;
    }

    public static String validate(List<FileHiderRule> rules) {
        Set<String> seen = new HashSet<>();
        for (FileHiderRule rule : rules) {
            if (rule == null) {
                return "Rules cannot be null.";
            }
            String name = rule.name == null ? "" : rule.name.trim();
            if (!isValidName(name)) {
                return "Rules cannot be blank or contain /, \\, *, or ?.";
            }
            RuleType type = rule.type == null ? RuleType.BOTH : rule.type;
            String key = name + "\u0000" + type.name();
            if (!seen.add(key)) {
                return "Duplicate rules are not allowed within the same rule group.";
            }
        }
        return null;
    }
}
