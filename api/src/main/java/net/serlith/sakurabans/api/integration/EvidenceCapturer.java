package net.serlith.sakurabans.api.integration;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;

/**
 * Captures evidence of one type when a punishment is created, for example a chat snapshot.
 * Templates and kinds can require evidence of a type; when a draft lacks it and a capturer for
 * the type is registered, SakuraBans captures it and attaches the result.
 *
 * <p>If the capture fails or times out, a punishment that requires the evidence fails with
 * {@code EVIDENCE_REQUIRED}.
 */
public interface EvidenceCapturer {

    /**
     * The type of evidence this captures, as returned by {@link EvidenceRef#type()}.
     *
     * @return {@code <namespace>:<type>}, for example {@code hydra:chat-snapshot}
     */
    String type();

    /**
     * Captures evidence about a player now.
     *
     * @param target the player the punishment is for
     * @param actor who issues the punishment
     * @return a reference to the captured evidence, whose type is {@link #type()}
     */
    CompletableFuture<EvidenceRef> capture(UUID target, Actor actor);
}
