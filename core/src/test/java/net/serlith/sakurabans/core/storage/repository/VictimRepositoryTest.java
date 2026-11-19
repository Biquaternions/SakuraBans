package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VictimRepositoryTest extends DatabaseTest {

    private VictimRepository victims;
    private List<Victim> everyKind;

    @BeforeEach
    void create() throws Exception {
        this.victims = new VictimRepository(this.storage);
        final UUID uuid = UUID.randomUUID();
        this.everyKind = List.of(
                PlayerVictim.of(uuid),
                AddressVictim.of(InetAddress.getByName("203.0.113.7")),
                AddressVictim.of(InetAddress.getByName("2001:db8::7")),
                CompositeVictim.of(uuid, InetAddress.getByName("203.0.113.7")),
                AddressRangeVictim.of(InetAddress.getByName("203.0.113.0"), 24),
                AddressRangeVictim.of(InetAddress.getByName("2001:db8::"), 32));
    }

    @Test
    void everyKindOfVictimRoundTrips() {
        for (final Victim victim : this.everyKind) {
            final long id = this.victims.id(victim).join();

            Assertions.assertThat(this.victims.byId(id).join()).as(victim.toString())
                    .contains(victim);
            Assertions.assertThat(this.victims.id(victim).join()).as("same row").isEqualTo(id);
        }
        Assertions.assertThat(this.count(SakuraTables.VICTIMS)).isEqualTo(this.everyKind.size());
    }

    @Test
    void findingNeverCreates() {
        final Victim victim = this.everyKind.getFirst();

        Assertions.assertThat(this.victims.find(victim).join()).isEmpty();
        final long id = this.victims.id(victim).join();
        Assertions.assertThat(this.victims.find(victim).join()).contains(id);
        Assertions.assertThat(this.victims.byId(id + 1000).join()).isEmpty();
    }

    @Test
    void racingCreationsShareOneRow() {
        final Victim victim = PlayerVictim.of(UUID.randomUUID());

        final List<CompletableFuture<Long>> racing = IntStream.range(0, 16)
                .mapToObj(ignored -> this.victims.id(victim)).toList();

        final Set<Long> ids = Set.copyOf(racing.stream().map(CompletableFuture::join).toList());
        Assertions.assertThat(ids).hasSize(1);
        Assertions.assertThat(this.count(SakuraTables.VICTIMS)).isOne();
    }
}
