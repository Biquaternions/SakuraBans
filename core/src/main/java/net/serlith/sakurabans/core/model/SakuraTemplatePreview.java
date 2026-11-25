package net.serlith.sakurabans.core.model;

import java.time.Duration;
import java.util.Optional;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplatePreview;

public record SakuraTemplatePreview(TemplateInfo template, int step) implements TemplatePreview {

    public SakuraTemplatePreview {
        if (step < 0 || step >= template.ladder().size()) {
            throw new IllegalArgumentException("step " + step + " is outside the ladder");
        }
    }

    @Override
    public Optional<Duration> duration() {
        return this.template.ladder().get(this.step).duration();
    }
}
