package net.serlith.sakurabans.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SakuraHistoryRecordsTest {

    private static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LAST = Instant.parse("2026-11-18T00:00:00Z");

    @Test
    void lastSeenCantPrecedeFirstSeen() throws Exception {
        final InetAddress address = InetAddress.getByName("1.2.3.4");
        final UUID uuid = UUID.randomUUID();

        assertThatThrownBy(() -> new SakuraNameRecord("Notch", LAST, FIRST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SakuraAddressRecord(address, LAST, FIRST, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SakuraPlayerProfileView(
                uuid, "Notch", LAST, FIRST, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SakuraNameRecord("Notch", FIRST, FIRST).lastSeen()).isEqualTo(FIRST);
    }

    @Test
    void addressesAreNormalised() throws Exception {
        final InetAddress named = InetAddress.getByAddress("host", new byte[] {1, 2, 3, 4});
        final InetAddress plain = InetAddress.getByName("1.2.3.4");

        assertThat(new SakuraAddressRecord(named, FIRST, LAST, "lobby")
                .address().toString()).isEqualTo("/1.2.3.4");
        assertThat(new SakuraPlayerProfileView(
                UUID.randomUUID(), "Notch", FIRST, LAST, named).lastAddress())
                .contains(plain);
    }

    @Test
    void namesHaveTheAccountNameLimit() {
        assertThatThrownBy(() -> new SakuraNameRecord("x".repeat(33), FIRST, LAST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SakuraNameRecord(".BedrockPlayer_123456789012345", FIRST, LAST).name())
                .startsWith(".");
    }

    @Test
    void templateNamesAreLowerCasedAndStepsNotNegative() {
        assertThat(new SakuraTemplateRef("Hacking", 3))
                .isEqualTo(new SakuraTemplateRef("hacking", 3));
        assertThatThrownBy(() -> new SakuraTemplateRef("spam", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "spam 1", "spam.1", "*"})
    void templateNamesRejectInvalidCharacters(final String name) {
        assertThatThrownBy(() -> new SakuraTemplateRef(name, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
