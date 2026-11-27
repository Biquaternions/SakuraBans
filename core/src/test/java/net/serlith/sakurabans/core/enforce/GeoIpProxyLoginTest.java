package net.serlith.sakurabans.core.enforce;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.geoip.GeoIpService;
import net.serlith.sakurabans.core.geoip.GeoIpTestFiles;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * On a proxy, GeoIP is checked at login to the proxy, also for a banned player that
 * {@code servers-without-login-check} lets in, and never when switching servers.
 */
class GeoIpProxyLoginTest extends ServiceTest {

    private GeoIpService geoip;
    private GeoIpLoginTest.CountingLookup counting;

    GeoIpProxyLoginTest() {
        super(new FakePlatform(Set.of("lobby", "limbo", "survival"), List.of("lobby")));
    }

    @Override
    protected boolean proxy() {
        return true;
    }

    @Override
    protected String configYaml() {
        return "geoip:\n  enabled: true\n  blacklist: [GB]\n"
                + "enforcement:\n  servers-without-login-check: [limbo]\n"
                + "  enforce-server-switch: true\n";
    }

    @BeforeEach
    void openGeoIp() {
        this.geoip = GeoIpTestFiles.ready(this.dir, this.config, this.executors, this.clock,
                this.log.logger());
        this.counting = new GeoIpLoginTest.CountingLookup(this.geoip);
        this.countries = this.counting;
    }

    @AfterEach
    void stopGeoIp() {
        this.geoip.stop();
    }

    private LoginAttempt steve() {
        return new LoginAttempt(STEVE, "Steve", GeoIpLoginTest.BRITISH, null);
    }

    @Test
    void theProxyLoginChecksCountriesAndServerSwitchesDont() {
        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED));
        final int lookups = this.counting.lookups.get();

        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival", true).join())
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.counting.lookups.get()).isEqualTo(lookups);
    }

    @Test
    void bannedPlayersLetInByListedServersAreStillCheckedForTheirCountry() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("griefing").build(), PunishOptions.defaults()).join();

        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_GEOIP_BLOCKED));
        Assertions.assertThat(this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                        GeoIpLoginTest.SWEDISH, null)).join())
                .isInstanceOfSatisfying(LoginDecision.Allowed.class, allowed ->
                        Assertions.assertThat(allowed.notice()).isPresent());
    }
}
