package net.serlith.sakurabans.api.appeal;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Page;
import org.jspecify.annotations.Nullable;

/**
 * Appeals. Every method returns a future completed on a worker thread. Implemented by SakuraBans;
 * don't implement it.
 */
public interface AppealService {

    /** The longest appeal message. */
    int MAX_MESSAGE = 2000;

    /**
     * Submits an appeal. Only the punishment's victim (for player victims) may appeal, once per
     * punishment while an earlier appeal is open.
     *
     * @param punishmentId the punishment's database id
     * @param appellant who appeals
     * @param message what they write, 1 to {@link #MAX_MESSAGE} characters
     * @return the new appeal; {@code ValidationFailed} if the appellant may not appeal it or an
     *         open appeal exists
     */
    CompletableFuture<AdminResult<Appeal>> submit(
            long punishmentId, UUID appellant, String message);

    /**
     * Selects appeals.
     *
     * @param query the filters and the cursor
     * @return one page of appeals
     */
    CompletableFuture<Page<Appeal>> appeals(AppealQuery query);

    /**
     * One appeal.
     *
     * @param id the appeal's id
     * @return the appeal, or empty
     */
    CompletableFuture<Optional<Appeal>> appeal(long id);

    /**
     * Decides an open appeal; {@code REVOKE} and {@code VOID} change the punishment accordingly.
     *
     * @param id the appeal's id
     * @param outcome the decision
     * @param note a note for the appellant, plain text, or {@code null}
     * @param expectedVersion the version the caller last saw
     * @param actor who decides
     * @return the decided appeal
     */
    CompletableFuture<AdminResult<Appeal>> decide(long id, AppealOutcome outcome,
            @Nullable String note, long expectedVersion, Actor actor);
}
