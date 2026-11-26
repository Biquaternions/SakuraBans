package net.serlith.sakurabans.core.punish;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult.Failure.Reason;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import org.jspecify.annotations.Nullable;

public final class PunishmentFactory {

    private final SakuraScopeService scopes;
    private final ConfigService config;
    private final PlayerRepository players;
    private final Platform platform;
    private final InstantSource clock;
    private final SakuraTemplateService templates;

    public PunishmentFactory(final SakuraScopeService scopes, final ConfigService config,
            final PlayerRepository players, final Platform platform, final InstantSource clock,
            final SakuraTemplateService templates) {
        this.scopes = scopes;
        this.config = config;
        this.players = players;
        this.platform = platform;
        this.clock = clock;
        this.templates = templates;
    }

    public sealed interface Prepared {

        record Ready(NewPunishment punishment) implements Prepared {
        }

        record Refused(Reason reason) implements Prepared {
        }
    }

    public Optional<Scope> requestedScope(final DraftPunishment draft) {
        return draft.scope().or(() -> draft.template()
                .flatMap(this.templates::template)
                .filter(template -> template.type() == draft.type())
                .flatMap(TemplateInfo::scope));
    }

    public CompletableFuture<Prepared> prepare(final DraftPunishment draft,
            final PunishOptions options) {
        if (draft.victim() instanceof ErasedAddressVictim) {
            try {
                Validator.create().add("victim", Code.NOT_ALLOWED,
                        "an erased victim has no address; name the address or player instead")
                        .throwIfAny();
            } catch (final ValidationException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        final String name = draft.template().orElse(null);
        if (name == null) {
            return this.prepare(draft, options, null);
        }
        return this.templates.resolve(name, draft.victim()).thenCompose(found -> {
            if (found.isEmpty() || found.get().template().type() != draft.type()) {
                return refused(Reason.UNKNOWN_TEMPLATE);
            }
            return this.prepare(draft, options, found.get());
        });
    }

    private CompletableFuture<Prepared> prepare(final DraftPunishment draft,
            final PunishOptions options,
            final SakuraTemplateService.@Nullable Resolution resolution) {
        final TemplateInfo template = resolution == null ? null : resolution.template();
        final Settings settings = this.config.settings();
        final Duration duration = resolution != null ? resolution.duration()
                : draft.duration().orElse(draft.type() == PunishmentType.WARN
                        ? settings.warnings().expireAfter() : null);
        final Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        draft.evidence().forEach(ref -> evidence.put(ref.uri(), ref));
        final Optional<String> reason = draft.reason().map(String::strip)
                .filter(text -> !text.isEmpty())
                .or(() -> Optional.ofNullable(template).map(TemplateInfo::reason)
                        .filter(text -> !text.isBlank()));
        if (reason.isEmpty() && settings.reasons().requiredFor().contains(draft.type())) {
            return refused(Reason.REASON_REQUIRED);
        }
        final String text = reason
                .map(given -> settings.reasons().allowNewlines()
                        ? given
                        : given.replaceAll("\\R+", " "))
                .orElse(settings.reasons().defaultReason());
        final Scope scope = draft.scope()
                .or(() -> Optional.ofNullable(template).flatMap(TemplateInfo::scope))
                .orElseGet(() -> this.scopes.defaultScope(
                        draft.actor().operator() instanceof PlayerOperator player
                                ? player.uuid()
                                : null));
        final boolean shadow = draft.shadow() || template != null && template.shadow();
        final Instant now = this.clock.instant();
        final CompletableFuture<Victim> victim = template != null && template.ip()
                ? this.address(draft.victim())
                : CompletableFuture.completedFuture(draft.victim());
        return victim.thenApply(found -> new Prepared.Ready(new NewPunishment(draft.type(),
                shadow, found, scope, text, draft.actor(), now,
                duration == null ? null : now.plus(duration),
                this.scopes.originServer().orElse(null), options.visibility(),
                template == null ? null : template.name(),
                resolution == null ? null : resolution.step(),
                List.copyOf(evidence.values()), null)));
    }

    public static DraftPunishment asDraft(final NewPunishment prepared) {
        return DraftPunishment.builder(prepared.type(), prepared.victim(), prepared.actor())
                .shadow(prepared.shadow())
                .scope(prepared.scope())
                .reason(prepared.reason())
                .duration(prepared.expiresAt() == null ? null
                        : Duration.between(prepared.createdAt(), prepared.expiresAt()))
                .evidence(prepared.evidence())
                .build();
    }

    private CompletableFuture<Victim> address(final Victim given) {
        return switch (given) {
            case PlayerVictim player -> this.address(player.uuid())
                    .thenApply(address -> (Victim) AddressVictim.of(address));
            case CompositeVictim composite ->
                    CompletableFuture.completedFuture(AddressVictim.of(composite.address()));
            default -> CompletableFuture.completedFuture(given);
        };
    }

    private CompletableFuture<InetAddress> address(final UUID uuid) {
        final Optional<InetAddress> online = this.platform.player(uuid).map(OnlinePlayer::address);
        return online.map(CompletableFuture::completedFuture)
                .orElseGet(() -> this.players.profile(uuid).thenApply(profile -> {
                    final InetAddress last =
                            profile.flatMap(PlayerProfileView::lastAddress).orElse(null);
                    if (last == null) {
                        Validator.create().add("victim", Code.NOT_ALLOWED,
                                "no address is known for " + uuid).throwIfAny();
                    }
                    return Preconditions.checkNotNull(last);
                }));
    }

    private static CompletableFuture<Prepared> refused(final Reason reason) {
        return CompletableFuture.completedFuture(new Prepared.Refused(reason));
    }

}
