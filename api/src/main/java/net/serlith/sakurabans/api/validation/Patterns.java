package net.serlith.sakurabans.api.validation;

import java.util.regex.Pattern;

final class Patterns {

    static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]+");
    static final Pattern EVIDENCE_TYPE = Pattern.compile("[a-z0-9_-]+:[a-z0-9_-]+");

    private Patterns() {
    }
}
