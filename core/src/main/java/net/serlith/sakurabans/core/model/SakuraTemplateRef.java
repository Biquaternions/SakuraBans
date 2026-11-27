package net.serlith.sakurabans.core.model;

import java.util.Optional;
import java.util.OptionalInt;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.TemplateRef;
import org.jspecify.annotations.Nullable;

/**
 * {@code stepOrNull}: the ladder step, null for a preset. {@code variantOrNull}: the variant's
 * scope, null for the global definition.
 */
public record SakuraTemplateRef(String name, @Nullable Integer stepOrNull,
        @Nullable Scope variantOrNull) implements TemplateRef {

    public SakuraTemplateRef {
        name = ModelChecks.identifier(name, "name");
        if (stepOrNull != null && stepOrNull < 0) {
            throw new IllegalArgumentException("step must not be negative: " + stepOrNull);
        }
        if (variantOrNull instanceof GlobalScope) {
            variantOrNull = null;
        }
    }

    public SakuraTemplateRef(final String name, final @Nullable Integer stepOrNull) {
        this(name, stepOrNull, null);
    }

    @Override
    public OptionalInt step() {
        return this.stepOrNull == null ? OptionalInt.empty() : OptionalInt.of(this.stepOrNull);
    }

    @Override
    public Optional<Scope> variant() {
        return Optional.ofNullable(this.variantOrNull);
    }
}
