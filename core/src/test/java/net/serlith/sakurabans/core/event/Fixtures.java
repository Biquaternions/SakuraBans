package net.serlith.sakurabans.core.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.model.SakuraPunishment;

final class Fixtures {

    private Fixtures() {
    }

    static SakuraPunishment ban(final long id) {
        return new SakuraPunishment(id, Long.toString(id), PunishmentType.BAN, "ban", false, null,
                PlayerVictim.of(UUID.randomUUID()), ConsoleOperator.INSTANCE, "console",
                GlobalScope.INSTANCE, "reason", Instant.parse("2026-11-19T12:00:00Z"), null, null,
                Visibility.NORMAL, null, null, null, List.of(), 1);
    }
}
