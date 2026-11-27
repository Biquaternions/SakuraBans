package net.serlith.sakurabans.core.configuration.settings;

import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CommandPatternsTest {

    private static CommandPatterns of(final String... entries) {
        return new CommandPatterns(Set.of(entries));
    }

    @Test
    void plainEntriesMatchTheCommandAndItsNamespacedForms() {
        final CommandPatterns patterns = of("msg", "me");

        Assertions.assertThat(patterns.matches("msg")).isTrue();
        Assertions.assertThat(patterns.matches("MSG")).isTrue();
        Assertions.assertThat(patterns.matches("essentials:msg")).isTrue();
        Assertions.assertThat(patterns.matches("minecraft:me")).isTrue();
        Assertions.assertThat(patterns.matches("msgx")).isFalse();
        Assertions.assertThat(patterns.matches("essentials:msgx")).isFalse();
        Assertions.assertThat(patterns.matches("home")).isFalse();
    }

    @Test
    void entriesWithColonsMatchTheWholeLabelOnly() {
        final CommandPatterns patterns = of("minecraft:me");

        Assertions.assertThat(patterns.matches("minecraft:me")).isTrue();
        Assertions.assertThat(patterns.matches("me")).isFalse();
        Assertions.assertThat(patterns.matches("other:me")).isFalse();
    }

    @Test
    void wildcardsMatchAnyText() {
        final CommandPatterns patterns = of("*:me", "vanilla:*", "e*", "a*b*c");

        Assertions.assertThat(patterns.matches("x:me")).isTrue();
        Assertions.assertThat(patterns.matches("me")).isFalse();
        Assertions.assertThat(patterns.matches("vanilla:say")).isTrue();
        Assertions.assertThat(patterns.matches("vanilla:")).isTrue();
        Assertions.assertThat(patterns.matches("vanilla")).isFalse();
        Assertions.assertThat(patterns.matches("essentials")).isTrue();
        Assertions.assertThat(patterns.matches("e")).isTrue();
        Assertions.assertThat(patterns.matches("home")).isFalse();
        // A namespaced command is also matched by its own part against a colon-less pattern.
        Assertions.assertThat(patterns.matches("plugin:echo")).isTrue();
        Assertions.assertThat(patterns.matches("axbxc")).isTrue();
        Assertions.assertThat(patterns.matches("abc")).isTrue();
        Assertions.assertThat(patterns.matches("ac")).isFalse();
        Assertions.assertThat(patterns.matches("axbxcx")).isFalse();
        Assertions.assertThat(patterns.matches("acb")).isFalse();
    }

    @Test
    void singleStarMatchesEverything() {
        Assertions.assertThat(of("*").matches("anything:at-all")).isTrue();
    }

    @Test
    void anEmptyListMatchesNothing() {
        Assertions.assertThat(CommandPatterns.NONE.matches("me")).isFalse();
    }

    @Test
    void equalEntriesAreEqual() {
        Assertions.assertThat(of("a", "b*")).isEqualTo(of("b*", "a"));
        Assertions.assertThat(of("a")).isNotEqualTo(of("b"));
    }
}
