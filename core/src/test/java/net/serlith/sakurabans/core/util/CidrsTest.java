package net.serlith.sakurabans.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CidrsTest {

    @ParameterizedTest
    @CsvSource({
        "10.0.0.0/8, 10.0.0.0, 8",
        "10.1.2.3/8, 10.0.0.0, 8",
        "192.168.1.77/32, 192.168.1.77, 32",
        "2001:db8:abcd::1/32, 2001:db8::, 32",
        "[2001:db8::]/48, 2001:db8::, 48",
        "10.*.*.*, 10.0.0.0, 8",
        "10.0.*.*, 10.0.0.0, 16",
        "10.0.5.*, 10.0.5.0, 24",
    })
    void parsesCidrAndWildcards(final String text, final String network, final int prefix)
            throws Exception {
        final AddressRangeVictim range = Cidrs.parse(text).orElseThrow();

        assertThat(range.network()).isEqualTo(InetAddress.getByName(network));
        assertThat(range.prefixLength()).isEqualTo(prefix);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "10.0.0.0", "10.0.0.0/0", "10.0.0.0/33", "::/129", "*.*.*.*",
        "10.*.1.*", "10.0.0.0.*", "10.0.*", "example.org/8", "10.0.0.0/-1", "1.2.3/24"})
    void rejectsEverythingElse(final String text) {
        assertThat(Cidrs.parse(text)).isEmpty();
    }
}
