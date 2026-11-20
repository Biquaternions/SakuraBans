package net.serlith.sakurabans.core.enforce;

import com.google.common.base.Preconditions;
import net.kyori.adventure.text.Component;

public sealed interface ChatDecision {

    record Allowed() implements ChatDecision {
    }

    record Denied(Component message) implements ChatDecision {
        public Denied {
            Preconditions.checkNotNull(message, "message");
        }
    }

    record ShadowMuted() implements ChatDecision {
    }

}
