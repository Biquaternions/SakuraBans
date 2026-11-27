package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import org.jspecify.annotations.Nullable;

/** Made by {@link TemplateBuilder}, which validates it. */
public record SakuraPresetTemplate(
        String name,
        PunishmentType type,
        TemplateDuration duration,
        boolean ip,
        boolean shadow,
        @Nullable Scope scopeOrNull,
        String reason,
        boolean permissionRequired,
        List<String> actions,
        Map<String, Integer> groups,
        @Nullable String messageOrNull,
        TemplateDisplay display,
        boolean silent,
        @Nullable Scope variantOrNull) implements PresetTemplate {

    public SakuraPresetTemplate {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(duration, "duration");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(display, "display");
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
    }

    @Override
    public Optional<Scope> variant() {
        return Optional.ofNullable(this.variantOrNull);
    }

    @Override
    public Optional<Scope> scope() {
        return Optional.ofNullable(this.scopeOrNull);
    }

    @Override
    public Optional<String> message() {
        return Optional.ofNullable(this.messageOrNull);
    }
}
