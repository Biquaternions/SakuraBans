package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.TemplateInfo;

/** A stored definition and its version, which updates and the editor check against. */
public record StoredTemplate(TemplateInfo template, long version) {

    public StoredTemplate {
        Preconditions.checkNotNull(template, "template");
        Preconditions.checkArgument(version >= 1, "version must be positive: %s", version);
    }

    public String name() {
        return this.template.name();
    }

    /** Where this definition is in force; empty for the global definition. */
    public Optional<Scope> variant() {
        return this.template.variant();
    }
}
