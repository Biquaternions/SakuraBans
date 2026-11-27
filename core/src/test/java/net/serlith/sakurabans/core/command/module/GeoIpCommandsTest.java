package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.geoip.Country;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.geoip.GeoIpService;
import net.serlith.sakurabans.core.geoip.GeoIpTestFiles;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S72b: {@code /geoip <target>} with MaxMind's test database. */
class GeoIpCommandsTest extends CommandTest {

    private static final InetAddress BRITISH = GeoIpTestFiles.address("81.2.69.142");
    private static final UUID HELPER = UUID.fromString("6a1f2c4e-0d3b-4f7a-9e21-3c5b7d9f1a2b");

    private GeoIpService geoip;
    private FakeCommandActor helper;

    @Override
    protected String configYaml() {
        return "geoip:\n  enabled: true\ndisplay:\n  censor-ip-addresses: true\n";
    }

    @BeforeEach
    void openGeoIp() {
        this.geoip = GeoIpTestFiles.ready(this.dir, this.config, this.executors, this.clock,
                this.log.logger());
        this.countries = this.geoip;
        this.players.recordConnection(STEVE, "Steve", this.now.get(), BRITISH, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), null, null).join();
        this.helper = FakeCommandActor.player(HELPER, "Helper", Permissions.GEOIP);
    }

    @AfterEach
    void stopGeoIp() {
        this.geoip.stop();
    }

    private String reply(final FakeCommandActor actor, final String line) {
        actor.clear();
        this.run(actor, line);
        return actor.last();
    }

    @Test
    void playersCountriesComeFromTheirLastAddress() {
        Assertions.assertThat(this.reply(this.console, "geoip Steve"))
                .isEqualTo("SakuraBans ...🖉 Steve (81.2.69.142) is in United Kingdom (GB).");
        Assertions.assertThat(this.reply(this.console, "geoip " + STEVE))
                .isEqualTo("SakuraBans ...🖉 Steve (81.2.69.142) is in United Kingdom (GB).");
    }

    @Test
    void censoredAddressesAreShownInFullOnlyWithViewIps() {
        Assertions.assertThat(this.reply(this.helper, "geoip Steve"))
                .isEqualTo("SakuraBans ...🖉 Steve (81.2.*.*) is in United Kingdom (GB).");
        Assertions.assertThat(this.reply(this.helper, "geoip 89.160.20.112"))
                .isEqualTo("SakuraBans ...🖉 89.160.*.* is in Sweden (SE).");

        this.helper.grant(Permissions.VIEW_IPS);

        Assertions.assertThat(this.reply(this.helper, "geoip Steve"))
                .isEqualTo("SakuraBans ...🖉 Steve (81.2.69.142) is in United Kingdom (GB).");
        Assertions.assertThat(this.reply(this.helper, "geoip 89.160.20.112"))
                .isEqualTo("SakuraBans ...🖉 89.160.20.112 is in Sweden (SE).");
    }

    @Test
    void anAddressWithoutCountryIsUnknown() {
        Assertions.assertThat(this.reply(this.console, "geoip 203.0.113.7"))
                .isEqualTo("SakuraBans ...🖉 No country is known for 203.0.113.7.");
        Assertions.assertThat(this.reply(this.console, "geoip 192.168.1.10"))
                .isEqualTo("SakuraBans ...🖉 No country is known for 192.168.1.10.");
    }

    @Test
    void unknownPlayersAndPlayersWithoutAddress() {
        Assertions.assertThat(this.reply(this.console, "geoip Nobody"))
                .isEqualTo("SakuraBans ...🖉 Nobody is known as Nobody.");
        Assertions.assertThat(this.reply(this.console, "geoip Alex"))
                .isEqualTo("SakuraBans ...🖉 No address is known for Alex.");
        Assertions.assertThat(this.reply(this.console, "geoip #12"))
                .contains("not a punishment id");
        Assertions.assertThat(this.reply(this.console, "geoip 81.2.69.0/24"))
                .isEqualTo("SakuraBans ...🖉 Name a player or an address.");
    }

    @Test
    void theCommandNeedsItsPermission() {
        final FakeCommandActor nobody = FakeCommandActor.player(UUID.randomUUID(), "Nobody");

        Assertions.assertThat(this.reply(nobody, "geoip Steve"))
                .doesNotContain("United Kingdom").contains("permission");
    }

    @Test
    void offAndWithoutDatabaseTheReplySaysSo() {
        this.countries = CountryLookup.disabled();
        Assertions.assertThat(this.reply(this.console, "geoip Steve")).isEqualTo(
                "SakuraBans ...🖉 GeoIP is off on this server (geoip.enabled in config.yml).");

        this.countries = new CountryLookup() {
            @Override
            public Optional<Country> country(final InetAddress address) {
                return Optional.empty();
            }

            @Override
            public State state() {
                return State.NO_DATABASE;
            }
        };
        Assertions.assertThat(this.reply(this.console, "geoip Steve"))
                .startsWith("SakuraBans ...🖉 GeoIP has no database yet");
    }
}
