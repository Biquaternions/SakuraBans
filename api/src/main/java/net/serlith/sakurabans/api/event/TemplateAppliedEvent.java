package net.serlith.sakurabans.api.event;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;

/**
 * After a template was applied and its use recorded: a preset's punishment, or a ladder step,
 * with or without a punishment. It fires once for every use, presets included. Every use moves
 * its victim up the ladders that count it. Fired right after the matching
 * {@link PunishmentCreatedEvent}, if there is one; the template's console actions run right
 * after this event's handlers.
 *
 * <p>Runs on a SakuraBans worker thread, on the instance that applied it.
 */
public interface TemplateAppliedEvent extends SakuraEvent {

    /**
     * The punishment that was created.
     *
     * @return the punishment, or empty for a ladder step without one
     */
    Optional<Punishment> punishment();

    /**
     * Whom the template was applied to. A template with {@code ip} punishes the player's
     * address, but this is still the player.
     *
     * @return the victim
     */
    Victim victim();

    /**
     * The template and, for a ladder, the step that was applied.
     *
     * @return the template reference
     */
    TemplateRef template();

    /**
     * Who applied it, with the source of the request.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * The reason of the use: the punishment's reason when there is a punishment; for a step
     * without one, the request's reason if it gave one, else the step's, else the template's.
     *
     * @return the reason
     */
    String reason();
}
