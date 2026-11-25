package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.serlith.sakurabans.api.model.PunishmentType;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class CommandsSection {

    private Map<String, List<String>> labels = new LinkedHashMap<>(Map.of(
            "sakurabans", List.of("sakurabans", "sb")));
    private Map<String, KindCommandSection> kinds = new LinkedHashMap<>(Map.of(
            "blacklist", new KindCommandSection(PunishmentType.BAN, List.of("blacklist")),
            "shadow-mute", new KindCommandSection(PunishmentType.MUTE,
                    List.of("shadowmute"))));

    @Getter
    @ConfigSerializable
    public static final class KindCommandSection {

        private PunishmentType type = PunishmentType.BAN;
        private List<String> labels = List.of();

        public KindCommandSection() {
        }

        KindCommandSection(final PunishmentType type, final List<String> labels) {
            this.type = type;
            this.labels = labels;
        }
    }
}
