package net.serlith.sakurabans.core.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public record OverrideCount(List<String> settings, Map<Locale, Integer> messages) {

    public OverrideCount {
        settings = List.copyOf(settings);
        messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
    }

    public static OverrideCount of(final SakuraSettingsService settings,
            final SakuraMessageTemplateService messages) {
        final SortedMap<Locale, SortedSet<String>> overridden = messages.overrides();
        final Map<Locale, Integer> counts = new LinkedHashMap<>();
        overridden.forEach((locale, keys) -> counts.put(locale, keys.size()));
        return new OverrideCount(new ArrayList<>(settings.overrides().keySet()), counts);
    }

    public boolean any() {
        return !this.settings.isEmpty() || !this.messages.isEmpty();
    }

    public int messageCount() {
        return this.messages.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void log(final ComponentLogger logger) {
        if (!this.any()) {
            return;
        }
        final List<String> locales = new ArrayList<>();
        this.messages.forEach((locale, count) -> locales.add(locale.toLanguageTag() + ": "
                + count));
        logger.info("The database overrides {} settings {} and {} messages {}; see"
                        + " /sb settings list and /sb messages list", this.settings.size(),
                this.settings, this.messageCount(), locales);
    }
}
