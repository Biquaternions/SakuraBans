package net.serlith.sakurabans.core.alts;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** STEVE shares A2 with ALEX, ALEX shares A3 with BOB (the S28 fixture, smaller). */
class AltServiceTest extends ServiceTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");

    private InetAddress a2;

    @BeforeEach
    void fixture() throws Exception {
        this.a2 = InetAddress.getByName("203.0.113.2");
        final InetAddress a3 = InetAddress.getByName("203.0.113.3");
        this.seen(STEVE, "Steve", InetAddress.getByName("203.0.113.1"));
        this.seen(STEVE, "Steve", this.a2);
        this.seen(ALEX, "Alex", this.a2);
        this.seen(ALEX, "Alex", a3);
        this.seen(BOB, "Bob", a3);
    }

    private void seen(final UUID uuid, final String name, final InetAddress address) {
        this.players.recordConnection(uuid, name, this.now.get(), address, null).join();
    }

    private List<String> names(final AddressStrictness strictness) {
        return this.altService.alts(STEVE, strictness).join().stream()
                .map(alt -> alt.account().name()).toList();
    }

    @Test
    void eachLevelFindsOneHopMore() {
        Assertions.assertThat(this.names(AddressStrictness.LENIENT)).isEmpty();
        Assertions.assertThat(this.names(AddressStrictness.NORMAL)).containsExactly("Alex");
        Assertions.assertThat(this.names(AddressStrictness.STERN))
                .containsExactlyInAnyOrder("Alex", "Bob");
        Assertions.assertThat(this.names(AddressStrictness.STRICT))
                .containsExactlyInAnyOrder("Alex", "Bob");
    }

    @Test
    void entriesShowTheSharedAddressesAndActivePunishments() {
        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(ALEX),
                MODERATOR).build(), PunishOptions.defaults()).join();

        final AltEntry alex = this.altService.alts(STEVE, AddressStrictness.NORMAL).join()
                .getFirst();
        Assertions.assertThat(alex.sharedAddresses()).containsExactly(this.a2);
        Assertions.assertThat(alex.activePunishments()).containsExactly(PunishmentType.MUTE);
        Assertions.assertThat(AltService.anyPunished(List.of(alex))).isTrue();
    }

    @Test
    void allowListedAccountsAndAddressesLinkNobody() {
        this.allowList.allow(this.a2, MODERATOR, null).join();
        Assertions.assertThat(this.names(AddressStrictness.STERN)).isEmpty();

        this.allowList.disallow(this.a2, MODERATOR).join();
        this.allowList.allow(ALEX, MODERATOR, null).join();
        Assertions.assertThat(this.names(AddressStrictness.STERN)).isEmpty();
    }
}
