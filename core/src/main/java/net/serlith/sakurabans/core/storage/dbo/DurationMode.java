package net.serlith.sakurabans.core.storage.dbo;

import java.time.Duration;
import java.util.Objects;
import net.serlith.sakurabans.api.template.TemplateDuration;
import org.jspecify.annotations.Nullable;

/** A {@link TemplateDuration} in two columns: this mode and {@code duration_ms}. */
public enum DurationMode {
    OPEN,
    PERMANENT,
    FIXED;

    public static DurationMode of(final TemplateDuration duration) {
        return switch (duration) {
            case TemplateDuration.Open open -> OPEN;
            case TemplateDuration.Permanent permanent -> PERMANENT;
            case TemplateDuration.Fixed fixed -> FIXED;
        };
    }

    public TemplateDuration duration(final @Nullable Duration length) {
        return switch (this) {
            case OPEN -> TemplateDuration.open();
            case PERMANENT -> TemplateDuration.permanent();
            case FIXED -> TemplateDuration.fixed(Objects.requireNonNull(length,
                    "duration_ms"));
        };
    }
}
