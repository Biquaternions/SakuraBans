package net.serlith.sakurabans.core.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.template.TemplateDisplay;

/** A template's {@code display} as {@code templates.yml} and the editor write it. */
public final class TemplateDisplays {

    private TemplateDisplays() {
    }

    public static String key(final TemplateDisplay display) {
        return display.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TemplateDisplay> parse(final String text) {
        return Arrays.stream(TemplateDisplay.values())
                .filter(display -> key(display).equalsIgnoreCase(text.strip()))
                .findFirst();
    }
}
