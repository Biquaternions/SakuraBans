package net.serlith.sakurabans.core.configuration.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandPatterns {

    public static final CommandPatterns NONE = new CommandPatterns(Set.of());

    private final Set<String> entries;
    private final Set<String> exact;
    private final List<Glob> globs;

    /** Entries are lower case, without a leading slash. */
    public CommandPatterns(final Set<String> entries) {
        final Set<String> literal = new LinkedHashSet<>();
        final List<Glob> wild = new ArrayList<>();
        for (final String entry : entries) {
            if (entry.indexOf('*') < 0) {
                literal.add(entry);
            } else {
                wild.add(Glob.of(entry));
            }
        }
        this.entries = Set.copyOf(entries);
        this.exact = Set.copyOf(literal);
        this.globs = List.copyOf(wild);
    }

    public Set<String> entries() {
        return this.entries;
    }

    /** Whether {@code label} (a command's first word, in any case) matches an entry. */
    public boolean matches(final String label) {
        final String lower = label.toLowerCase(Locale.ROOT);
        final int colon = lower.indexOf(':');
        final String plain = colon < 0 ? null : lower.substring(colon + 1);
        if (this.exact.contains(lower) || plain != null && this.exact.contains(plain)) {
            return true;
        }
        for (final Glob glob : this.globs) {
            if (glob.matches(lower) || plain != null && !glob.namespaced() && glob.matches(plain)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof CommandPatterns patterns && this.entries.equals(patterns.entries);
    }

    @Override
    public int hashCode() {
        return this.entries.hashCode();
    }

    @Override
    public String toString() {
        return "CommandPatterns" + this.entries;
    }

    /** A pattern split at its {@code *}s: the pieces must appear in order. */
    private record Glob(List<String> parts, boolean namespaced) {

        static Glob of(final String pattern) {
            final List<String> parts = new ArrayList<>();
            int from = 0;
            for (int star = pattern.indexOf('*'); star >= 0;
                    star = pattern.indexOf('*', from)) {
                parts.add(pattern.substring(from, star));
                from = star + 1;
            }
            parts.add(pattern.substring(from));
            return new Glob(List.copyOf(parts), pattern.indexOf(':') >= 0);
        }

        boolean matches(final String text) {
            final String first = this.parts.getFirst();
            final String last = this.parts.getLast();
            if (!text.startsWith(first)) {
                return false;
            }
            int position = first.length();
            for (int i = 1; i < this.parts.size() - 1; i++) {
                final int found = text.indexOf(this.parts.get(i), position);
                if (found < 0) {
                    return false;
                }
                position = found + this.parts.get(i).length();
            }
            return text.length() - last.length() >= position && text.endsWith(last);
        }
    }
}
