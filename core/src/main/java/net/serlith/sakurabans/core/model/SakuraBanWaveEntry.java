package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.wave.BanWaveEntry;
import org.jspecify.annotations.Nullable;

public record SakuraBanWaveEntry(long id, long waveId, DraftPunishment draft,
        PunishOptions options, Actor addedBy, Instant addedAt,
        @Nullable PunishResult resultOrNull) implements BanWaveEntry {

    @Override
    public Optional<PunishResult> result() {
        return Optional.ofNullable(this.resultOrNull);
    }

}
