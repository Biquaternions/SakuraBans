package net.serlith.sakurabans.core.enforce;

import com.google.common.base.Preconditions;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.event.PlayerDenyEvent.Denial;
import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public sealed interface LoginDecision {

    record Allowed() implements LoginDecision {
    }

    record Denied(Component screen, Denial denial, @Nullable Punishment punishment,
            @Nullable String fallbackOrNull) implements LoginDecision {
        public Denied {
            Preconditions.checkNotNull(screen, "screen");
            Preconditions.checkNotNull(denial, "denial");
        }

        public Denied(final Component screen, final Denial denial,
                final @Nullable Punishment punishment) {
            this(screen, denial, punishment, null);
        }

        public Optional<String> fallback() {
            return Optional.ofNullable(this.fallbackOrNull);
        }

    }

}
