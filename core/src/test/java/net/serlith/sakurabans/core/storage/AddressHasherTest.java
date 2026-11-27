package net.serlith.sakurabans.core.storage;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AddressHasherTest {

    static final String KEY = "test-address-hash-key-0123456789abcdef";

    @Test
    void theHashIsAnHmacOfTheStoredBytesUnderTheKey() throws Exception {
        final InetAddress address = InetAddress.getByName("2001:db8::1");
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        Assertions.assertThat(AddressHasher.keyed(KEY).hash(address))
                .hasSize(AddressHasher.LENGTH)
                .isEqualTo(mac.doFinal(address.getAddress()))
                .isNotEqualTo(MessageDigest.getInstance("SHA-256").digest(address.getAddress()));
    }

    @Test
    void theSameKeyGivesTheSameHashOnEveryInstanceAndAnotherKeyAnother() throws Exception {
        final InetAddress address = InetAddress.getByName("203.0.113.7");
        final AddressHasher first = AddressHasher.keyed(KEY);
        final AddressHasher second = new AddressHasher();
        second.key(KEY);

        Assertions.assertThat(second.hash(address)).isEqualTo(first.hash(address));
        Assertions.assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        final AddressHasher other = AddressHasher.keyed(KEY + "x");
        Assertions.assertThat(other.hash(address)).isNotEqualTo(first.hash(address));
        Assertions.assertThat(other.fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void mappedAddressesHashLikeTheirIpv4Form() throws Exception {
        final AddressHasher hasher = AddressHasher.keyed(KEY);
        final InetAddress ipv4 = InetAddress.getByName("203.0.113.7");
        final byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        System.arraycopy(ipv4.getAddress(), 0, mapped, 12, 4);
        final InetAddress ipv6 = Inet6Address.getByAddress(null, mapped, -1);

        Assertions.assertThat(ipv6).isInstanceOf(Inet6Address.class);
        Assertions.assertThat(hasher.hash(ipv6)).isEqualTo(hasher.hash(ipv4));
        Assertions.assertThat(hasher.hash(InetAddress.getByName("203.0.113.8")))
                .isNotEqualTo(hasher.hash(ipv4));
    }

    @Test
    void nothingIsHashedBeforeTheKeyIsSetAndTheKeyIsNeverPrinted() throws Exception {
        final AddressHasher hasher = new AddressHasher();
        Assertions.assertThat(hasher.ready()).isFalse();
        Assertions.assertThatThrownBy(() -> hasher.hash(InetAddress.getByName("203.0.113.7")))
                .isInstanceOf(IllegalStateException.class);

        hasher.key(KEY);

        Assertions.assertThat(hasher.ready()).isTrue();
        Assertions.assertThat(hasher.toString()).doesNotContain(KEY);
        Assertions.assertThat(hasher.fingerprint()).doesNotContain(KEY).hasSize(64);
    }
}
