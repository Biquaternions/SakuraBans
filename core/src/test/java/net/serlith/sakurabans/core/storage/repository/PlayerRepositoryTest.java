package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerRepositoryTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Actor ACTOR = Actor.of(ConsoleOperator.INSTANCE, "console");

    private PlayerRepository players;
    private InetAddress home;
    private InetAddress work;

    @BeforeEach
    void create() throws Exception {
        this.players = new PlayerRepository(this.storage, this.hasher);
        this.home = InetAddress.getByName("203.0.113.7");
        this.work = InetAddress.getByName("2001:db8::7");
    }

    @Test
    void connectionsRecordTheAccountItsNameAndItsAddress() {
        final Instant at = this.now.get();

        this.players.recordConnection(STEVE, "Steve", at, this.home, "lobby").join();

        final PlayerProfileView profile = this.players.profile(STEVE).join().orElseThrow();
        Assertions.assertThat(profile.name()).isEqualTo("Steve");
        Assertions.assertThat(profile.firstSeen()).isEqualTo(at);
        Assertions.assertThat(profile.lastAddress()).contains(this.home);
        final List<AddressRecord> addresses = this.players.addresses(STEVE).join();
        Assertions.assertThat(addresses).singleElement().satisfies(address -> {
            Assertions.assertThat(address.address()).isEqualTo(this.home);
            Assertions.assertThat(address.lastServer()).contains("lobby");
        });
    }

    @Test
    void laterConnectionsKeepFirstSeenAndBuildTheHistory() {
        final Instant first = this.now.get();
        final Instant later = first.plus(Duration.ofDays(3));
        this.players.recordConnection(STEVE, "Steve", first, this.home, "lobby").join();

        this.players.recordConnection(STEVE, "Steve2", later, this.work, "survival").join();

        final PlayerProfileView profile = this.players.profile(STEVE).join().orElseThrow();
        Assertions.assertThat(profile.firstSeen()).isEqualTo(first);
        Assertions.assertThat(profile.lastSeen()).isEqualTo(later);
        Assertions.assertThat(profile.name()).isEqualTo("Steve2");
        Assertions.assertThat(profile.lastAddress()).contains(this.work);
        final List<NameRecord> names = this.players.names(STEVE).join();
        Assertions.assertThat(names).extracting(NameRecord::name).containsExactly("Steve2",
                "Steve");
        Assertions.assertThat(this.players.addresses(STEVE).join())
                .extracting(AddressRecord::address)
                .containsExactly(this.work, this.home);
        Assertions.assertThat(this.players.lastUuid("steve").join()).contains(STEVE);
        Assertions.assertThat(this.players.profile("STEVE2").join()).map(PlayerProfileView::uuid)
                .contains(STEVE);
        Assertions.assertThat(this.players.profile("Steve").join()).as("no longer its name")
                .isEmpty();
    }

    @Test
    void connectionsWithoutAnAddressKeepTheKnownOne() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, "lobby").join();

        this.players.recordConnection(STEVE, "Steve", this.now.get().plusSeconds(60), null, null)
                .join();

        Assertions.assertThat(this.players.profile(STEVE).join().orElseThrow().lastAddress())
                .contains(this.home);
        Assertions.assertThat(this.players.addresses(STEVE).join()).hasSize(1);
    }

    @Test
    void sharedAddressesListEveryAccount() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get().plusSeconds(1), this.home,
                null).join();

        Assertions.assertThat(this.players.accountsAt(this.home).join())
                .extracting(PlayerProfileView::uuid)
                .containsExactly(ALEX, STEVE);
        Assertions.assertThat(this.players.accountsAt(this.work).join()).isEmpty();
    }

    @Test
    void forgettingAnAddressRemovesItEverywhere() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, null).join();

        Assertions.assertThat(this.players.forget(STEVE, this.home, ACTOR, this.now.get()).join())
                .isOne();
        Assertions.assertThat(this.players.forget(STEVE, this.home, ACTOR, this.now.get()).join())
                .isZero();
        Assertions.assertThat(this.players.addresses(STEVE).join()).isEmpty();
        final Optional<InetAddress> last =
                this.players.profile(STEVE).join().orElseThrow().lastAddress();
        Assertions.assertThat(last).isEmpty();
    }

    @Test
    void unknownPlayersAreEmpty() {
        Assertions.assertThat(this.players.profile(STEVE).join()).isEmpty();
        Assertions.assertThat(this.players.profile("nobody").join()).isEmpty();
        Assertions.assertThat(this.players.lastUuid("nobody").join()).isEmpty();
        Assertions.assertThat(this.players.names(STEVE).join()).isEmpty();
    }
}
