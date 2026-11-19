package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;

/**
 * After a punishment from a template was created, moving its victim up the template's ladder.
 * Fired right after the matching {@link PunishmentCreatedEvent}.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface TemplateStepEvent extends SyncedEvent {

    /**
     * The punishment that was created.
     *
     * @return the punishment
     */
    Punishment punishment();

    /**
     * The template and the step that were applied.
     *
     * @return the template reference
     */
    TemplateRef template();
}
