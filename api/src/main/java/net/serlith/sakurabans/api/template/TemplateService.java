package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Victim;

/**
 * The templates and the victims' progress on their ladders. Templates are stored in the database
 * and shared by every instance; reads answer from memory on any thread, changes are stored,
 * audited and synced. Implemented by SakuraBans; don't implement it.
 */
public interface TemplateService {

    /**
     * Every template.
     *
     * @return the templates, by name
     */
    List<TemplateInfo> templates();

    /**
     * One template.
     *
     * @param name the template name, in any case
     * @return the template, or empty if none has this name
     */
    Optional<TemplateInfo> template(String name);

    /**
     * What the next punishment from a template would be for a victim. Ladder resolution stays in
     * SakuraBans, so callers never compute steps themselves.
     *
     * @param name the template name
     * @param victim the victim
     * @return the preview, or empty if the template doesn't exist
     */
    CompletableFuture<Optional<TemplatePreview>> preview(String name, Victim victim);

    /**
     * Creates a template.
     *
     * @param definition its values
     * @param actor who creates it
     * @return the new template; {@code ValidationFailed} if the name is taken or the kind
     *         doesn't exist
     */
    CompletableFuture<AdminResult<TemplateInfo>> create(TemplateDefinition definition, Actor actor);

    /**
     * Replaces a template's values. Victims keep their progress (counted by template name).
     *
     * @param definition the new values; its name names the template
     * @param expectedVersion the version the caller last saw
     * @param actor who changes it
     * @return the changed template
     */
    CompletableFuture<AdminResult<TemplateInfo>> update(
            TemplateDefinition definition, long expectedVersion, Actor actor);

    /**
     * Reorders a template's ladder.
     *
     * @param name the template name
     * @param order the current step indexes in their new order: a permutation of
     *         {@code 0..ladder.size() - 1}
     * @param expectedVersion the version the caller last saw
     * @param actor who changes it
     * @return the changed template; {@code ValidationFailed} if {@code order} isn't a
     *         permutation
     */
    CompletableFuture<AdminResult<TemplateInfo>> reorderSteps(
            String name, List<Integer> order, long expectedVersion, Actor actor);

    /**
     * Deletes a template. Punishments created from it keep their template reference.
     *
     * @param name the template name
     * @param expectedVersion the version the caller last saw
     * @param actor who deletes it
     * @return the deleted template
     */
    CompletableFuture<AdminResult<TemplateInfo>> delete(
            String name, long expectedVersion, Actor actor);
}
