package net.serlith.sakurabans.core.admin;

import java.util.Map;

public record SettingsSnapshot(long version, Map<String, Object> values,
        Map<String, ValueOrigin> origins) {

    public SettingsSnapshot {
        values = Map.copyOf(values);
        origins = Map.copyOf(origins);
    }
}
