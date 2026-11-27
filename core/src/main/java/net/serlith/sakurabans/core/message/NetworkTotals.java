package net.serlith.sakurabans.core.message;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.core.model.PunishmentTotals;
import org.jspecify.annotations.Nullable;

public final class NetworkTotals {

    public static final List<String> TAGS = List.of("total_bans", "total_mutes", "total_warns",
            "total_kicks", "active_bans", "active_mutes", "active_warns");

    private record State(@Nullable PunishmentTotals totals, TagResolver tags) {
    }

    private final AtomicReference<State> state = new AtomicReference<>(new State(null,
            resolver(null)));

    public Optional<PunishmentTotals> current() {
        return Optional.ofNullable(this.state.get().totals());
    }

    public void update(final PunishmentTotals totals) {
        this.state.set(new State(totals, resolver(totals)));
    }

    public TagResolver tags() {
        return this.state.get().tags();
    }

    public static String value(final @Nullable PunishmentTotals totals, final String name) {
        if (totals == null) {
            return "";
        }
        final long value = switch (name) {
            case "total_bans" -> totals.totalBans();
            case "total_mutes" -> totals.totalMutes();
            case "total_warns" -> totals.totalWarns();
            case "total_kicks" -> totals.totalKicks();
            case "active_bans" -> totals.activeBans();
            case "active_mutes" -> totals.activeMutes();
            case "active_warns" -> totals.activeWarns();
            default -> throw new IllegalArgumentException("not a total: " + name);
        };
        return Long.toString(value);
    }

    private static TagResolver resolver(final @Nullable PunishmentTotals totals) {
        return TagResolver.resolver(TAGS.stream()
                .map(name -> Placeholder.unparsed(name, value(totals, name)))
                .toList());
    }
}
