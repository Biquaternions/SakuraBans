package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.AltAutoBan;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.model.ActorSources;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;

public final class AltAutoBans {

    /** The source of every automatic ban: how they are recognised and rolled back. */
    public static final String SOURCE = ActorSources.AUTO;
    static final String ACCEPTED = "alts_auto_ban_accepted";

    private final ConfigService config;
    private final AltService alts;
    private final AltPolicy policy;
    private final SakuraPunishmentService punishments;
    private final ExemptionService exemption;
    private final MetaRepository meta;
    private final SakuraEventBus events;
    private final NotificationService notifications;
    private final SyncService sync;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public AltAutoBans(final ConfigService config, final AltService alts, final AltPolicy policy,
            final SakuraPunishmentService punishments, final ExemptionService exemption,
            final MetaRepository meta, final SakuraEventBus events,
            final NotificationService notifications, final SyncService sync,
            final InstantSource clock, final ComponentLogger logger) {
        this.config = config;
        this.alts = alts;
        this.policy = policy;
        this.punishments = punishments;
        this.exemption = exemption;
        this.meta = meta;
        this.events = events;
        this.notifications = notifications;
        this.sync = sync;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        this.subscriptions.add(this.events.subscribe(SakuraPunishmentCreatedEvent.class,
                EventPriority.MONITOR, event -> this.banned(event.punishment())));
    }

    public void stop() {
        this.subscriptions.forEach(Subscription::unsubscribe);
        this.subscriptions.clear();
    }

    public CompletableFuture<Boolean> accepted() {
        return this.meta.get(ACCEPTED).thenApply(Optional::isPresent);
    }

    /** True when a mode is set but {@code /sb accept alts} hasn't been run yet. */
    public CompletableFuture<Boolean> pending() {
        if (this.config.settings().alts().autoBan() == AltAutoBan.DISABLED) {
            return CompletableFuture.completedFuture(false);
        }
        return this.accepted().thenApply(accepted -> !accepted);
    }

    /** Stores the acceptance; false when it was already given. */
    public CompletableFuture<Boolean> accept() {
        return this.meta.putIfAbsent(ACCEPTED, this.clock.instant().toString());
    }

    /**
     * How many accounts a ban of {@code player} would also ban, when more than
     * {@code alts.auto-ban-confirm-above} and the feature is on; empty otherwise.
     */
    public CompletableFuture<OptionalInt> needsConfirmation(final UUID player) {
        final Settings.Alts settings = this.config.settings().alts();
        if (settings.autoBan() == AltAutoBan.DISABLED) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
        return this.accepted().thenCompose(accepted -> !accepted
                ? CompletableFuture.completedFuture(OptionalInt.empty())
                : this.candidates(player).thenApply(found ->
                        found.size() > settings.autoBanConfirmAbove()
                                ? OptionalInt.of(found.size())
                                : OptionalInt.empty()));
    }

    private CompletableFuture<List<AltEntry>> candidates(final UUID player) {
        final Settings settings = this.config.settings();
        return this.alts.alts(player, settings.enforcement().addressStrictness())
                .thenApply(found -> AltService.visible(found, settings.alts().hiddenPlayers())
                        .stream()
                        .filter(alt -> !this.policy.allowedAccounts()
                                .contains(alt.account().uuid()))
                        .toList());
    }

    private void banned(final Punishment ban) {
        if (ban.type() != PunishmentType.BAN || SOURCE.equals(ban.source())
                || this.config.settings().alts().autoBan() == AltAutoBan.DISABLED) {
            return;
        }
        final UUID player = switch (ban.victim()) {
            case PlayerVictim victim -> victim.uuid();
            case CompositeVictim victim -> victim.uuid();
            default -> null;
        };
        if (player == null) {
            return;
        }
        Futures.logged(this.accepted().thenCompose(accepted -> !accepted
                        ? CompletableFuture.completedFuture(null)
                        : this.candidates(player).thenCompose(found -> this.banAll(ban, found))),
                this.logger, "ban the linked accounts of #" + ban.id());
    }

    private CompletableFuture<Void> banAll(final Punishment ban, final List<AltEntry> found) {
        final List<CompletableFuture<Optional<String>>> bans = new ArrayList<>();
        for (final AltEntry alt : found) {
            bans.add(this.banOne(ban, alt.account().uuid(), alt.account().name()));
        }
        return Futures.allOf(bans).thenAccept(results -> {
            final List<String> names = results.stream().flatMap(Optional::stream).toList();
            this.announce(ban, names);
        });
    }

    private void announce(final Punishment ban, final List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        // One summary, here and on the other instances, instead of one notice per account.
        this.notifications.altsAutoBanned(ban, names);
        this.sync.publish(new SyncPacket.AltsAutoBannedPacket(ban.id(), names));
    }

    /** The account's name when it was banned; empty when skipped (exempt, already banned). */
    private CompletableFuture<Optional<String>> banOne(final Punishment ban, final UUID account,
            final String name) {
        final Actor actor = Actor.of(ban.operator(), SOURCE);
        return this.exemption.isExempt(actor, account, PunishmentType.BAN)
                .thenCompose(exempt -> {
                    if (exempt) {
                        return CompletableFuture.completedFuture(Optional.<String>empty());
                    }
                    final Instant now = this.clock.instant();
                    final Duration remaining = ban.expiresAt()
                            .map(expiry -> Duration.between(now, expiry))
                            .orElse(null);
                    if (remaining != null && !remaining.isPositive()) {
                        return CompletableFuture.completedFuture(Optional.<String>empty());
                    }
                    final DraftPunishment draft = DraftPunishment.builder(PunishmentType.BAN,
                                    PlayerVictim.of(account), actor)
                            .scope(ban.scope())
                            .reason(ban.reason())
                            .duration(remaining)
                            .build();
                    // The first ban was within the operator's limits; its copies are too.
                    return this.punishments.punish(draft, PunishOptions.builder()
                                    .visibility(ban.visibility())
                                    .bypassLimits(true)
                                    .build())
                            .thenApply(result -> result instanceof PunishResult.Success
                                    ? Optional.of(name)
                                    : Optional.<String>empty());
                });
    }

    /**
     * {@code ON_JOIN}: after the login check found {@code bans} for a joining account, an
     * address or composite ban of someone else that reaches it bans the account itself. Runs
     * after the decision, never waited for: the join is already denied by that ban.
     */
    public void joined(final LoginAttempt attempt, final List<Punishment> bans) {
        final Settings settings = this.config.settings();
        if (settings.alts().autoBan() != AltAutoBan.ON_JOIN || bans.isEmpty()) {
            return;
        }
        final boolean own = bans.stream().anyMatch(ban -> switch (ban.victim()) {
            case PlayerVictim victim -> victim.uuid().equals(attempt.uuid());
            case CompositeVictim victim -> victim.uuid().equals(attempt.uuid());
            default -> false;
        });
        final Punishment through = bans.stream()
                .filter(ban -> ban.victim() instanceof AddressVictim
                        || ban.victim() instanceof CompositeVictim)
                .findFirst()
                .orElse(null);
        if (own || through == null || this.policy.allowedAccounts().contains(attempt.uuid())
                || settings.alts().hiddenPlayers().hidesPlayer(attempt.uuid(), attempt.name())) {
            return;
        }
        Futures.logged(this.accepted().thenCompose(accepted -> !accepted
                        ? CompletableFuture.completedFuture(null)
                        : this.banOne(through, attempt.uuid(), attempt.name())
                                .thenAccept(name -> this.announce(through,
                                        name.stream().toList()))),
                this.logger, "ban " + attempt.name() + " for joining under #" + through.id());
    }
}
