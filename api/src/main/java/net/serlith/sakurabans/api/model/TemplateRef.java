package net.serlith.sakurabans.api.model;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The template a punishment was created from. Implemented by SakuraBans; don't implement it.
 */
public interface TemplateRef {

    /**
     * The template.
     *
     * @return the template name, lower case
     */
    String name();

    /**
     * The ladder step that was applied.
     *
     * @return the step, counted from 0, or empty for a preset
     */
    OptionalInt step();

    /**
     * The variant of the template that was used: the server or category whose definition was
     * in force for the use. The punished player's message is rendered from it on every server;
     * once that variant is deleted, from the global definition.
     *
     * @return the variant's scope, or empty for the global definition
     */
    Optional<Scope> variant();
}
