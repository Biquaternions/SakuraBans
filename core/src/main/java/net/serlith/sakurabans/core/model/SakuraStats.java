package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.stats.CountBucket;
import net.serlith.sakurabans.api.stats.PlayerSummary;
import net.serlith.sakurabans.api.stats.StaffStats;
import org.jspecify.annotations.Nullable;

public final class SakuraStats {

    private SakuraStats() {
    }

    public record Summary(UUID uuid, List<Punishment> active, Map<String, Long> counts,
            long voided, @Nullable Punishment lastOrNull) implements PlayerSummary {

        public Summary {
            active = List.copyOf(active);
            counts = Map.copyOf(counts);
        }

        @Override
        public Optional<Punishment> last() {
            return Optional.ofNullable(this.lastOrNull);
        }
    }

    public record Staff(Operator operator, Instant from, Instant to, Map<String, Long> issued,
            Map<String, Long> revoked, Map<String, Long> voided) implements StaffStats {

        public Staff {
            issued = Map.copyOf(issued);
            revoked = Map.copyOf(revoked);
            voided = Map.copyOf(voided);
        }
    }

    public record Count(Instant start, String kind, long count) implements CountBucket {
    }

}
