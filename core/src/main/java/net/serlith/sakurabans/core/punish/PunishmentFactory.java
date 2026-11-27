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
import net.serlith.sakurabans.api.model.Actor;
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
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult.Failure.Reason;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.NewTemplateUse;
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

    /** Where a prepared punishment comes from (S64): a ladder step isn't clipped by limits. */
    public enum Origin { PLAIN, PRESET, LADDER_STEP }

    public sealed interface Prepared {

        /**
         * {@code use} and {@code template}: the template use to record with the punishment and
         * the definition that was applied; both null for a plain punishment, never otherwise.
         */
        record Ready(NewPunishment punishment, Origin origin, @Nullable NewTemplateUse use,
                @Nullable TemplateInfo template) implements Prepared {

            public Ready {
                Preconditions.checkArgument((origin == Origin.PLAIN) == (use == null)
                        && (use == null) == (template == null),
                        "a use and its template come with a preset or a ladder step only");
            }

            public Ready withPunishment(final NewPunishment changed) {
                return new Ready(changed, this.origin, this.use, this.template);
            }
        }

        record Refused(Reason reason) implements Prepared {
        }
    }

    public Optional<Scope> requestedScope(final DraftPunishment draft) {
        return draft.scope().or(() -> draft.template()
                .flatMap(name -> this.templates.resolve(name, null, draft.actor()))
                .map(StoredTemplate::template)
                .filter(template -> template instanceof PresetTemplate preset
                        && preset.type() == draft.type())
                .flatMap(TemplateInfo::scope));
    }

    /**
     * The preset whose node the draft needs (S63): its name when the definition in force has
     * {@code permission-required} and fits the draft's type, else null (the factory refuses a
     * preset that doesn't fit).
     */
    public @Nullable String requiredTemplate(final DraftPunishment draft) {
        return draft.template()
                .flatMap(name -> this.templates.resolve(name, draft.scope().orElse(null),
                        draft.actor()))
                .map(StoredTemplate::template)
                .filter(template -> template.permissionRequired()
                        && TemplateKind.of(template).type().orElse(null) == draft.type())
                .map(TemplateInfo::name)
                .orElse(null);
    }

    public CompletableFuture<Prepared> prepare(final DraftPunishment draft,
            final PunishOptions options) {
        if (draft.victim() instanceof ErasedAddressVictim) {
            return erased();
        }
        final String name = draft.template().orElse(null);
        final PresetTemplate preset;
        if (name == null) {
            preset = null;
        } else {
            // The variant in force where it is issued (S62).
            final StoredTemplate found = this.templates.resolve(name,
                    draft.scope().orElse(null), draft.actor()).orElse(null);
            if (found == null) {
                return refused(Reason.UNKNOWN_TEMPLATE);
            }
            if (!(found.template() instanceof PresetTemplate given)
                    || given.type() != draft.type()) {
                return refused(Reason.TEMPLATE_TYPE_MISMATCH);
            }
            if (draft.duration().isPresent()
                    && !(given.duration() instanceof TemplateDuration.Open)) {
                return invalid("duration", Code.EXCLUSIVE, "the template " + given.name()
                        + " decides the duration; don't give one");
            }
            preset = given;
        }
        return this.prepare(new Input(draft.type(), draft.victim(), draft.actor(),
                draft.scope().orElse(null), draft.reason().orElse(null),
                draft.duration().orElse(null), draft.shadow(), draft.evidence(), preset,
                null, null), options);
    }

    /** A ladder's step with a punishment (D37): the step decides type and duration. */
    public CompletableFuture<Prepared> prepare(final LadderRequest request,
            final LadderTemplate ladder, final int index, final PunishOptions options) {
        if (request.victim() instanceof ErasedAddressVictim) {
            return erased();
        }
        final TemplateStep step = ladder.step(index);
        Preconditions.checkArgument(step.type().isPresent(), "a step without a punishment");
        return this.prepare(new Input(step.type().orElseThrow(), request.victim(),
                request.actor(), request.scope().orElse(null), request.reason().orElse(null),
                null, false, request.evidence(), ladder, index, step), options);
    }

    private CompletableFuture<Prepared> prepare(final Input input, final PunishOptions options) {
        final TemplateInfo template = input.template();
        final TemplateStep step = input.step();
        final PunishmentType type = input.type();
        final Settings settings = this.config.settings();
        final TemplateDuration fixed = step != null ? step.duration()
                : template instanceof PresetTemplate preset ? preset.duration()
                : TemplateDuration.open();
        final Duration duration = switch (fixed) {
            case TemplateDuration.Permanent permanent -> null;
            case TemplateDuration.Fixed length -> length.length();
            case TemplateDuration.Open open -> input.duration() != null ? input.duration()
                    : type == PunishmentType.WARN ? settings.warnings().expireAfter() : null;
        };
        final Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        input.evidence().forEach(ref -> evidence.put(ref.uri(), ref));
        final Optional<String> reason = Optional.ofNullable(input.reason()).map(String::strip)
                .filter(text -> !text.isEmpty())
                .or(() -> Optional.ofNullable(step).flatMap(TemplateStep::reason))
                .or(() -> Optional.ofNullable(template).map(TemplateInfo::reason)
                        .filter(text -> !text.isBlank()));
        if (reason.isEmpty() && settings.reasons().requiredFor().contains(type)) {
            return refused(Reason.REASON_REQUIRED);
        }
        final String text = reason
                .map(given -> settings.reasons().allowNewlines()
                        ? given
                        : given.replaceAll("\\R+", " "))
                .orElse(settings.reasons().defaultReason());
        final Scope scope = Optional.ofNullable(input.scope())
                .or(() -> Optional.ofNullable(template).flatMap(TemplateInfo::scope))
                .orElseGet(() -> this.scopes.defaultScope(
                        input.actor().operator() instanceof PlayerOperator player
                                ? player.uuid()
                                : null));
        final boolean shadow = type == PunishmentType.MUTE
                && (input.shadow() || template != null && template.shadow());
        final Instant now = this.clock.instant();
        final Scope variant = template == null ? null : template.variant().orElse(null);
        final CompletableFuture<Victim> victim = template != null && template.ip()
                ? this.address(input.victim())
                : CompletableFuture.completedFuture(input.victim());
        final Origin origin = template == null ? Origin.PLAIN
                : step == null ? Origin.PRESET : Origin.LADDER_STEP;
        return victim.thenApply(found -> new Prepared.Ready(new NewPunishment(type,
                shadow, found, scope, text, input.actor(), now,
                duration == null ? null : now.plus(duration),
                options.originServer().or(this.scopes::originServer).orElse(null),
                visibility(template, options.visibility()),
                template == null ? null : template.name(), input.index(),
                List.copyOf(evidence.values()), null, variant), origin,
                template == null ? null : new NewTemplateUse(template.name(), input.index(),
                        input.victim(), input.actor(), now, variant), template));
    }

    private static Visibility visibility(final @Nullable TemplateInfo template,
            final Visibility asked) {
        return template != null && template.silent() && asked == Visibility.NORMAL
                ? Visibility.SILENT
                : asked;
    }

    /** {@code template}, {@code index} and {@code step}: the preset or the ladder's step. */
    private record Input(PunishmentType type, Victim victim, Actor actor, @Nullable Scope scope,
            @Nullable String reason, @Nullable Duration duration, boolean shadow,
            List<EvidenceRef> evidence, @Nullable TemplateInfo template, @Nullable Integer index,
            @Nullable TemplateStep step) {
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

    private static CompletableFuture<Prepared> erased() {
        return invalid("victim", Code.NOT_ALLOWED,
                "an erased victim has no address; name the address or player instead");
    }

    private static CompletableFuture<Prepared> invalid(final String path, final Code code,
            final String message) {
        try {
            Validator.create().add(path, code, message).throwIfAny();
            throw new IllegalStateException("unreachable");
        } catch (final ValidationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<Prepared> refused(final Reason reason) {
        return CompletableFuture.completedFuture(new Prepared.Refused(reason));
    }

}
