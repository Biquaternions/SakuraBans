package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateStep;
import org.jspecify.annotations.Nullable;

/** Made by {@link TemplateBuilder}, which validates it. */
public record SakuraLadderTemplate(
        String name,
        List<TemplateStep> steps,
        @Nullable Duration expiryOrNull,
        boolean addressShared,
        boolean ip,
        boolean shadow,
        @Nullable Scope scopeOrNull,
        String reason,
        boolean permissionRequired,
        List<String> actions,
        Map<String, Integer> groups,
        @Nullable String messageOrNull,
        TemplateDisplay display,
        @Nullable Scope variantOrNull) implements LadderTemplate {

    public SakuraLadderTemplate {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(display, "display");
        steps = List.copyOf(steps);
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
    }

    @Override
    public Optional<Duration> expiry() {
        return Optional.ofNullable(this.expiryOrNull);
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
