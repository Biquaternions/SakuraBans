package net.serlith.sakurabans.core.model;

import java.time.Instant;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.NameRecord;

public record SakuraNameRecord(String name, Instant firstSeen, Instant lastSeen)
        implements NameRecord {

    public SakuraNameRecord {
        ModelChecks.name(name, Lengths.MAX_PLAYER_NAME, "name");
        ModelChecks.seen(firstSeen, lastSeen);
    }
}
