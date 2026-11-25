package net.serlith.sakurabans.core.admin;

import java.util.List;

public record MessageField(String key, List<String> placeholders, String defaultText,
        String description) {

    public MessageField {
        placeholders = List.copyOf(placeholders);
    }
}
