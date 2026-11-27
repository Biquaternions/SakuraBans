package net.serlith.sakurabans.core.model;

import java.util.UUID;
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
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import org.jspecify.annotations.Nullable;

public final class ApiValues {

    private ApiValues() {
    }

    public static Victim victim(final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> PlayerVictim.of(player.uuid());
            case AddressVictim address -> AddressVictim.of(address.address());
            case CompositeVictim composite ->
                    CompositeVictim.of(composite.uuid(), composite.address());
            case AddressRangeVictim range ->
                    AddressRangeVictim.of(range.network(), range.prefixLength());
            case ErasedAddressVictim erased ->
                    ErasedAddressVictim.of(erased.hash(), erased.uuid().orElse(null));
        };
    }

    public static Operator operator(final Operator operator) {
        return switch (operator) {
            case ConsoleOperator console -> ConsoleOperator.INSTANCE;
            case PlayerOperator player -> PlayerOperator.of(player.uuid(), player.name());
            case CustomOperator custom ->
                    CustomOperator.of(custom.name(), custom.uuid().orElse(null));
        };
    }

    public static Actor actor(final Actor actor) {
        return Actor.of(operator(actor.operator()), actor.source());
    }

    public static Scope scope(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> GlobalScope.INSTANCE;
            case ServerScope server -> ServerScope.of(server.server());
            case CategoryScope category -> CategoryScope.of(category.category());
        };
    }

    public static DraftPunishment draft(final DraftPunishment draft) {
        return DraftPunishment.builder(draft.type(), victim(draft.victim()), actor(draft.actor()))
                .shadow(draft.shadow())
                .scope(draft.scope().map(ApiValues::scope).orElse(null))
                .reason(draft.reason().orElse(null))
                .duration(draft.duration().orElse(null))
                .template(draft.template().orElse(null))
                .evidence(draft.evidence())
                .build();
    }

    public static LadderRequest ladder(final LadderRequest request) {
        return LadderRequest.builder(request.ladder(), victim(request.victim()),
                        actor(request.actor()))
                .scope(request.scope().map(ApiValues::scope).orElse(null))
                .reason(request.reason().orElse(null))
                .evidence(request.evidence())
                .build();
    }

    public static PunishOptions options(final PunishOptions options) {
        return PunishOptions.builder()
                .visibility(options.visibility())
                .override(options.override())
                .enforce(options.enforce())
                .bypassLimits(options.bypassLimits())
                .originServer(options.originServer().orElse(null))
                .build();
    }

    public static RevocationTarget target(final RevocationTarget target) {
        return switch (target) {
            case RevocationTarget.ById byId -> RevocationTarget.ById.of(byId.id());
            case RevocationTarget.ByVictim byVictim -> RevocationTarget.ByVictim.of(
                    byVictim.type(), victim(byVictim.victim()),
                    byVictim.scope().map(ApiValues::scope).orElse(null));
        };
    }

    public static RevocationRequest revocation(final RevocationRequest request) {
        return RevocationRequest.builder(target(request.target()), actor(request.actor()))
                .reason(request.reason().orElse(null))
                .visibility(request.visibility())
                .ownOnly(request.ownOnly())
                .build();
    }

    public static EditRequest edit(final EditRequest request) {
        return EditRequest.builder(request.id(), edit(request.edit()), actor(request.actor()))
                .build();
    }

    public static PunishmentEdit edit(final PunishmentEdit edit) {
        return switch (edit) {
            case PunishmentEdit.NewReason reason -> PunishmentEdit.NewReason.of(reason.reason());
            case PunishmentEdit.NewExpiry expiry ->
                    PunishmentEdit.NewExpiry.of(expiry.expiresAt().orElse(null));
            case PunishmentEdit.NewScope scope -> PunishmentEdit.NewScope.of(scope(scope.scope()));
        };
    }

    public static boolean sameOperator(final Operator first, final Operator second) {
        return switch (first) {
            case ConsoleOperator console -> second instanceof ConsoleOperator;
            case PlayerOperator player -> uuidOf(second) != null
                    && player.uuid().equals(uuidOf(second));
            case CustomOperator custom -> custom.uuid().isPresent()
                    ? custom.uuid().get().equals(uuidOf(second))
                    : second instanceof CustomOperator other && other.uuid().isEmpty()
                            && other.name().equals(custom.name());
        };
    }

    private static @Nullable UUID uuidOf(final Operator operator) {
        return switch (operator) {
            case ConsoleOperator console -> null;
            case PlayerOperator player -> player.uuid();
            case CustomOperator custom -> custom.uuid().orElse(null);
        };
    }
}
