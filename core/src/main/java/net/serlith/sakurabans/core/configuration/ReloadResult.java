package net.serlith.sakurabans.core.configuration;

import java.util.List;

public sealed interface ReloadResult {

    record Reloaded(List<String> restartRequired, List<ConfigIssue> warnings)
            implements ReloadResult {
    }

    record Failed(List<ConfigIssue> errors) implements ReloadResult {
    }
}
