package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Victim;

/**
 * The configured templates and the victims' progress on their ladders. Implemented by
 * SakuraBans; don't implement it.
 */
public interface TemplateService {

    /**
     * Every loaded template. Answers from the loaded configuration, on any thread.
     *
     * @return the templates, in configuration order
     */
    List<TemplateInfo> templates();

    /**
     * One loaded template. Answers from the loaded configuration, on any thread.
     *
     * @param name the template name, in any case
     * @return the template, or empty if none has this name
     */
    Optional<TemplateInfo> template(String name);

    /**
     * The ladder step the next punishment from a template would use for a victim, counting the
     * victim's earlier punishments from that template (after {@code expire_ladder}).
     *
     * @param name the template name
     * @param victim the victim
     * @return the step, counted from 0, or empty if the template doesn't exist
     */
    CompletableFuture<Optional<Integer>> nextStep(String name, Victim victim);
}
