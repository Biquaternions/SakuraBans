package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import org.jspecify.annotations.Nullable;

/**
 * One application of a template ({@code sb_template_uses}). {@code victim} is whom the
 * template was applied to, before an {@code ip} template turned it into an address; {@code step}
 * is null for a preset; {@code variant} is the variant's scope, null for the global definition.
 */
public record NewTemplateUse(String template, @Nullable Integer step, Victim victim,
        Actor actor, Instant createdAt, @Nullable Scope variant) {

    public NewTemplateUse {
        Preconditions.checkNotNull(template, "template");
        Preconditions.checkArgument(step == null || step >= 0, "step must not be negative");
        Preconditions.checkNotNull(victim, "victim");
        Preconditions.checkNotNull(actor, "actor");
        Preconditions.checkNotNull(createdAt, "createdAt");
        if (variant instanceof GlobalScope) {
            variant = null;
        }
    }

    public NewTemplateUse(final String template, final @Nullable Integer step,
            final Victim victim, final Actor actor, final Instant createdAt) {
        this(template, step, victim, actor, createdAt, null);
    }
}
