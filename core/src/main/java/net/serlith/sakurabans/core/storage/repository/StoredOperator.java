package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import org.jooq.Condition;
import org.jooq.Field;
import org.jspecify.annotations.Nullable;

record StoredOperator(OperatorKind kind, @Nullable UUID uuid, String name) {

    static final String CONSOLE_NAME = "Console";

    static StoredOperator of(final Operator operator) {
        return switch (operator) {
            case ConsoleOperator console -> new StoredOperator(OperatorKind.CONSOLE, null,
                    CONSOLE_NAME);
            case PlayerOperator player -> new StoredOperator(OperatorKind.PLAYER, player.uuid(),
                    player.name());
            case CustomOperator custom -> new StoredOperator(OperatorKind.CUSTOM,
                    custom.uuid().orElse(null), custom.name());
        };
    }

    Operator operator() {
        return switch (this.kind) {
            case CONSOLE -> ConsoleOperator.INSTANCE;
            case PLAYER -> PlayerOperator.of(requireUuid(this.uuid), this.name);
            case CUSTOM -> CustomOperator.of(this.name, this.uuid);
        };
    }

    static Operator operator(final OperatorKind kind, final @Nullable UUID uuid,
            final String name) {
        return new StoredOperator(kind, uuid, name).operator();
    }

    static Actor actor(final OperatorKind kind, final @Nullable UUID uuid, final String name,
            final String source) {
        return Actor.of(operator(kind, uuid, name), source);
    }

    static Condition identity(final Operator operator, final Field<OperatorKind> kind,
            final Field<UUID> uuid, final Field<String> name) {
        final StoredOperator stored = of(operator);
        if (stored.kind == OperatorKind.CONSOLE) {
            return kind.eq(OperatorKind.CONSOLE);
        }
        if (stored.uuid != null) {
            return kind.in(OperatorKind.PLAYER, OperatorKind.CUSTOM).and(uuid.eq(stored.uuid));
        }
        return kind.eq(OperatorKind.CUSTOM).and(uuid.isNull()).and(name.eq(stored.name));
    }

    private static UUID requireUuid(final @Nullable UUID uuid) {
        Preconditions.checkState(uuid != null, "a stored player operator has no UUID");
        return uuid;
    }
}
