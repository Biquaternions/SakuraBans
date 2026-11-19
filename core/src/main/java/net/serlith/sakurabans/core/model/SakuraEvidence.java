package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;

public record SakuraEvidence(EvidenceRef ref, Actor addedBy, Instant addedAt) implements Evidence {

    public SakuraEvidence {
        Preconditions.checkNotNull(ref, "ref");
        Preconditions.checkNotNull(addedBy, "addedBy");
        Preconditions.checkNotNull(addedAt, "addedAt");
    }
}
