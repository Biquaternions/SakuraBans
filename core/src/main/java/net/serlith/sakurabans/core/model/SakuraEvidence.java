package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;

public record SakuraEvidence(EvidenceRef ref, Actor addedBy, Instant addedAt) implements Evidence {

    public SakuraEvidence {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(addedBy, "addedBy");
        Objects.requireNonNull(addedAt, "addedAt");
    }
}
