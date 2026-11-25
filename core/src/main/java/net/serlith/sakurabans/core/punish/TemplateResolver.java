package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface TemplateResolver {

    TemplateResolver NONE = (name, victim, actor) ->
            CompletableFuture.completedFuture(Optional.empty());

    CompletableFuture<Optional<Resolution>> resolve(String name, Victim victim, Actor actor);

    record Resolution(String name, String kind, PunishmentType type, String reason,
            @Nullable Duration duration, int step, Set<String> requiredEvidence,
            List<EvidenceRef> captured) {

        public Resolution {
            requiredEvidence = Set.copyOf(requiredEvidence);
            captured = List.copyOf(captured);
        }
    }

}
