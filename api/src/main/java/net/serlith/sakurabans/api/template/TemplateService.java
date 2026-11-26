package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Optional;

/**
 * The templates and the victims' progress on their ladders. Templates are stored in the database
 * and shared by every instance; reads answer from memory on any thread. Implemented by
 * SakuraBans; don't implement it.
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

}
