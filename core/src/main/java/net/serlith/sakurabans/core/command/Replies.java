package net.serlith.sakurabans.core.command;

import java.net.InetAddress;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.IpBanDecay;
import net.serlith.sakurabans.core.model.PlayerCounts;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.PunishmentCounts;
import org.jspecify.annotations.Nullable;

public final class Replies {

    private final CommandRunner runner;
    private final Placeholders placeholders;
    private final NameCache names;
    private final ConfigService config;
    private final InstantSource clock;
    private final MessageService messages;
    private final PunishmentCounts counts;
    private final CountryLookup countries;

    public Replies(final CommandRunner runner, final Placeholders placeholders,
            final NameCache names, final ConfigService config, final InstantSource clock,
            final MessageService messages, final PunishmentCounts counts,
            final CountryLookup countries) {
        this.runner = runner;
        this.placeholders = placeholders;
        this.names = names;
        this.config = config;
        this.clock = clock;
        this.messages = messages;
        this.counts = counts;
        this.countries = countries;
    }

    public TagResolver punishment(final CommandActor actor, final Punishment punishment) {
        return this.withCounts(actor, punishment, Placeholders.countedPlayer(punishment)
                .flatMap(this.counts::cached).orElse(null));
    }

    public CompletableFuture<TagResolver> punishmentCounted(final CommandActor actor,
            final Punishment punishment, final MessageKey key) {
        final Optional<UUID> player = Placeholders.countedPlayer(punishment);
        if (player.isEmpty() || this.counts.cached(player.get()).isPresent()
                || !this.messages.uses(key, Placeholders.PLAYER_COUNT_TAGS)) {
            return CompletableFuture.completedFuture(this.punishment(actor, punishment));
        }
        return this.counts.fetch(player.get())
                .thenApply(found -> this.withCounts(actor, punishment, found.orElse(null)));
    }

    private TagResolver withCounts(final CommandActor actor, final Punishment punishment,
            final @Nullable PlayerCounts counts) {
        final Instant now = this.clock.instant();
        return TagResolver.resolver(this.placeholders.playerCounts(counts, true, now),
                this.placeholders.punishment(punishment,
                        this.runner.locale(actor), now, this.names::name,
                        this.showAddresses(actor)),
                Placeholder.component("decayed", IpBanDecay.decayed(punishment,
                        this.config.settings().enforcement().ipBanDecay(), now)
                        ? this.runner.render(actor, MessageKey.INFO_ADDRESS_DECAYED)
                        : Component.empty()));
    }

    public TagResolver victimCountry(final Punishment punishment) {
        return Placeholders.country(Placeholders.victimAddress(punishment)
                .flatMap(this.countries::countryName).orElse(null));
    }

    public TagResolver country(final InetAddress address) {
        return Placeholders.country(this.countries.countryName(address).orElse(null));
    }

    public String victim(final CommandActor actor, final Victim victim) {
        return this.placeholders.victim(victim, this.runner.locale(actor), this.names::name,
                this.showAddresses(actor));
    }

    public boolean showAddresses(final CommandActor actor) {
        return actor.console() || !this.config.settings().display().censorIpAddresses()
                || actor.hasPermission(Permissions.VIEW_IPS);
    }

}
