package net.serlith.sakurabans.core.message;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.geoip.GeoIpService;
import net.serlith.sakurabans.core.geoip.GeoIpTestFiles;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code <country>} in each message that has an address, with this test's own texts, and
 * the editor's lists. 81.2.69.142 is in the United Kingdom in MaxMind's test database.
 */
class CountryTagTest extends CommandTest {

    private static final InetAddress BRITISH = GeoIpTestFiles.address("81.2.69.142");

    private GeoIpService geoip;
    private FakeOnlinePlayer staff;

    @Override
    protected String configYaml() {
        return "geoip:\n  enabled: true\n  blacklist: [JP]\n"
                + "alts:\n  join-notification:\n    threshold: 1\n    only-if-punished: false\n";
    }

    @BeforeEach
    void texts() throws Exception {
        final Path lang = this.dir.resolve("lang");
        Files.createDirectories(lang);
        Files.writeString(lang.resolve("en_US.yml"),
                """
                notify:
                  attempt_join: 'join <name> <country>'
                  attempt_geoip: 'geoip <name> <country>'
                  alts:
                    join: 'alts <name> <country>'
                alts:
                  entry: 'alt <name> <country>'
                iphistory:
                  entry: 'address <address> <country>'
                check:
                  detail: 'detail #<id> <country>'
                screen:
                  geoip: 'screen <country>'
                """);
        this.messages.load();
        this.geoip = GeoIpTestFiles.ready(this.dir, this.config, this.executors, this.clock,
                this.log.logger());
        this.countries = this.geoip;
        this.staff = this.platform.join(ALEX, "Alex", GeoIpTestFiles.address("198.51.100.9"))
                .grant(Permissions.NOTIFY_ATTEMPT_JOIN, Permissions.ALTS_NOTIFY);
        // Mod shares Steve's address, so each is the other's alt.
        this.players.recordConnection(MOD, "Mod", this.now.get(), BRITISH, null).join();
        this.players.recordConnection(STEVE, "Steve", this.now.get(), BRITISH, null).join();
    }

    @AfterEach
    void stopGeoIp() {
        this.geoip.stop();
    }

    private Punishment ban(final Victim victim) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, victim, CONSOLE).build(), PunishOptions.defaults())
                .join()).punishment();
    }

    private LoginDecision login(final InetAddress address) {
        return this.loginGate.check(new LoginAttempt(STEVE, "Steve", address, null)).join();
    }

    private String lastNotice() {
        return this.staff.messages().isEmpty() ? "" : plain(this.staff.messages().getLast());
    }

    @Test
    void theAltJoinNoticeNamesTheJoiningPlayersCountry() {
        this.login(BRITISH);

        eventually(() -> this.lastNotice().startsWith("alts "));
        Assertions.assertThat(this.lastNotice()).isEqualTo("alts Steve United Kingdom");
    }

    @Test
    void theBannedJoinNoticeNamesTheCountry() {
        this.ban(PlayerVictim.of(STEVE));

        Assertions.assertThat(this.login(BRITISH)).isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.lastNotice()).isEqualTo("join Steve United Kingdom");
    }

    @Test
    void theGeoIpScreenAndNoticeNameTheCountry() {
        final InetAddress japanese = GeoIpTestFiles.address("2001:218::1");

        Assertions.assertThat(this.login(japanese)).isInstanceOfSatisfying(
                LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(plain(denied.screen())).isEqualTo("screen Japan"));
        Assertions.assertThat(this.lastNotice()).isEqualTo("geoip Steve Japan");
    }

    @Test
    void altListsAndAddressHistoriesNameTheAddressesCountry() {
        this.run(this.console, "alts Steve");
        Assertions.assertThat(this.console.texts()).contains("alt Mod United Kingdom");

        this.console.clear();
        this.run(this.console, "iphistory Steve");
        Assertions.assertThat(this.console.texts()).contains("address 81.2.69.142 United Kingdom");
    }

    @Test
    void detailsNameTheCountryOfAnAddressPunishmentOnly() {
        final Punishment address = this.ban(AddressVictim.of(BRITISH));
        final Punishment player = this.ban(PlayerVictim.of(STEVE));

        this.run(this.console, "checkban #" + address.id());
        this.run(this.console, "sb info " + address.id());
        this.run(this.console, "checkban #" + player.id());

        Assertions.assertThat(this.console.texts()).contains(
                "detail #" + address.id() + " United Kingdom",
                "detail #" + player.id() + " ");
        Assertions.assertThat(this.console.texts().stream()
                .filter(("detail #" + address.id() + " United Kingdom")::equals)).hasSize(2);
        Assertions.assertThat(plain(this.enforcementMessages.summary(address, Locale.US)))
                .isEqualTo("detail #" + address.id() + " United Kingdom");
    }

    @Test
    void theTagIsEmptyWhileGeoIpIsOff() {
        this.countries = CountryLookup.disabled();

        this.run(this.console, "iphistory Steve");

        Assertions.assertThat(this.console.texts()).contains("address 81.2.69.142 ");
    }

    @Test
    void theEditorListsTheTagForThoseMessagesOnly() {
        Assertions.assertThat(MessageKey.all().stream().map(MessageKey::key).toList())
                .as("every listed key exists")
                .containsAll(Placeholders.COUNTRY_KEYS);
        Assertions.assertThat(this.messageTemplates.schema()).allSatisfy(field ->
                Assertions.assertThat(field.placeholders().contains(Placeholders.COUNTRY_TAG))
                        .as(field.key())
                        .isEqualTo(Placeholders.COUNTRY_KEYS.contains(field.key())));
        // Of the built-in texts, only the GeoIP texts use the tag.
        final Map<String, String> english = MessageService.builtInTexts();
        english.forEach((key, text) -> Assertions.assertThat(text.contains("<country>"))
                .as(key)
                .isEqualTo(key.equals("screen.geoip") || key.equals("notify.attempt_geoip")
                        || key.equals("geoip.result")));
        Assertions.assertThat(Placeholders.COUNTRY_KEYS).contains("screen.geoip",
                "notify.attempt_geoip", "geoip.result");
    }
}
