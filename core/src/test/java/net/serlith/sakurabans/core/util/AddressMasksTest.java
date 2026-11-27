package net.serlith.sakurabans.core.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AddressMasksTest {

    @Test
    void censorsAddressesWhereverTheyStand() {
        Assertions.assertThat(AddressMasks.censor("BAN 198.51.100.77 global permanent x"))
                .isEqualTo("BAN 198.51.*.* global permanent x");
        Assertions.assertThat(AddressMasks.censor("198.51.100.0/24 and 10.1.2.3"))
                .isEqualTo("198.51.*.*/24 and 10.1.*.*");
        Assertions.assertThat(AddressMasks.censor("uuid@198.51.100.7"))
                .isEqualTo("uuid@198.51.*.*");
        Assertions.assertThat(AddressMasks.censor("{k=203.0.113.9, j=1}"))
                .isEqualTo("{k=203.0.*.*, j=1}");
    }

    @Test
    void censorsIpv6InFullAndCompressedForm() {
        Assertions.assertThat(AddressMasks.censor("ban 2001:db8:0:0:0:0:0:1 now"))
                .isEqualTo("ban 2001:db8:* now");
        Assertions.assertThat(AddressMasks.censor("2001:db8::1")).isEqualTo("2001:db8:*");
        Assertions.assertThat(AddressMasks.censor("fe80::1%eth0")).isEqualTo("fe80:0:*");
        Assertions.assertThat(AddressMasks.censor("2001:db8:0:0:0:0:0:0/32"))
                .isEqualTo("2001:db8:*/32");
    }

    @Test
    void leavesOtherTextAlone() {
        for (final String text : new String[] {"2026-11-19T12:00:00Z at 10:30:45", "1.2.3",
                "v1.2.3.4.5", "a :: b", "::", "Foo::bar", "256.1.1.1", "names=2",
                "9f86d081884c7d659a2feaa0", "8667ba71-b85a-4004-af54-457a9734eed7",
                "erased:ab12", ""}) {
            Assertions.assertThat(AddressMasks.censor(text)).as(text).isEqualTo(text);
        }
    }
}
