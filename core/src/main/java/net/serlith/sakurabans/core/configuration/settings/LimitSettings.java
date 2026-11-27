package net.serlith.sakurabans.core.configuration.settings;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;

public record LimitSettings(boolean reduceToLimit, List<Group> groups,
        Duration consoleCooldown) {

    public static final LimitSettings NONE = new LimitSettings(true, List.of(), Duration.ZERO);

    public LimitSettings {
        groups = List.copyOf(groups);
    }

    public record Group(String name, String permission, Map<PunishmentType, Duration> maxDurations,
            Map<PunishmentType, Duration> cooldowns, boolean requireTemplate,
            Duration cooldownRedo) {

        public Group {
            maxDurations = Map.copyOf(maxDurations);
            cooldowns = Map.copyOf(cooldowns);
        }

        public Optional<Duration> maxDuration(final PunishmentType type) {
            return Optional.ofNullable(this.maxDurations.get(type));
        }

        public Optional<Duration> cooldown(final PunishmentType type) {
            return Optional.ofNullable(this.cooldowns.get(type));
        }
    }

}
