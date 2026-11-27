package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import org.jspecify.annotations.Nullable;

/**
 * The templates and the victims' progress on their ladders. Templates are stored in the database
 * and shared by every instance; reads answer from memory on any thread. Implemented by
 * SakuraBans; don't implement it.
 *
 * <p>A template has one name network-wide and a global definition; it may also have
 * <em>variants</em> for servers and categories. Which definition a use gets (a punish command,
 * {@link net.serlith.sakurabans.api.punish.PunishmentService#punish},
 * {@link net.serlith.sakurabans.api.punish.PunishmentService#applyLadder}): the request's scope
 * if it names one; otherwise, on a backend, this server; on a proxy, the server the operator
 * is on; otherwise (the console or an offline operator on a proxy, or a backend without a
 * name) the global definition. From a server, its own variant, else its categories' variants
 * in the order {@code scopes.categories} declares them, else the global definition; from a
 * category, its variant, else the global definition; from {@code global}, the global
 * definition. Ladder progress is shared by every definition of a name.
 */
public interface TemplateService {

    /**
     * Every template's global definition.
     *
     * @return the templates, by name
     */
    List<TemplateInfo> templates();

    /**
     * One template's global definition.
     *
     * @param name the template name, in any case
     * @return the template, or empty if none has this name
     */
    Optional<TemplateInfo> template(String name);

    /**
     * The definition of a template in force at a scope, by the rule of this interface: a
     * server's own variant, else its categories', else the global definition.
     *
     * @param name the template name, in any case
     * @param scope a server, a category or {@code global}
     * @return the definition, or empty if no template has this name
     */
    Optional<TemplateInfo> template(String name, Scope scope);

    /**
     * The step the ladder's global definition would apply to the victim now (its next step),
     * counted from the victim's earlier uses; past the end, the last step. The same as
     * {@link #nextStep(String, Victim, Scope)} without a scope.
     *
     * @param ladder the ladder's name, in any case
     * @param victim the victim
     * @return the step, counted from 0, or empty if no ladder has this name
     */
    CompletableFuture<OptionalInt> nextStep(String ladder, Victim victim);

    /**
     * The step the ladder would apply to the victim now if applied with this scope: the
     * definition in force at the scope, picked by the rule of this interface exactly as for a
     * request that names the scope ({@code /punish --server lobby}, a
     * {@link net.serlith.sakurabans.api.punish.LadderRequest} with a scope). Progress is shared
     * by every definition of a name, but a variant may have other steps, another expiry or
     * count across addresses, so the step may differ per scope. Past the end, the last step.
     *
     * @param ladder the ladder's name, in any case
     * @param victim the victim
     * @param scope a server, a category or {@code global}; null for the global definition, as
     *     {@link #nextStep(String, Victim)}
     * @return the step, counted from 0, or empty if no ladder has this name (or the definition
     *     in force at the scope isn't a ladder)
     */
    CompletableFuture<OptionalInt> nextStep(String ladder, Victim victim, @Nullable Scope scope);

}
