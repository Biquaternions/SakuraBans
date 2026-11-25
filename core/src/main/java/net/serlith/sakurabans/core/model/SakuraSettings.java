package net.serlith.sakurabans.core.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.message.MessageField;
import net.serlith.sakurabans.api.message.MessageSnapshot;
import net.serlith.sakurabans.api.settings.SettingField;
import net.serlith.sakurabans.api.settings.SettingType;
import net.serlith.sakurabans.api.settings.SettingsSnapshot;
import net.serlith.sakurabans.api.settings.ValueOrigin;
import org.jspecify.annotations.Nullable;

public final class SakuraSettings {

    private SakuraSettings() {
    }

    public record Field(String key, SettingType type, Object defaultValue, String description,
            List<String> options, @Nullable Double minOrNull, @Nullable Double maxOrNull,
            boolean networkWide, boolean editable, boolean restartRequired)
            implements SettingField {

        public Field {
            options = List.copyOf(options);
        }

        @Override
        public Optional<Double> min() {
            return Optional.ofNullable(this.minOrNull);
        }

        @Override
        public Optional<Double> max() {
            return Optional.ofNullable(this.maxOrNull);
        }
    }

    public record Snapshot(long version, Map<String, Object> values,
            Map<String, ValueOrigin> origins) implements SettingsSnapshot {

        public Snapshot {
            values = Map.copyOf(values);
            origins = Map.copyOf(origins);
        }
    }

    public record Message(String key, List<String> placeholders, String defaultText,
            String description) implements MessageField {

        public Message {
            placeholders = List.copyOf(placeholders);
        }
    }

    public record Messages(long version, Locale locale, Map<String, String> texts,
            Map<String, ValueOrigin> origins) implements MessageSnapshot {

        public Messages {
            texts = Map.copyOf(texts);
            origins = Map.copyOf(origins);
        }
    }

}
