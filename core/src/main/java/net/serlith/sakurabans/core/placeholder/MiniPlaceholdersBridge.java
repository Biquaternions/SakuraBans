package net.serlith.sakurabans.core.placeholder;

import io.github.miniplaceholders.api.Expansion;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.serlith.sakurabans.core.util.BuildConstants;

public final class MiniPlaceholdersBridge implements PlaceholderBridges.Registration {

    private final Expansion expansion;

    private MiniPlaceholdersBridge(final Expansion expansion) {
        this.expansion = expansion;
    }

    /** Declared as a {@link PlaceholderBridges.Registration} so callers never name this class. */
    public static PlaceholderBridges.Registration register(final PlaceholderValues values) {
        final Expansion.Builder builder = Expansion.builder(PlaceholderValues.PREFIX)
                .author("Biquaternions")
                .version(BuildConstants.VERSION);
        for (final String name : PlaceholderValues.AUDIENCE) {
            builder.audiencePlaceholder(Audience.class, name, (audience, queue, context) ->
                    text(uuidOf(audience).flatMap(uuid -> values.audience(uuid, name))
                            .orElse("")));
        }
        for (final String name : PlaceholderValues.GLOBAL) {
            builder.globalPlaceholder(name, (queue, context) ->
                    text(values.global(name).orElse("")));
        }
        final Expansion expansion = builder.build();
        expansion.register();
        return new MiniPlaceholdersBridge(expansion);
    }

    private static Optional<UUID> uuidOf(final Audience audience) {
        return audience.get(Identity.UUID);
    }

    private static Tag text(final String value) {
        return Tag.selfClosingInserting(Component.text(value));
    }

    @Override
    public void unregister() {
        if (this.expansion.registered()) {
            this.expansion.unregister();
        }
    }
}
