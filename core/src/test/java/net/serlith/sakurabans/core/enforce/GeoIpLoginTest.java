package net.serlith.sakurabans.core.enforce;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.geoip.Country;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.geoip.GeoIpService;
import net.serlith.sakurabans.core.geoip.GeoIpTestFiles;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.OfflinePermissions;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The country lists at login, with MaxMind's test database: 81.2.69.142 is in the United
 * Kingdom, 89.160.20.112 in Sweden, 2001:218::1 in Japan; 203.0.113.7 (the tests' home) isn't
 * listed, and 192.168.1.10 is private.
 */
class GeoIpLoginTest extends ServiceTest {

    static final InetAddress BRITISH = GeoIpTestFiles.address("81.2.69.142");
    static final InetAddress SWEDISH = GeoIpTestFiles.address("89.160.20.112");
    static final InetAddress JAPANESE = GeoIpTestFiles.address("2001:218::1");
    static final InetAddress PRIVATE = GeoIpTestFiles.address("192.168.1.10");

    private GeoIpService geoip;
    private CountingLookup counting;
    private FakeOnlinePlayer staff;

    @Override
    protected String configYaml() {
        return "geoip:\n  enabled: true\n";
    }

    @BeforeEach
    void openGeoIp() {
        this.geoip = GeoIpTestFiles.ready(this.dir, this.config, this.executors, this.clock,
                this.log.logger());
        this.counting = new CountingLookup(this.geoip);
        this.countries = this.counting;
        this.staff = this.platform.join(ALEX, "Alex", GeoIpTestFiles.address("198.51.100.9"))
                .grant(Permissions.NOTIFY_ATTEMPT_JOIN);
    }

    @AfterEach
    void stopGeoIp() {
        this.geoip.stop();
    }

    private void lists(final String geoipLines) throws IOException {
        this.lists(geoipLines, "");
    }

    private void lists(final String geoipLines, final String otherSections) throws IOException {
        Files.writeString(this.dir.resolve("config.yml"), this.baseConfigYaml()
                + "geoip:\n  enabled: true\n" + geoipLines.indent(2) + otherSections);
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
    }

    private LoginDecision login(final UUID uuid, final String name, final InetAddress address) {
        return this.loginGate.check(new LoginAttempt(uuid, name, address, null)).join();
    }

    private static PlayerDenyEvent.Denial denial(final LoginDecision decision) {
        Assertions.assertThat(decision).isInstanceOf(LoginDecision.Denied.class);
        return ((LoginDecision.Denied) decision).denial();
    }

    private String lastNotice() {
        return plain(this.staff.messages().getLast());
    }

    @Test
    void blacklistedCountriesAreTurnedAwayWithTheirScreenAndStaffAreTold() throws IOException {
        this.lists("blacklist: [GB]");
        final int before = this.staff.messages().size();

        final LoginDecision decision = this.login(STEVE, "Steve", BRITISH);

        Assertions.assertThat(decision).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> {
                    Assertions.assertThat(denied.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
                    Assertions.assertThat(denied.punishment()).isNull();
                    Assertions.assertThat(denied.fallback()).isEmpty();
                    Assertions.assertThat(plain(denied.screen())).startsWith("SakuraBans\n")
                            .contains("\n\nYou can't join this server from where you are.\n"
                                    + "Country: United Kingdom\n\n");
                });
        Assertions.assertThat(this.events(PlayerDenyEvent.class)).singleElement()
                .satisfies(event -> {
                    Assertions.assertThat(event.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
                    Assertions.assertThat(event.punishment()).isEmpty();
                    Assertions.assertThat(event.address()).isEqualTo(BRITISH);
                });
        Assertions.assertThat(this.staff.messages()).hasSize(before + 1);
        Assertions.assertThat(this.lastNotice()).isEqualTo(
                "SakuraBans ...🖉 Steve tried to join from a country that isn't allowed:"
                        + " United Kingdom");
        Assertions.assertThat(this.mutes.loaded(STEVE)).isFalse();
        Assertions.assertThat(this.login(ALEX, "Alex", SWEDISH))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void deniedJoinsAreNoPunishmentAndLeaveNothingStoredOrAudited() throws IOException {
        this.lists("blacklist: [GB]");
        final int audited = this.count(SakuraTables.AUDIT_LOG);

        Assertions.assertThat(denial(this.login(STEVE, "Steve", BRITISH)))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);

        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.count(SakuraTables.AUDIT_LOG)).isEqualTo(audited);
        Assertions.assertThat(this.events(PunishmentCreatedEvent.class)).isEmpty();
        Assertions.assertThat(this.published).isEmpty();
    }

    @Test
    void theStaffNoticeSharesTheJoinThrottle() throws IOException {
        this.lists("blacklist: [GB]");
        final int before = this.staff.messages().size();

        this.login(STEVE, "Steve", BRITISH);
        this.login(STEVE, "Steve", BRITISH);
        Assertions.assertThat(this.staff.messages()).hasSize(before + 1);

        this.now.set(this.now.get().plus(Duration.ofSeconds(30)));
        this.login(STEVE, "Steve", BRITISH);
        Assertions.assertThat(this.staff.messages()).hasSize(before + 2);
    }

    @Test
    void theNoticeNeedsTheJoinAttemptPermission() throws IOException {
        this.lists("blacklist: [GB]");
        final FakeOnlinePlayer other = this.platform.join(UUID.randomUUID(), "Helper",
                GeoIpTestFiles.address("198.51.100.10"));
        final int before = other.messages().size();

        this.login(STEVE, "Steve", BRITISH);

        Assertions.assertThat(other.messages()).hasSize(before);
    }

    @Test
    void onlyWhitelistedCountriesMayJoin() throws IOException {
        this.lists("whitelist: [SE]");

        Assertions.assertThat(this.login(STEVE, "Steve", SWEDISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(denial(this.login(ALEX, "Alex", BRITISH)))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
        Assertions.assertThat(denial(this.login(MOD, "Mod", JAPANESE)))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
    }

    @Test
    void theWhitelistWinsWhenBothListsAreSet() throws IOException {
        this.lists("whitelist: [SE]\nblacklist: [SE, JP]");

        Assertions.assertThat(this.login(STEVE, "Steve", SWEDISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", BRITISH))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.login(MOD, "Mod", JAPANESE))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void unknownCountriesMayJoinByDefault() throws IOException {
        this.lists("blacklist: [GB]");

        Assertions.assertThat(this.login(STEVE, "Steve", this.home))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", PRIVATE))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void unknownCountriesAreTurnedAwayWithoutAllowUnknown() throws IOException {
        this.lists("blacklist: [GB]\nallow-unknown: false");

        final LoginDecision unlisted = this.login(STEVE, "Steve", this.home);
        final LoginDecision lan = this.login(ALEX, "Alex", PRIVATE);

        Assertions.assertThat(denial(unlisted))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
        Assertions.assertThat(denial(lan)).isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
        Assertions.assertThat(plain(((LoginDecision.Denied) unlisted).screen()))
                .contains("\nCountry: unknown\n");
        Assertions.assertThat(this.lastNotice()).isEqualTo(
                "SakuraBans ...🖉 Alex tried to join from a country that isn't allowed: unknown");
        Assertions.assertThat(this.login(MOD, "Mod", SWEDISH))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void privateAddressesAreUnknownEvenWithWhitelist() throws IOException {
        this.lists("whitelist: [SE]");

        Assertions.assertThat(this.login(STEVE, "Steve", PRIVATE))
                .as("allow-unknown is true by default")
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.geoip.country(PRIVATE)).isEmpty();
    }

    @Test
    void allowListedPlayersAndAddressesPassWithoutLookup() throws IOException {
        this.allowList.allow(STEVE, CONSOLE, null).join();
        this.allowList.allow(SWEDISH, CONSOLE, null).join();
        this.lists("whitelist: [JP]");
        final int lookups = this.counting.lookups.get();

        Assertions.assertThat(this.login(STEVE, "Steve", BRITISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", SWEDISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.counting.lookups.get()).isEqualTo(lookups);
        Assertions.assertThat(this.login(MOD, "Mod", BRITISH))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void theBypassPermissionComesFromLuckPerms() throws IOException {
        this.lists("blacklist: [GB]");
        this.offline = new OfflinePermissions() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
                return CompletableFuture.completedFuture(Optional.of(uuid.equals(STEVE)
                        && node.equals(Permissions.GEOIP_BYPASS)));
            }

            @Override
            public CompletableFuture<OptionalInt> weight(final UUID uuid) {
                return CompletableFuture.completedFuture(OptionalInt.empty());
            }
        };

        Assertions.assertThat(this.login(STEVE, "Steve", BRITISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", BRITISH))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void withoutLuckPermsNobodyHasTheBypassAtLogin() throws IOException {
        this.lists("blacklist: [GB]");

        Assertions.assertThat(this.login(STEVE, "Steve", BRITISH))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.log.entries("warn")).anySatisfy(entry ->
                Assertions.assertThat(entry.format()).contains("GeoIP bypasses"));
    }

    @Test
    void cancellingTheDenialLetsThePlayerIn() throws IOException {
        this.lists("blacklist: [GB]");
        this.bus.subscribe(PlayerDenyEvent.class, EventPriority.NORMAL,
                event -> event.cancelled(true));
        final int before = this.staff.messages().size();

        Assertions.assertThat(this.login(STEVE, "Steve", BRITISH))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.staff.messages()).hasSize(before);
    }

    @Test
    void bannedPlayersSeeTheBanFirst() throws IOException {
        this.lists("blacklist: [GB]");
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("cheating").build(), PunishOptions.defaults()).join();

        Assertions.assertThat(denial(this.login(STEVE, "Steve", BRITISH)))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_BANNED);
        Assertions.assertThat(this.lastNotice()).contains("tried to join but is banned");
    }

    @Test
    void backendsWithoutTheBanCheckStillCheckCountries() throws IOException {
        this.lists("blacklist: [GB]", "enforcement:\n  login-check: false\n");
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("cheating").build(), PunishOptions.defaults()).join();

        Assertions.assertThat(denial(this.login(STEVE, "Steve", BRITISH)))
                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED);
        Assertions.assertThat(this.login(STEVE, "Steve", SWEDISH))
                .as("let in as a banned player")
                .isInstanceOfSatisfying(LoginDecision.Allowed.class, allowed ->
                        Assertions.assertThat(allowed.notice()).isPresent());
    }

    @Test
    void emptyListsLookNothingUp() {
        final LoginDecision decision = this.login(STEVE, "Steve", BRITISH);

        Assertions.assertThat(decision).isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.counting.lookups.get()).isZero();
    }

    /** Counts the lookups made through it. */
    static final class CountingLookup implements CountryLookup {

        final AtomicInteger lookups = new AtomicInteger();
        private final CountryLookup delegate;

        CountingLookup(final CountryLookup delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Country> country(final InetAddress address) {
            this.lookups.incrementAndGet();
            return this.delegate.country(address);
        }

        @Override
        public State state() {
            return this.delegate.state();
        }
    }
}
