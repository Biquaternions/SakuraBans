package net.serlith.sakurabans.core.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.util.Scopes;

/** The overrides that reach this server: per applying scope, winning one first. */
public record OverrideCount(Map<Scope, Integer> settings, Map<Scope, Integer> messages) {

    public OverrideCount {
        settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
    }

    public static OverrideCount of(final SakuraSettingsService settings,
            final SakuraMessageTemplateService messages) {
        final Map<Scope, Integer> scopes = new LinkedHashMap<>();
        for (final Scope scope : settings.layers()) {
            final int count = settings.overrides(scope).size();
            if (count > 0) {
                scopes.put(scope, count);
            }
        }
        final Map<Scope, Integer> counts = new LinkedHashMap<>();
        for (final Scope scope : settings.layers()) {
            final int count = messages.overrides(scope).values().stream()
                    .mapToInt(SortedSet::size).sum();
            if (count > 0) {
                counts.put(scope, count);
            }
        }
        return new OverrideCount(scopes, counts);
    }

    public boolean any() {
        return !this.settings.isEmpty() || !this.messages.isEmpty();
    }

    public int settingCount() {
        return this.settings.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int messageCount() {
        return this.messages.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** {@code server:lobby 2, global 5}: the scopes that apply here and what they hold. */
    public String settingScopes() {
        return scopes(this.settings);
    }

    /** The same for messages, all locales together. */
    public String messageScopes() {
        return scopes(this.messages);
    }

    private static String scopes(final Map<Scope, Integer> counts) {
        final List<String> scopes = new ArrayList<>();
        counts.forEach((scope, count) -> scopes.add(Scopes.format(scope) + " " + count));
        return String.join(", ", scopes);
    }

    public void log(final ComponentLogger logger) {
        if (!this.any()) {
            return;
        }
        logger.info("The database overrides {} settings for this server ({}) and {} messages"
                        + " ({}); see /sb settings list and /sb messages list",
                this.settingCount(), this.settingScopes(), this.messageCount(),
                this.messageScopes());
    }
}
