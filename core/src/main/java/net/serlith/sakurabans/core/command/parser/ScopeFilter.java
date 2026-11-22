package net.serlith.sakurabans.core.command.parser;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public record ScopeFilter(@Nullable Scope scopeOrNull) {

    public static final ScopeFilter ALL = new ScopeFilter(null);

    public static ScopeFilter of(final Scope scope) {
        return new ScopeFilter(scope);
    }

    public Optional<Scope> scope() {
        return Optional.ofNullable(this.scopeOrNull);
    }
}
