package net.serlith.sakurabans.core.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AddressHashesTest {

    @Test
    void theHashIsPlainSha256OfTheStoredBytes() throws Exception {
        final InetAddress address = InetAddress.getByName("2001:db8::1");

        Assertions.assertThat(AddressHashes.of(address))
                .hasSize(AddressHashes.LENGTH)
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(address.getAddress()));
    }

    @Test
    void mappedAddressesHashLikeTheirIpv4Form() throws Exception {
        final InetAddress ipv4 = InetAddress.getByName("203.0.113.7");
        final byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        System.arraycopy(ipv4.getAddress(), 0, mapped, 12, 4);
        final InetAddress ipv6 = Inet6Address.getByAddress(null, mapped, -1);

        Assertions.assertThat(ipv6).isInstanceOf(Inet6Address.class);
        Assertions.assertThat(AddressHashes.of(ipv6)).isEqualTo(AddressHashes.of(ipv4));
        Assertions.assertThat(AddressHashes.of(InetAddress.getByName("203.0.113.8")))
                .isNotEqualTo(AddressHashes.of(ipv4));
    }
}
