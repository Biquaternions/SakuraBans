package net.serlith.sakurabans.core.configuration;

import java.io.Serial;
import java.util.List;

public final class ConfigException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<ConfigIssue> errors;

    public ConfigException(final List<ConfigIssue> errors) {
        super(errors.size() + " configuration error(s):\n  "
                + String.join("\n  ", errors.stream().map(ConfigIssue::toString).toList()));
        this.errors = List.copyOf(errors);
    }

    public List<ConfigIssue> errors() {
        return this.errors;
    }
}
