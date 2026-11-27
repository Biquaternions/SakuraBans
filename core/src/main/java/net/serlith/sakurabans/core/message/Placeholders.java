package net.serlith.sakurabans.core.message;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.model.PlayerCounts;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jspecify.annotations.Nullable;

public final class Placeholders {

    public static final List<String> PLAYER_COUNT_TAGS = List.of("player_bans", "player_mutes",
            "player_warns", "player_kicks", "player_active_warns");

    public static final Set<String> PLAYER_COUNT_KEYS = Set.of("screen.ban",
            "screen.ban_temporary", "screen.kick", "victim.banned_admitted",
            "victim.banned_admitted_temporary", "victim.muted", "victim.muted_temporary",
            "victim.warned", "victim.warned_away", "dialog.muted_title", "dialog.warned_title",
            "chat.muted", "chat.muted_temporary", "chat.command_muted", "notify.ban",
            "notify.mute", "notify.warn", "notify.kick", "notify.unban", "notify.unmute",
            "notify.unwarn", "notify.attempt_join", "notify.attempt_chat",
            "notify.alts_auto_banned", "punish.ban", "punish.mute", "punish.warn", "punish.kick",
            "punish.failure.already_punished", "check.detail", "list.entry", "void.done",
            "delete.confirm", "delete.done");

    public static final String COUNTRY_TAG = "country";

    public static final Set<String> COUNTRY_KEYS = Set.of("notify.attempt_join",
            "notify.attempt_geoip", "notify.alts.join", "alts.entry", "iphistory.entry",
            "check.detail", "screen.geoip", "geoip.result");

    private final MessageService messages;
    private final Formats formats;

    public Placeholders(final MessageService messages, final Formats formats) {
        this.messages = messages;
        this.formats = formats;
    }

    /**
     * {@code <victim> <operator> <reason> <duration> <remaining> <expires> <id> <display_id>
     * <scope> <type> <server> <revoker> <revoke_reason> <created>}.
     *
     * @param names the last known name of an account, from memory (never a lookup)
     * @param showAddresses whether the viewer may see addresses uncensored
     */
    public TagResolver punishment(final Punishment punishment, final Locale locale,
            final Instant now, final Function<UUID, Optional<String>> names,
            final boolean showAddresses) {
        final String revoker = punishment.end().flatMap(end -> end.actor())
                .map(Actor::operator).map(operator -> this.operator(operator, locale)).orElse("");
        final String revokeReason = punishment.end().flatMap(end -> end.reason()).orElse("");
        return TagResolver.resolver(
                Placeholder.unparsed("victim",
                        this.victim(punishment.victim(), locale, names, showAddresses)),
                Placeholder.unparsed("operator", this.operator(punishment.operator(), locale)),
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.unparsed("duration", this.formats.length(punishment, locale)),
                Placeholder.unparsed("remaining", this.formats.remaining(punishment, now, locale)),
                Placeholder.unparsed("expires", this.formats.expiry(punishment, locale)),
                Placeholder.unparsed("created",
                        this.formats.instant(punishment.createdAt(), locale)),
                Placeholder.unparsed("id", Long.toString(punishment.id())),
                Placeholder.unparsed("display_id", punishment.displayId()),
                Placeholder.component("scope", this.messages.render(
                        scopeKey(punishment.scope()), locale,
                        Placeholder.unparsed("name", scopeName(punishment.scope())))),
                Placeholder.component("type",
                        this.messages.render(typeKey(punishment.type()), locale)),
                Placeholder.unparsed("server", punishment.originServer().orElse("")),
                Placeholder.unparsed("revoker", revoker),
                Placeholder.unparsed("revoke_reason", revokeReason));
    }

    public TagResolver playerCounts(final @Nullable PlayerCounts counts,
            final boolean shadowMutes, final Instant now) {
        return TagResolver.resolver(PLAYER_COUNT_TAGS.stream()
                .map(name -> Placeholder.unparsed(name, playerCount(counts, name, shadowMutes,
                        now)))
                .toList());
    }

    public static String playerCount(final @Nullable PlayerCounts counts, final String name,
            final boolean shadowMutes, final Instant now) {
        if (counts == null) {
            return "";
        }
        final long value = switch (name) {
            case "player_bans" -> counts.bans();
            case "player_mutes" -> shadowMutes ? counts.mutes() : counts.visibleMutes();
            case "player_warns" -> counts.warns();
            case "player_kicks" -> counts.kicks();
            case "player_active_warns" -> counts.activeWarns(now);
            default -> throw new IllegalArgumentException("not a player count: " + name);
        };
        return Long.toString(value);
    }

    public static TagResolver country(final @Nullable String name) {
        return Placeholder.unparsed(COUNTRY_TAG, name == null ? "" : name);
    }

    public static Optional<InetAddress> victimAddress(final Punishment punishment) {
        return switch (punishment.victim()) {
            case AddressVictim address -> Optional.of(address.address());
            case CompositeVictim composite -> Optional.of(composite.address());
            case PlayerVictim player -> Optional.empty();
            case ErasedAddressVictim erased -> Optional.empty();
            case AddressRangeVictim range -> Optional.empty();
        };
    }

    public static Optional<UUID> countedPlayer(final Punishment punishment) {
        return switch (punishment.victim()) {
            case PlayerVictim player -> Optional.of(player.uuid());
            case CompositeVictim composite -> Optional.of(composite.uuid());
            case ErasedAddressVictim erased -> erased.uuid();
            case AddressVictim address -> Optional.empty();
            case AddressRangeVictim range -> Optional.empty();
        };
    }

    public String operator(final Operator operator, final Locale locale) {
        return switch (operator) {
            case ConsoleOperator console ->
                    this.messages.renderPlain(MessageKey.OPERATOR_CONSOLE, locale);
            case PlayerOperator player -> player.name();
            case CustomOperator custom -> custom.name();
        };
    }

    public String victim(final Victim victim, final Locale locale,
            final Function<UUID, Optional<String>> names, final boolean showAddresses) {
        return switch (victim) {
            case PlayerVictim player -> name(player.uuid(), names);
            case AddressVictim address -> address(address.address(), showAddresses);
            case CompositeVictim composite -> name(composite.uuid(), names) + " ("
                    + address(composite.address(), showAddresses) + ")";
            case AddressRangeVictim range -> showAddresses
                    ? range.network().getHostAddress() + "/" + range.prefixLength()
                    : InetAddresses.censor(range.network()) + "/" + range.prefixLength();
            case ErasedAddressVictim erased -> {
                final String text =
                        this.messages.renderPlain(MessageKey.VICTIM_ERASED_ADDRESS, locale);
                yield erased.uuid().map(uuid -> name(uuid, names) + " (" + text + ")")
                        .orElse(text);
            }
        };
    }

    private static String name(final UUID uuid, final Function<UUID, Optional<String>> names) {
        return names.apply(uuid).orElse(uuid.toString());
    }

    private static String address(final InetAddress address, final boolean showAddresses) {
        return showAddresses ? address.getHostAddress() : InetAddresses.censor(address);
    }

    private static MessageKey scopeKey(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> MessageKey.SCOPE_GLOBAL;
            case ServerScope server -> MessageKey.SCOPE_SERVER;
            case CategoryScope category -> MessageKey.SCOPE_CATEGORY;
        };
    }

    private static String scopeName(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> "";
            case ServerScope server -> server.server();
            case CategoryScope category -> category.category();
        };
    }

    public static MessageKey typeKey(final PunishmentType type) {
        return switch (type) {
            case BAN -> MessageKey.TYPE_BAN;
            case MUTE -> MessageKey.TYPE_MUTE;
            case WARN -> MessageKey.TYPE_WARN;
            case KICK -> MessageKey.TYPE_KICK;
        };
    }
}
