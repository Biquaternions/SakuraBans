package net.serlith.sakurabans.core.permission;

import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public sealed interface Action {

    /**
     * {@code requiredTemplate}: the preset's name when it has {@code permission-required}, so
     * its node {@code sakurabans.template.<name>} is needed too (S63); null otherwise.
     */
    record Punish(PunishmentType type, @Nullable Scope scope, @Nullable String requiredTemplate)
            implements Action {

        public Punish(final PunishmentType type, final @Nullable Scope scope) {
            this(type, scope, null);
        }
    }

    /** D37: a ladder needs {@code sakurabans.punish}, not the permission of its step's type. */
    record ApplyLadder(String ladder, boolean permissionRequired, @Nullable Scope scope)
            implements Action {
    }

    record ChangePunishment(Operation operation, long id) implements Action {

        public enum Operation {
            REVOKE,
            EDIT,
            VOID,
            EXPUNGE,
            EVIDENCE
        }
    }

    record Manage(Area area) implements Action {

        public enum Area {
            TEMPLATES,
            SETTINGS,
            MESSAGES,
            PLAYER_DATA,
            ROLLBACK
        }
    }
}
