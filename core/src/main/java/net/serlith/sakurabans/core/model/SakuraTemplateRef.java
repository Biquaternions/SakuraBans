package net.serlith.sakurabans.core.model;

import net.serlith.sakurabans.api.model.TemplateRef;

public record SakuraTemplateRef(String name, int step) implements TemplateRef {

    public SakuraTemplateRef {
        name = ModelChecks.identifier(name, "name");
        if (step < 0) {
            throw new IllegalArgumentException("step must not be negative: " + step);
        }
    }
}
