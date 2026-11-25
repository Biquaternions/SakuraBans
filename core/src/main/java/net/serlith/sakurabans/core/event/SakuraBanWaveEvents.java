package net.serlith.sakurabans.core.event;

import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.api.event.BanWaveReleasedEvent;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.api.wave.BanWaveEntry;
import org.jspecify.annotations.Nullable;

public final class SakuraBanWaveEvents {

    private SakuraBanWaveEvents() {
    }

    public record Changed(ChangeType change, String entityId, long version, Actor actor,
            @Nullable BanWave waveOrNull, boolean remote)
            implements EntityChangedEvent.BanWaveChanged {

        @Override
        public Optional<BanWave> wave() {
            return Optional.ofNullable(this.waveOrNull);
        }
    }

    public record Released(BanWave wave, List<BanWaveEntry> entries, boolean remote)
            implements BanWaveReleasedEvent {

        public Released {
            entries = List.copyOf(entries);
        }
    }
}
