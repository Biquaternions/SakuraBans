package net.serlith.sakurabans.api.punish;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

/**
 * Creates, revokes, edits and finds punishments. Implemented by SakuraBans; don't implement it.
 *
 * <p>Values you pass in are validated again by SakuraBans, so your own implementations of the
 * request types are held to the same rules as the built-in ones.
 */
public interface PunishmentService {

    /**
     * Creates a punishment: applies defaults, templates and limits, checks exemption, fires
     * {@code PunishmentCreateEvent}, stores it, enforces it and notifies staff.
     *
     * @param draft the punishment to create
     * @param options visibility, override policy, enforcement and limits
     * @return the outcome
     */
    CompletableFuture<PunishResult> punish(DraftPunishment draft, PunishOptions options);

    /**
     * Ends punishments early (unban, unmute, unwarn).
     *
     * @param request which punishments, by whom and why
     * @return the outcome
     */
    CompletableFuture<RevokeResult> revoke(RevocationRequest request);

    /**
     * Changes one aspect of a punishment and records it in the audit log.
     *
     * @param id the punishment's database id
     * @param edit the change
     * @param operator who changes it
     * @return the outcome
     */
    CompletableFuture<EditResult> edit(long id, PunishmentEdit edit, Operator operator);

    /**
     * Deletes a punishment from the history for good. The audit log keeps a record of it.
     *
     * @param id the punishment's database id
     * @param operator who deletes it
     * @return the outcome
     */
    CompletableFuture<ExpungeResult> expunge(long id, Operator operator);

    /**
     * Finds a punishment by its database id.
     *
     * @param id the id
     * @return the punishment, or empty if none has this id
     */
    CompletableFuture<Optional<Punishment>> byId(long id);

    /**
     * Finds a punishment by the code shown to players.
     *
     * @param displayId the display code, in any case
     * @return the punishment, or empty if the code doesn't match one
     */
    CompletableFuture<Optional<Punishment>> byDisplayId(String displayId);

    /**
     * Selects punishments.
     *
     * @param query the filters and the page
     * @return one page of punishments, newest first
     */
    CompletableFuture<Page<Punishment>> query(PunishmentQuery query);

    /**
     * The ban that would deny this connection in this scope, the way the login check decides it
     * (with the configured address strictness).
     *
     * @param uuid the account
     * @param address the connection's address, or {@code null} to check the account only
     * @param scope the scope to check; global, server and category bans that cover it count
     * @return the most severe matching ban, or empty
     */
    CompletableFuture<Optional<Punishment>> activeBan(
            UUID uuid, @Nullable InetAddress address, Scope scope);

    /**
     * The mute that applies to this connection in this scope, decided like
     * {@link #activeBan(UUID, InetAddress, Scope)}.
     *
     * @param uuid the account
     * @param address the connection's address, or {@code null} to check the account only
     * @param scope the scope to check
     * @return the most severe matching mute, or empty
     */
    CompletableFuture<Optional<Punishment>> activeMute(
            UUID uuid, @Nullable InetAddress address, Scope scope);
}
