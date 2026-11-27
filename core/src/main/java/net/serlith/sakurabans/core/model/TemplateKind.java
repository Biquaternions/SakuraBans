package net.serlith.sakurabans.core.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateInfo;
import org.jspecify.annotations.Nullable;

/** What a template is: a preset of one type, or a ladder. */
public enum TemplateKind {
    BAN(PunishmentType.BAN),
    MUTE(PunishmentType.MUTE),
    WARN(PunishmentType.WARN),
    KICK(PunishmentType.KICK),
    LADDER(null);

    private final @Nullable PunishmentType type;

    TemplateKind(final @Nullable PunishmentType type) {
        this.type = type;
    }

    /** The preset's type; empty for a ladder. */
    public Optional<PunishmentType> type() {
        return Optional.ofNullable(this.type);
    }

    public boolean ladder() {
        return this == LADDER;
    }

    /** As {@code templates.yml} writes it. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static TemplateKind of(final PunishmentType type) {
        return switch (type) {
            case BAN -> BAN;
            case MUTE -> MUTE;
            case WARN -> WARN;
            case KICK -> KICK;
        };
    }

    public static TemplateKind of(final TemplateInfo template) {
        return switch (template) {
            case PresetTemplate preset -> of(preset.type());
            case LadderTemplate ladder -> LADDER;
        };
    }

    public static Optional<TemplateKind> parse(final String text) {
        return Arrays.stream(values())
                .filter(kind -> kind.key().equalsIgnoreCase(text.strip()))
                .findFirst();
    }
}
