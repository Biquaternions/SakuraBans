package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.geoip.Country;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S72b: with GeoIP off on this server the lists, even when set (they are global), change
 * nothing, and the lookup is never asked: not by the login check, not for {@code <country>}.
 */
class GeoIpOffLoginTest extends ServiceTest {

    private final AtomicInteger lookups = new AtomicInteger();

    @Override
    protected String configYaml() {
        return "geoip:\n  blacklist: [GB]\n  allow-unknown: false\n"
                + "alts:\n  join-notification:\n    threshold: 1\n    only-if-punished: false\n";
    }

    @BeforeEach
    void offLookup() {
        this.countries = new CountryLookup() {
            @Override
            public Optional<Country> country(final InetAddress address) {
                GeoIpOffLoginTest.this.lookups.incrementAndGet();
                return Optional.of(new Country("GB", "United Kingdom"));
            }

            @Override
            public State state() {
                return State.DISABLED;
            }
        };
    }

    @Test
    void theLoginPathIsUnchangedAndNothingIsLookedUp() {
        final FakeOnlinePlayer staff = this.platform.join(ALEX, "Alex",
                InetAddress.ofLiteral("198.51.100.9")).grant(Permissions.NOTIFY_ATTEMPT_JOIN,
                        Permissions.ALTS_NOTIFY);
        // An alt of Steve's, so the alt join notice (which takes <country>) is sent too.
        this.players.recordConnection(MOD, "Mod", this.now.get(), this.home, null).join();

        Assertions.assertThat(this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home,
                null)).join()).isInstanceOf(LoginDecision.Allowed.class);
        eventually(() -> staff.messages().stream()
                .anyMatch(message -> plain(message).contains("known alts")));

        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("cheating").build(), PunishOptions.defaults()).join();
        Assertions.assertThat(this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home,
                null)).join()).isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                Assertions.assertThat(denied.denial())
                        .isEqualTo(PlayerDenyEvent.Denial.LOGIN_BANNED));
        Assertions.assertThat(plain(staff.messages().getLast()))
                .isEqualTo("SakuraBans ...🖉 Steve tried to join but is banned (permanent, by Mod):"
                        + " cheating");

        Assertions.assertThat(this.lookups.get()).isZero();
    }
}
