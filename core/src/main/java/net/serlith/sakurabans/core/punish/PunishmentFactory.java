package net.serlith.sakurabans.core.punish;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
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
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.model.SakuraKind;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import org.jspecify.annotations.Nullable;

public final class PunishmentFactory {

    private final SakuraScopeService scopes;
    private final ConfigService config;
    private final PlayerRepository players;
    private final Platform platform;
    private final InstantSource clock;
    private final TemplateResolver templates;
    private final KindLookup kinds;

    public PunishmentFactory(final SakuraScopeService scopes, final ConfigService config,
            final PlayerRepository players, final Platform platform, final InstantSource clock,
            final TemplateResolver templates, final KindLookup kinds) {
        this.scopes = scopes;
        this.config = config;
        this.players = players;
        this.platform = platform;
        this.clock = clock;
        this.templates = templates;
        this.kinds = kinds;
    }

    public sealed interface Prepared {

        record Ready(NewPunishment punishment, SakuraKind kind) implements Prepared {
        }

        record Refused(Reason reason) implements Prepared {
        }
    }

    public CompletableFuture<Prepared> prepare(final DraftPunishment draft,
            final PunishOptions options) {
        final String name = draft.template().orElse(null);
        if (name == null) {
            return this.prepare(draft, options, null);
        }
        return this.templates.resolve(name, draft.victim(), draft.actor()).thenCompose(found -> {
            if (found.isEmpty() || found.get().type() != draft.type()) {
                return refused(Reason.UNKNOWN_TEMPLATE);
            }
            if (draft.kind().isPresent() && !draft.kind().get().equals(found.get().kind())) {
                return refused(Reason.UNKNOWN_KIND);
            }
            return this.prepare(draft, options, found.get());
        });
    }

    private CompletableFuture<Prepared> prepare(final DraftPunishment draft,
            final PunishOptions options, final TemplateResolver.@Nullable Resolution template) {
        final String kindId = template != null ? template.kind()
                : draft.kind().orElseGet(() -> BuiltInKinds.defaultFor(draft.type()));
        final Optional<SakuraKind> found = this.kinds.find(kindId)
                .filter(kind -> kind.baseType() == draft.type());
        if (found.isEmpty()) {
            return refused(Reason.UNKNOWN_KIND);
        }
        final SakuraKind kind = found.get();
        final Settings settings = this.config.settings();
        final Duration duration = template != null ? template.duration()
                : draft.duration().orElse(draft.type() == PunishmentType.WARN
                        && !kind.permanentOnly() ? settings.warnings().expireAfter() : null);
        final Validator errors = Validator.create();
        errors.check(!kind.permanentOnly() || duration == null, "duration",
                Code.NOT_ALLOWED, kind.id() + " is always permanent");
        try {
            errors.throwIfAny();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        draft.evidence().forEach(ref -> evidence.put(ref.uri(), ref));
        if (template != null) {
            template.captured().forEach(ref -> evidence.putIfAbsent(ref.uri(), ref));
        }
        final Set<String> evidenceTypes = evidence.values().stream()
                .flatMap(ref -> ref.type().stream())
                .collect(Collectors.toSet());
        final Set<String> required = new HashSet<>(kind.requiredEvidence());
        if (template != null) {
            required.addAll(template.requiredEvidence());
        }
        if (!evidenceTypes.containsAll(required)) {
            return refused(Reason.EVIDENCE_REQUIRED);
        }
        final Optional<String> reason = draft.reason().map(String::strip)
                .filter(text -> !text.isEmpty())
                .or(() -> Optional.ofNullable(template).map(TemplateResolver.Resolution::reason)
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
                .or(kind::defaultScope)
                .orElseGet(() -> this.scopes.defaultScope(
                        draft.actor().operator() instanceof PlayerOperator player
                                ? player.uuid()
                                : null));
        final Instant now = this.clock.instant();
        return this.victim(draft.victim(), kind).thenApply(victim -> new Prepared.Ready(
                new NewPunishment(draft.type(), kind.id(), kind.shadow(),
                        kind.strictnessOrNull(), victim, scope, text, draft.actor(), now,
                        duration == null ? null : now.plus(duration),
                        this.scopes.originServer().orElse(null),
                        options.visibility(), template == null ? null : template.name(),
                        template == null ? null : template.step(),
                        List.copyOf(evidence.values()), null),
                kind));
    }

    public static DraftPunishment asDraft(final NewPunishment prepared) {
        return DraftPunishment.builder(prepared.type(), prepared.victim(), prepared.actor())
                .kind(prepared.kind())
                .scope(prepared.scope())
                .reason(prepared.reason())
                .duration(prepared.expiresAt() == null ? null
                        : Duration.between(prepared.createdAt(), prepared.expiresAt()))
                .evidence(prepared.evidence())
                .build();
    }

    private CompletableFuture<Victim> victim(final Victim given, final SakuraKind kind) {
        return switch (kind.addressMode()) {
            case AS_GIVEN -> CompletableFuture.completedFuture(given);
            case ADDRESS -> switch (given) {
                case PlayerVictim player -> this.address(player.uuid())
                        .thenApply(address -> (Victim) AddressVictim.of(address));
                case CompositeVictim composite ->
                        CompletableFuture.completedFuture((Victim) AddressVictim.of(
                                composite.address()));
                default -> CompletableFuture.completedFuture(given);
            };
            case COMPOSITE -> given instanceof PlayerVictim player
                    ? this.address(player.uuid())
                            .thenApply(address -> (Victim) CompositeVictim.of(player.uuid(),
                                    address))
                    : CompletableFuture.completedFuture(given);
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
