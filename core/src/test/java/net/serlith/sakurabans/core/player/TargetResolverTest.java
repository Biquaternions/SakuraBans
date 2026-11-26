package net.serlith.sakurabans.core.player;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.player.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TargetResolverTest extends DatabaseTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID NOTCH = UUID.randomUUID();

    private final FakePlatform platform = new FakePlatform();
    private final NameCache names = new NameCache();
    private PlayerRepository players;
    private TargetResolver resolver;
    private InetAddress home;

    @BeforeEach
    void create() throws Exception {
        this.home = InetAddress.getByName("203.0.113.7");
        this.players = new PlayerRepository(this.storage);
        this.platform.profiles(name -> CompletableFuture.completedFuture(
                name.equalsIgnoreCase("Notch") ? Optional.of(NOTCH) : Optional.empty()));
        this.resolver = new TargetResolver(this.platform, this.names, this.players,
                new UuidResolver(new SakuraIntegrationRegistry(), this.platform, this.config,
                        this.log.logger()));
    }

    private ResolvedTarget resolve(final TargetInput input) {
        return this.resolver.resolve(input).join();
    }

    @Test
    void onlinePlayersComeFirst() {
        this.platform.join(STEVE, "Steve", this.home);

        Assertions.assertThat(this.resolve(new TargetInput.NameInput("steve")))
                .isEqualTo(new ResolvedTarget.Player(STEVE, "Steve", this.home));
        Assertions.assertThat(this.names.uuid("steve")).contains(STEVE);
    }

    @Test
    void knownPlayersComeFromTheDatabaseWithTheirLastAddress() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, null).join();

        Assertions.assertThat(this.resolve(new TargetInput.NameInput("STEVE")))
                .isEqualTo(new ResolvedTarget.Player(STEVE, "Steve", this.home));
        Assertions.assertThat(this.resolve(new TargetInput.UuidInput(STEVE)))
                .isEqualTo(new ResolvedTarget.Player(STEVE, "Steve", this.home));
    }

    @Test
    void unknownNamesAskThePlatform() {
        Assertions.assertThat(this.resolve(new TargetInput.NameInput("Notch")))
                .isEqualTo(new ResolvedTarget.Player(NOTCH, "Notch", null));
        Assertions.assertThat(this.resolve(new TargetInput.NameInput("Nobody")))
                .isEqualTo(new ResolvedTarget.Unknown(new TargetInput.NameInput("Nobody")));
    }

    @Test
    void unseenUuidsResolveWithoutName() {
        final UUID stranger = UUID.randomUUID();

        Assertions.assertThat(this.resolve(new TargetInput.UuidInput(stranger)))
                .isEqualTo(new ResolvedTarget.Player(stranger, null, null));
    }

    @Test
    void addressesRangesAndIdsPassThrough() {
        final AddressRangeVictim range = AddressRangeVictim.of(this.home, 24);

        Assertions.assertThat(this.resolve(new TargetInput.AddressInput(this.home)))
                .isEqualTo(new ResolvedTarget.Address(this.home));
        Assertions.assertThat(this.resolve(new TargetInput.RangeInput(range)))
                .isEqualTo(new ResolvedTarget.Range(range));
        Assertions.assertThat(this.resolve(new TargetInput.IdInput(7)))
                .isEqualTo(new ResolvedTarget.PunishmentId(7));
    }
}
