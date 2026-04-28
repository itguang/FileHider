package local.filehider.settings;

import java.util.Objects;

public class FileHiderRule {
    public String name = "";
    public RuleType type = RuleType.BOTH;

    public FileHiderRule() {
    }

    public FileHiderRule(String name, RuleType type) {
        this.name = name;
        this.type = type == null ? RuleType.BOTH : type;
    }

    public FileHiderRule copy() {
        return new FileHiderRule(name, type);
    }

    public String key() {
        return name + "\u0000" + type.name();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FileHiderRule that)) {
            return false;
        }
        return Objects.equals(name, that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}
