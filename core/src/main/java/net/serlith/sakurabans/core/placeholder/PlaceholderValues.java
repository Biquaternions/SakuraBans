package net.serlith.sakurabans.core.placeholder;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.NetworkTotals;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentCounts;

public final class PlaceholderValues {

    public static final String PREFIX = "sakurabans";

    /** Placeholders about the player they are evaluated for. */
    public static final List<String> AUDIENCE = Stream.concat(Stream.of("muted",
            "mute_reason", "mute_remaining", "mute_expires"),
            Placeholders.PLAYER_COUNT_TAGS.stream()).toList();

    /** The network's counts; also available with an audience. */
    public static final List<String> GLOBAL = NetworkTotals.TAGS;

    private final MuteCache mutes;
    private final PunishmentCounts counts;
    private final Formats formats;
    private final MessageService messages;
    private final Platform platform;
    private final InstantSource clock;

    public PlaceholderValues(final MuteCache mutes, final PunishmentCounts counts,
            final Formats formats, final MessageService messages, final Platform platform,
            final InstantSource clock) {
        this.mutes = mutes;
        this.counts = counts;
        this.formats = formats;
        this.messages = messages;
        this.platform = platform;
        this.clock = clock;
    }

    public Optional<String> global(final String name) {
        if (!GLOBAL.contains(name)) {
            return Optional.empty();
        }
        return Optional.of(NetworkTotals.value(this.counts.totals().orElse(null), name));
    }

    public Optional<String> audience(final UUID player, final String name) {
        if (GLOBAL.contains(name)) {
            return this.global(name);
        }
        if (!AUDIENCE.contains(name)) {
            return Optional.empty();
        }
        final Instant now = this.clock.instant();
        if (Placeholders.PLAYER_COUNT_TAGS.contains(name)) {
            return Optional.of(Placeholders.playerCount(this.counts.cached(player).orElse(null),
                    name, false, now));
        }
        final MuteCache.Lookup lookup = this.mutes.lookupVisible(player);
        if (lookup instanceof MuteCache.Lookup.Unknown) {
            return Optional.of("");
        }
        final Punishment mute = lookup instanceof MuteCache.Lookup.Muted(Punishment found)
                ? found : null;
        if (name.equals("muted")) {
            return Optional.of(Boolean.toString(mute != null));
        }
        if (mute == null) {
            return Optional.of("");
        }
        final Locale locale = this.platform.player(player).flatMap(OnlinePlayer::locale)
                .orElseGet(this.messages::defaultLocale);
        return Optional.of(switch (name) {
            case "mute_reason" -> mute.reason();
            case "mute_remaining" -> this.formats.remaining(mute, now, locale);
            case "mute_expires" -> this.formats.expiry(mute, locale);
            default -> throw new IllegalStateException("unhandled placeholder " + name);
        });
    }
}
