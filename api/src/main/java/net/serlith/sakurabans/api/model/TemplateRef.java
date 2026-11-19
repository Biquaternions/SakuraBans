package net.serlith.sakurabans.api.model;

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
     * @return the step, counted from 0
     */
    int step();
}
