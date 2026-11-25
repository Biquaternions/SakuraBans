package net.serlith.sakurabans.core.admin;

import java.util.List;
import net.serlith.sakurabans.core.model.SettingType;
import org.jspecify.annotations.Nullable;

public record SettingField(String key, SettingType type, Object defaultValue, String description,
        List<String> options, @Nullable Double min, @Nullable Double max, boolean networkWide,
        boolean editable, boolean restartRequired) {

    public SettingField {
        options = List.copyOf(options);
    }
}
