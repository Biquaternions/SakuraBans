package net.serlith.sakurabans.api.punish;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

/**
 * Creates, revokes, edits, voids and finds punishments. Implemented by SakuraBans; don't
 * implement it.
 *
 * <p>Values you pass in are validated again by SakuraBans, so your own implementations of the
 * request types are held to the same rules as the built-in ones. Every action is authorized for
 * its actor first, and fails with {@code FORBIDDEN} if the actor may not do it. A player actor
 * who is online is checked with their permissions on this server; one who isn't, with their
 * LuckPerms permissions, and is refused without LuckPerms. The console, and a custom operator
 * without a UUID, pass.
 */
public interface PunishmentService {

    /**
     * Creates a punishment: applies defaults, templates and limits, checks exemption,
     * fires {@code PunishmentCreateEvent}, stores it, enforces it and notifies staff.
     *
     * @param draft the punishment to create
     * @param options visibility, override policy, enforcement and limits
     * @return the outcome
     */
    CompletableFuture<PunishResult> punish(DraftPunishment draft, PunishOptions options);

    /**
     * Applies a ladder: counts the victim's earlier uses of it (and the weighted uses of its
     * group mates), picks the step, and applies it. A step with a punishment goes the way of
     * {@link #punish} (events, exemption and cooldowns of the step's type; the duration limits
     * of {@code limits.yml} don't clip a ladder's step); a step without one only runs its
     * actions. Either way the use is recorded and {@code TemplateAppliedEvent} fires. Two
     * applies to the same victim at the same moment get consecutive steps (the later one is
     * counted again); with {@code address-shared}, applies to different accounts or addresses
     * of one person at the same moment may still get the same step.
     *
     * <p>The actor needs {@code sakurabans.punish} (and {@code sakurabans.template.<name>} when
     * the ladder requires it), not the permission of the step's type.
     *
     * @param request the ladder, the victim and the actor
     * @param options visibility, override policy and enforcement of the punishment
     * @return the outcome
     */
    CompletableFuture<LadderResult> applyLadder(LadderRequest request, PunishOptions options);

    /**
     * Ends punishments early (unban, unmute, unwarn).
     *
     * @param request which punishments, by whom and why
     * @return the outcome
     */
    CompletableFuture<RevokeResult> revoke(RevocationRequest request);

    /**
     * Changes one aspect of a punishment and records it in the audit log. Two changes at once
     * both apply, the later one last.
     *
     * @param request the punishment, the change and the actor
     * @return the outcome
     */
    CompletableFuture<EditResult> edit(EditRequest request);

    /**
     * Marks a punishment as a mistake. It stays in the history, but no longer counts for ladders,
     * warning thresholds or statistics; an active one ends with {@code VOIDED}.
     *
     * @param id the punishment's database id
     * @param actor who voids it
     * @param reason why, plain text, or {@code null}
     * @return the outcome
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the reason is too
     *         long (thrown immediately, not through the future)
     */
    CompletableFuture<VoidResult> voidPunishment(long id, Actor actor, @Nullable String reason);

    /**
     * Deletes a punishment from the history for good. The audit log keeps a record of it.
     *
     * @param id the punishment's database id
     * @param actor who deletes it
     * @return the outcome
     */
    CompletableFuture<ExpungeResult> expunge(long id, Actor actor);

    /**
     * Attaches evidence to a punishment, active or not.
     *
     * @param id the punishment's database id
     * @param evidence the reference to attach
     * @param actor who attaches it
     * @return the outcome; {@code EVIDENCE_ALREADY_ATTACHED} if it is there already
     */
    CompletableFuture<EditResult> attachEvidence(long id, EvidenceRef evidence, Actor actor);

    /**
     * Detaches evidence from a punishment.
     *
     * @param id the punishment's database id
     * @param evidence the reference to detach (matched by URI)
     * @param actor who detaches it
     * @return the outcome; {@code EVIDENCE_NOT_ATTACHED} if it isn't there
     */
    CompletableFuture<EditResult> detachEvidence(long id, EvidenceRef evidence, Actor actor);

    /**
     * Ends every active punishment an operator issued since an instant ({@code ROLLED_BACK}).
     *
     * @param request whose punishments, since when, by whom and why
     * @return the outcome
     */
    CompletableFuture<RollbackResult> rollback(RollbackRequest request);

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
     * @param query the filters, the order and the page
     * @return one page of punishments
     */
    CompletableFuture<Page<Punishment>> query(PunishmentQuery query);

    /**
     * Counts the punishments a query selects, ignoring its paging.
     *
     * @param query the filters
     * @return the number of matching punishments
     */
    CompletableFuture<Long> count(PunishmentQuery query);

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
     * {@link #activeBan(UUID, InetAddress, Scope)}. Check {@link Punishment#shadow()} for a
     * shadow mute.
     *
     * @param uuid the account
     * @param address the connection's address, or {@code null} to check the account only
     * @param scope the scope to check
     * @return the most severe matching mute, or empty
     */
    CompletableFuture<Optional<Punishment>> activeMute(
            UUID uuid, @Nullable InetAddress address, Scope scope);

    /**
     * The mute that applies to a player online on this instance, from memory, without I/O. Safe
     * to call on chat and command threads; it is what SakuraBans' own chat check uses. On a proxy,
     * "here" is the server the player is on (only global mutes before they reach one).
     *
     * @param uuid the account
     * @return the mute that applies here, or empty if there is none or the player isn't online
     *         on this instance
     */
    Optional<Punishment> cachedMute(UUID uuid);
}
