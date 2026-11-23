package net.serlith.sakurabans.core.alts;

import java.net.InetAddress;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.integration.AltSource;
import net.serlith.sakurabans.api.integration.ExternalAlt;
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
    private static final UUID CAROL = UUID.fromString("5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f");

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
        this.seen(CAROL, "Carol", InetAddress.getByName("203.0.113.4"));
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
        Assertions.assertThat(alex.source()).isEqualTo("sakurabans");
        Assertions.assertThat(alex.score()).isEmpty();
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

    @Test
    void externalSourcesAreMergedAndFailingOnesLeftOut() {
        this.integrations.register(new Source("hydra",
                CompletableFuture.completedFuture(List.of(ExternalAlt.of(CAROL, 0.8, List.of())))));
        this.integrations.register(new Source("broken",
                CompletableFuture.failedFuture(new IllegalStateException("down"))));

        final List<AltEntry> alts = this.altService.alts(STEVE, AddressStrictness.NORMAL).join();

        Assertions.assertThat(alts).extracting(alt -> alt.account().name())
                .containsExactly("Alex", "Carol");
        Assertions.assertThat(alts.get(1).source()).isEqualTo("hydra");
        Assertions.assertThat(alts.get(1).score()).hasValue(0.8);
        Assertions.assertThat(this.log.entries("warn"))
                .anySatisfy(entry -> Assertions.assertThat(entry.format()).contains("broken"));
    }

    private record Source(String name, CompletableFuture<List<ExternalAlt>> answer)
            implements AltSource {

        @Override
        public CompletableFuture<List<ExternalAlt>> alts(final UUID uuid, final double minScore) {
            return this.answer;
        }

        @Override
        public CompletableFuture<OptionalDouble> score(final UUID first, final UUID second) {
            return CompletableFuture.completedFuture(OptionalDouble.empty());
        }

        @Override
        public CompletableFuture<List<UUID>> accountsAt(final InetAddress address) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
