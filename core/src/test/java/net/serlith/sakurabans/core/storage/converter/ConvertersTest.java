package net.serlith.sakurabans.core.storage.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import org.junit.jupiter.api.Test;

class ConvertersTest {

    private enum Color { RED, GREEN }

    @Test
    void uuidsRoundTripThroughSixteenBytes() {
        final UuidBytesConverter converter = new UuidBytesConverter();
        final UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

        final byte[] bytes = converter.to(uuid);

        assertThat(bytes).hasSize(16);
        assertThat(converter.from(bytes)).isEqualTo(uuid);
        assertThat(converter.from(null)).isNull();
        assertThat(converter.to(null)).isNull();
    }

    @Test
    void addressesKeepTheirFamily() throws Exception {
        final InetAddressBytesConverter converter = new InetAddressBytesConverter();
        final InetAddress v4 = InetAddress.getByName("203.0.113.7");
        final InetAddress v6 = InetAddress.getByName("2001:db8::1");
        final InetAddress mapped = InetAddress.getByName("::ffff:203.0.113.7");

        assertThat(converter.to(v4)).hasSize(4);
        assertThat(converter.to(v6)).hasSize(16);
        assertThat(converter.to(mapped)).as("mapped addresses are IPv4").hasSize(4);
        assertThat(converter.from(converter.to(v4))).isEqualTo(v4);
        assertThat(converter.from(converter.to(v6))).isEqualTo(v6);
        assertThatThrownBy(() -> converter.from(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instantsAndDurationsAreMilliseconds() {
        final InstantMillisConverter instants = new InstantMillisConverter();
        final DurationMillisConverter durations = new DurationMillisConverter();

        assertThat(instants.to(Instant.ofEpochSecond(1, 999_999_999))).isEqualTo(1999L);
        assertThat(instants.from(1999L)).isEqualTo(Instant.ofEpochMilli(1999));
        assertThat(durations.to(Duration.ofMinutes(1))).isEqualTo(60_000L);
        assertThat(durations.from(60_000L)).isEqualTo(Duration.ofMinutes(1));
        assertThat(instants.from(null)).isNull();
        assertThat(durations.to(null)).isNull();
    }

    @Test
    void flagsAreZeroOrOne() {
        final FlagConverter flags = new FlagConverter();

        assertThat(flags.to(true)).isEqualTo((short) 1);
        assertThat(flags.from((short) 0)).isFalse();
        assertThatThrownBy(() -> flags.from((short) 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enumCodesAreExplicitAndComplete() {
        final EnumCode<Color> codes =
                EnumCode.of(Color.class, Map.of(Color.RED, 7, Color.GREEN, 3));
        final EnumCodeConverter<Color> converter = new EnumCodeConverter<>(codes);

        assertThat(converter.to(Color.RED)).isEqualTo((short) 7);
        assertThat(converter.from((short) 3)).isEqualTo(Color.GREEN);
        assertThatThrownBy(() -> converter.from((short) 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Color");
        assertThatThrownBy(() -> EnumCode.of(Color.class, Map.of(Color.RED, 1)))
                .hasMessageContaining("GREEN has no code");
        assertThatThrownBy(() -> EnumCode.of(Color.class, Map.of(Color.RED, 1, Color.GREEN, 1)))
                .hasMessageContaining("share the code 1");
        assertThatThrownBy(() -> EnumCode.of(Color.class, Map.of(Color.RED, 1, Color.GREEN, -1)))
                .hasMessageContaining("out of range");
    }

    @Test
    void storedCodesNeverChange() {
        assertThat(SbTypes.PUNISHMENT_TYPE_CODES.code(PunishmentType.BAN)).isZero();
        assertThat(SbTypes.PUNISHMENT_TYPE_CODES.code(PunishmentType.KICK)).isEqualTo((short) 3);
    }

    @Test
    void everyEnumCodeTableRoundTrips() throws Exception {
        final List<String> checked = new ArrayList<>();
        for (final Field field : SbTypes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == EnumCode.class) {
                roundTrip((EnumCode<?>) field.get(null));
                checked.add(field.getName());
            }
        }
        assertThat(checked).hasSizeGreaterThanOrEqualTo(8);
    }

    private static <E extends Enum<E>> void roundTrip(final EnumCode<E> codes) {
        for (final E constant : codes.type().getEnumConstants()) {
            assertThat(codes.value(codes.code(constant))).isEqualTo(constant);
        }
    }
}
