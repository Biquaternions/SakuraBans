package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InetAddressesTest {

    @Test
    void parsesIpv4() throws Exception {
        assertThat(InetAddresses.parseLiteral("203.0.113.7"))
                .contains(InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 7}));
        assertThat(InetAddresses.parseLiteral("0.0.0.0")).isPresent();
        assertThat(InetAddresses.parseLiteral("255.255.255.255")).isPresent();
    }

    @Test
    void parsesIpv6WithAndWithoutBrackets() {
        assertThat(InetAddresses.parseLiteral("2001:db8::1")).get()
                .isInstanceOf(Inet6Address.class)
                .isEqualTo(InetAddresses.parseLiteral("[2001:db8::1]").orElseThrow());
    }

    @Test
    void mappedAddressesBecomeIpv4AndScopesAreDropped() {
        assertThat(InetAddresses.parseLiteral("::ffff:10.0.0.1")).get()
                .isInstanceOf(Inet4Address.class)
                .hasToString("/10.0.0.1");
        assertThat(InetAddresses.parseLiteral("fe80::1%3")).get()
                .hasToString("/fe80:0:0:0:0:0:0:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "example.org", "localhost", "1.2.3", "01.2.3.4", "1.2.3.4.5",
        "256.1.1.1", "1.2.3.4 ", "0x7f.0.0.1", "fe80::1%eth0", "1::2::3", "::g"})
    void rejectsHostNamesAndSloppyLiterals(final String text) {
        assertThat(InetAddresses.parseLiteral(text)).isEmpty();
    }

    // InetAddress resolves a named zone against this machine's interfaces, so a name that
    // exists here must be rejected as well (the GitHub runner has eth0).
    @Test
    void rejectsTheZonesOfThisMachinesInterfaces() throws Exception {
        final List<String> names = NetworkInterface.networkInterfaces()
                .map(NetworkInterface::getName)
                .toList();

        assertThat(names).allSatisfy(name ->
                assertThat(InetAddresses.parseLiteral("fe80::1%" + name)).isEmpty());
        assertThat(InetAddresses.parseLiteral("[fe80::1%" + names.getFirst() + "]")).isEmpty();
        assertThat(InetAddresses.parseLiteral("fe80::1%")).isEmpty();
    }

    @Test
    void convertsToAndFromBytes() throws Exception {
        final InetAddress address = InetAddress.getByName("2001:db8::7");

        assertThat(InetAddresses.fromBytes(InetAddresses.toBytes(address))).isEqualTo(address);
        assertThatThrownBy(() -> InetAddresses.fromBytes(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InetAddresses.fromBytes(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void censorsTheHostPart() throws Exception {
        assertThat(InetAddresses.censor(InetAddress.getByName("203.0.113.7")))
                .isEqualTo("203.0.*.*");
        assertThat(InetAddresses.censor(InetAddress.getByName("2001:db8:abcd::1")))
                .isEqualTo("2001:db8:*");
    }
}
