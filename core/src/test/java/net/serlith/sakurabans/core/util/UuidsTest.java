package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidsTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void parsesBothForms() {
        assertThat(Uuids.parse("069a79f4-44e9-4726-a5be-fca90e38aaf5")).contains(NOTCH);
        assertThat(Uuids.parse("069a79f444e94726a5befca90e38aaf5")).contains(NOTCH);
        assertThat(Uuids.parse("069A79F444E94726A5BEFCA90E38AAF5")).contains(NOTCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1-1-1-1-1", "069a79f4-44e9-4726-a5be-fca90e38aaf",
        "069a79f444e94726a5befca90e38aaf5a", "g69a79f444e94726a5befca90e38aaf5"})
    void rejectsOtherShapes(final String text) {
        assertThat(Uuids.parse(text)).isEmpty();
    }

    @Test
    void convertsToAndFromBytes() {
        assertThat(Uuids.fromBytes(Uuids.toBytes(NOTCH))).isEqualTo(NOTCH);
        assertThat(Uuids.toBytes(Uuids.ZERO)).containsOnly(0);
        assertThatThrownBy(() -> Uuids.fromBytes(new byte[15]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offlineUuidsAreNameBasedAndCaseSensitive() {
        assertThat(Uuids.offline("Notch").version()).isEqualTo(3);
        assertThat(Uuids.offline("Notch")).isEqualTo(Uuids.offline("Notch"))
                .isNotEqualTo(Uuids.offline("notch"));
    }

    @Test
    void recognisesFloodgateUuids() {
        assertThat(Uuids.isFloodgate(UUID.fromString("00000000-0000-0000-0009-01f64f65c7c3")))
                .isTrue();
        assertThat(Uuids.isFloodgate(NOTCH)).isFalse();
        assertThat(Uuids.isFloodgate(Uuids.ZERO)).isFalse();
    }
}
