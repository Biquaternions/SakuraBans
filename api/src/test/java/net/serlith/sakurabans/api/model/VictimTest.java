package net.serlith.sakurabans.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VictimTest {

    private static final UUID UUID_A = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static InetAddress ip(final String literal) throws Exception {
        return InetAddress.getByName(literal);
    }

    @Test
    void addressesDropTheHostName() throws Exception {
        final InetAddress named = InetAddress.getByAddress("example.org", new byte[] {1, 2, 3, 4});

        final AddressVictim victim = AddressVictim.of(named);

        assertThat(victim.address().toString()).isEqualTo("/1.2.3.4");
        assertThat(victim).isEqualTo(AddressVictim.of(ip("1.2.3.4")));
    }

    @Test
    void ipv4MappedAddressesBecomeIpv4() throws Exception {
        final byte[] mapped = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 10, 0, 0, 1};

        assertThat(AddressVictim.of(InetAddress.getByAddress(mapped)).address())
                .isInstanceOf(Inet4Address.class)
                .isEqualTo(ip("10.0.0.1"));
    }

    @Test
    void ipv6ScopeIdsAreDropped() throws Exception {
        final InetAddress scoped = Inet6Address.getByAddress(null, ip("fe80::1").getAddress(), 3);

        assertThat(AddressVictim.of(scoped)).isEqualTo(AddressVictim.of(ip("fe80::1")));
    }

    @Test
    void compositeVictimsNormaliseTheirAddress() throws Exception {
        final InetAddress named = InetAddress.getByAddress("host", new byte[] {1, 2, 3, 4});

        assertThat(CompositeVictim.of(UUID_A, named))
                .isEqualTo(CompositeVictim.of(UUID_A, ip("1.2.3.4")));
    }

    @ParameterizedTest
    @CsvSource({
        "10.1.2.3, 8, 10.0.0.0",
        "192.168.77.200, 24, 192.168.77.0",
        "192.168.77.200, 32, 192.168.77.200",
        "255.255.255.255, 1, 128.0.0.0",
        "2001:db8:abcd:12::1, 48, 2001:db8:abcd::",
        "2001:db8::ff, 127, 2001:db8::fe",
    })
    void rangesClearTheHostBits(final String address, final int prefix, final String network)
            throws Exception {
        assertThat(AddressRangeVictim.of(ip(address), prefix).network()).isEqualTo(ip(network));
    }

    @ParameterizedTest
    @CsvSource({"10.0.0.0, 0", "10.0.0.0, 33", "10.0.0.0, -1", "::, 0", "::, 129"})
    void rangesRejectPrefixesThatDontFitTheFamily(final String address, final int prefix) {
        assertThatThrownBy(() -> AddressRangeVictim.of(ip(address), prefix))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix length");
    }

    @Test
    void rangesContainOnlyTheirAddresses() throws Exception {
        final AddressRangeVictim range = AddressRangeVictim.of(ip("10.20.0.0"), 16);

        assertThat(range.contains(ip("10.20.0.0"))).isTrue();
        assertThat(range.contains(ip("10.20.255.255"))).isTrue();
        assertThat(range.contains(ip("10.21.0.0"))).isFalse();
        assertThat(range.contains(ip("::ffff:10.20.1.1"))).isTrue();
        assertThat(range.contains(ip("::a14:101"))).isFalse();
        assertThat(range.ipv4()).isTrue();
    }

    @Test
    void ipv6RangesContainOnlyTheirAddresses() throws Exception {
        final AddressRangeVictim range = AddressRangeVictim.of(ip("2001:db8::"), 32);

        assertThat(range.contains(ip("2001:db8:ffff::1"))).isTrue();
        assertThat(range.contains(ip("2001:db9::"))).isFalse();
        assertThat(range.contains(ip("10.0.0.1"))).isFalse();
        assertThat(range.ipv4()).isFalse();
    }

    @ValueSource(strings = {"10.0.0.7", "2001:db8::7"})
    @ParameterizedTest
    void fullLengthRangesContainOneAddress(final String literal) throws Exception {
        final InetAddress address = ip(literal);
        final int bits = address.getAddress().length * Byte.SIZE;

        assertThat(AddressRangeVictim.of(address, bits).contains(address)).isTrue();
    }

    @Test
    void userImplementationsGetTheDefaultMethods() throws Exception {
        final InetAddress network = ip("10.0.0.0");
        final AddressRangeVictim own = new AddressRangeVictim() {
            @Override
            public InetAddress network() {
                return network;
            }

            @Override
            public int prefixLength() {
                return 8;
            }
        };

        assertThat(own.contains(ip("10.9.9.9"))).isTrue();
        assertThat(own.ipv4()).isTrue();
    }

    @Test
    void factoriesReturnValueObjects() {
        assertThat(PlayerVictim.of(UUID_A)).isEqualTo(PlayerVictim.of(UUID_A))
                .hasSameHashCodeAs(PlayerVictim.of(UUID_A));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void missingValuesAreRejected() {
        assertThatThrownBy(() -> PlayerVictim.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AddressVictim.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CompositeVictim.of(UUID_A, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anErasedAddressKeepsItsHashInLowerCase() {
        final String hash = "AB".repeat(32);

        final ErasedAddressVictim erased = ErasedAddressVictim.of(hash, UUID_A);

        assertThat(erased.hash()).isEqualTo("ab".repeat(32));
        assertThat(erased.uuid()).contains(UUID_A);
        assertThat(ErasedAddressVictim.of(hash, null).uuid()).isEmpty();
        assertThat(erased).isEqualTo(ErasedAddressVictim.of("ab".repeat(32), UUID_A));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "zz"})
    void anErasedAddressNeedsSixtyFourHexDigits(final String prefix) {
        assertThatThrownBy(() -> ErasedAddressVictim.of(prefix + "0".repeat(60), null))
                .isInstanceOf(net.serlith.sakurabans.api.validation.ValidationException.class);
    }
}
