package net.serlith.sakurabans.core.permission;

import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public sealed interface Action {

    record Punish(PunishmentType type, @Nullable Scope scope) implements Action {
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
