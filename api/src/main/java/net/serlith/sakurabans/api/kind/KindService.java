package net.serlith.sakurabans.api.kind;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;

/**
 * The punishment kinds. They are stored in the database and shared by every instance; reads
 * answer from memory on any thread, changes are stored, audited and synced. Implemented by
 * SakuraBans; don't implement it.
 */
public interface KindService {

    /**
     * Every kind.
     *
     * @return the kinds, built-in ones first, then by id
     */
    List<Kind> kinds();

    /**
     * One kind.
     *
     * @param id the id, in any case
     * @return the kind, or empty
     */
    Optional<Kind> kind(String id);

    /**
     * Creates a kind.
     *
     * @param definition its values
     * @param actor who creates it
     * @return the new kind; {@code ValidationFailed} if the id is taken
     */
    CompletableFuture<AdminResult<Kind>> create(KindDefinition definition, Actor actor);

    /**
     * Replaces a kind's values. Punishments created earlier keep the values they copied.
     *
     * @param definition the new values; its id names the kind, and the base type can't change
     * @param expectedVersion the version the caller last saw
     * @param actor who changes it
     * @return the changed kind
     */
    CompletableFuture<AdminResult<Kind>> update(
            KindDefinition definition, long expectedVersion, Actor actor);

    /**
     * Deletes a kind that no punishment uses. Built-in kinds can't be deleted.
     *
     * @param id the id
     * @param expectedVersion the version the caller last saw
     * @param actor who deletes it
     * @return the deleted kind; {@code ValidationFailed} if it is built in or in use
     */
    CompletableFuture<AdminResult<Kind>> delete(String id, long expectedVersion, Actor actor);
}
