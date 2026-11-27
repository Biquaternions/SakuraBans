package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationsTest {

    static Stream<Arguments> valid() {
        return Stream.of(
                Arguments.of("90m", Duration.ofMinutes(90)),
                Arguments.of("1.5h", Duration.ofMinutes(90)),
                Arguments.of("1d12h", Duration.ofHours(36)),
                Arguments.of("2w", Duration.ofDays(14)),
                Arguments.of("1mo", Duration.ofDays(30)),
                Arguments.of("1y", Duration.ofDays(365)),
                Arguments.of("1D12H", Duration.ofHours(36)),
                Arguments.of("2weeks", Duration.ofDays(14)),
                Arguments.of("1y2mo3w4d5h6m7s", Duration.ofDays(365 + 60 + 21 + 4)
                        .plusHours(5).plusMinutes(6).plusSeconds(7)),
                Arguments.of("30s", Duration.ofSeconds(30)),
                Arguments.of(" 10m ", Duration.ofMinutes(10)),
                Arguments.of("0.0005s", Duration.ofMillis(1)),
                // The sync poll interval's documented example and minimum.
                Arguments.of("500ms", Duration.ofMillis(500)),
                Arguments.of("250MS", Duration.ofMillis(250)),
                Arguments.of("1s500ms", Duration.ofMillis(1500)));
    }

    @ParameterizedTest
    @MethodSource("valid")
    void parsesLiteBansSyntax(final String text, final Duration expected) {
        assertThat(Durations.parse(text)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "90", "1x", "1d 2h", "d", "-1d", "0s", "0.0004s", "1.d",
        "999999999999y", "perm", "0.4ms"})
    void rejectsEverythingElse(final String text) {
        assertThat(Durations.parse(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"perm", "PERMANENT", "forever"})
    void knowsPermanentTokens(final String token) {
        assertThat(Durations.isPermanentToken(token)).isTrue();
    }

    @Test
    void otherTokensAreNotPermanent() {
        assertThat(Durations.isPermanentToken("1d")).isFalse();
    }

    @Test
    void formatsCompactly() {
        assertThat(Durations.formatCompact(Duration.ofHours(36))).isEqualTo("1d 12h");
        assertThat(Durations.formatCompact(Duration.ofDays(366).plusSeconds(5)))
                .isEqualTo("1y 1d 5s");
        assertThat(Durations.formatCompact(Duration.ofMillis(999))).isEqualTo("0s");
        assertThat(Durations.formatCompact(Duration.ZERO)).isEqualTo("0s");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 59, 3600, 90061, 31_536_000, 31_626_061, 999_999_999})
    void compactFormatParsesBackWithoutSpaces(final long seconds) {
        final Duration duration = Duration.ofSeconds(seconds);

        final String token = Durations.formatCompact(duration).replace(" ", "");

        assertThat(Durations.parse(token)).contains(duration);
    }
}
