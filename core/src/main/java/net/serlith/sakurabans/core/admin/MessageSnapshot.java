package net.serlith.sakurabans.core.admin;

import java.util.Locale;
import java.util.Map;

public record MessageSnapshot(long version, Locale locale, Map<String, String> texts,
        Map<String, ValueOrigin> origins) {

    public MessageSnapshot {
        texts = Map.copyOf(texts);
        origins = Map.copyOf(origins);
    }
}
