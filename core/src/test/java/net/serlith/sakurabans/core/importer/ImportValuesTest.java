package net.serlith.sakurabans.core.importer;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.util.Uuids;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ImportValuesTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    void uuidsComeFromBytesAndText() {
        Assertions.assertThat(ImportValues.uuid(Uuids.toBytes(STEVE))).contains(STEVE);
        Assertions.assertThat(ImportValues.uuid(STEVE.toString())).contains(STEVE);
        Assertions.assertThat(ImportValues.uuid(STEVE.toString().replace("-", "")))
                .contains(STEVE);
        Assertions.assertThat(ImportValues.uuid("CONSOLE")).isEmpty();
        Assertions.assertThat(ImportValues.uuid(new byte[3])).isEmpty();
        Assertions.assertThat(ImportValues.uuid(Uuids.ZERO.toString())).isEmpty();
        Assertions.assertThat(ImportValues.uuid(null)).isEmpty();
    }

    @Test
    void addressesComeFromBytesAndLiteralsOnly() throws Exception {
        final InetAddress address = InetAddress.getByName("203.0.113.7");
        Assertions.assertThat(ImportValues.address(address.getAddress())).contains(address);
        Assertions.assertThat(ImportValues.address("203.0.113.7")).contains(address);
        Assertions.assertThat(ImportValues.address("/203.0.113.7")).contains(address);
        Assertions.assertThat(ImportValues.address("::ffff:203.0.113.7")).contains(address);
        Assertions.assertThat(ImportValues.address("example.com")).isEmpty();
        Assertions.assertThat(ImportValues.address("#")).isEmpty();
    }

    @Test
    void zeroAndNegativeTimesMeanNone() {
        Assertions.assertThat(ImportValues.seconds(0)).isEmpty();
        Assertions.assertThat(ImportValues.millis(-1)).isEmpty();
        Assertions.assertThat(ImportValues.seconds(1_700_000_000L))
                .contains(Instant.ofEpochSecond(1_700_000_000L));
        Assertions.assertThat(ImportValues.millis((Number) null)).isEmpty();
        Assertions.assertThat(ImportValues.millis(Long.valueOf(5_000)))
                .contains(Instant.ofEpochMilli(5_000));
    }

    @Test
    void colourCodesAreStripped() {
        Assertions.assertThat(ImportValues.plain("&4&lBanned§r: &#AABBCCtoxic §x§1§2§3§4§5§6!"))
                .isEqualTo("Banned: toxic !");
        Assertions.assertThat(ImportValues.plain("50% & more")).isEqualTo("50% & more");
    }

    @Test
    void scopesAndOperators() {
        Assertions.assertThat(ImportValues.scope("*")).contains(GlobalScope.INSTANCE);
        Assertions.assertThat(ImportValues.scope(null)).contains(GlobalScope.INSTANCE);
        Assertions.assertThat(ImportValues.scope("Survival"))
                .contains(ServerScope.of("survival"));
        Assertions.assertThat(ImportValues.scope("my server")).isEmpty();

        final UUID console = UUID.randomUUID();
        Assertions.assertThat(ImportValues.operator(null, "CONSOLE"))
                .isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(ImportValues.operator(console, "Server", console))
                .isEqualTo(ConsoleOperator.INSTANCE);
        Assertions.assertThat(ImportValues.operator(STEVE, "Steve"))
                .isEqualTo(PlayerOperator.of(STEVE, "Steve"));
        Assertions.assertThat(ImportValues.operator(STEVE, null))
                .isEqualTo(CustomOperator.of(STEVE.toString(), STEVE));
        Assertions.assertThat(ImportValues.operator(null, "Mod"))
                .isEqualTo(CustomOperator.of("Mod", null));
    }

    @Test
    void victims() throws Exception {
        final InetAddress address = InetAddress.getByName("203.0.113.7");
        Assertions.assertThat(ImportValues.victim(STEVE, address))
                .contains(CompositeVictim.of(STEVE, address));
        Assertions.assertThat(ImportValues.victim(null, null)).isEmpty();
    }
}
