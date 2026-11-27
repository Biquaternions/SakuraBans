package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.util.Cidrs;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The queries behind alts and ranges (S29, S30); the integration tests run them everywhere. */
class AltQueriesTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private PunishmentRepository punishments;
    private PlayerRepository players;
    private InetAddress shared;

    @BeforeEach
    void fixture() throws Exception {
        this.punishments = new PunishmentRepository(this.storage, this.hasher,
                new PunishmentMapper(Long::toString));
        this.players = new PlayerRepository(this.storage, this.hasher);
        this.shared = InetAddress.getByName("203.0.113.2");
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(),
                InetAddress.getByName("203.0.113.3"), null).join();
    }

    private void punish(final PunishmentType type, final Victim victim) {
        this.punishments.insert(new NewPunishment(type,
                false, victim, GlobalScope.INSTANCE, "reason",
                CONSOLE, this.now.get(), null, null, Visibility.NORMAL, null, null, List.of(),
                null), OverridePolicy.REJECT).join();
    }

    @Test
    void linksListTheSharedAddresses() {
        final List<PlayerRepository.Link> links =
                this.players.links(STEVE, AltPolicy.NONE, this.now.get()).join();

        Assertions.assertThat(links).singleElement().satisfies(link -> {
            Assertions.assertThat(link.account().uuid()).isEqualTo(ALEX);
            Assertions.assertThat(link.shared()).containsExactly(this.shared);
        });
    }

    @Test
    void activeTypesPerAccount() {
        this.punish(PunishmentType.BAN, PlayerVictim.of(ALEX));
        this.punish(PunishmentType.MUTE, PlayerVictim.of(ALEX));

        Assertions.assertThat(this.punishments.activeTypes(List.of(STEVE, ALEX), this.now.get())
                .join()).containsOnlyKeys(ALEX).containsEntry(ALEX,
                        Set.of(PunishmentType.BAN, PunishmentType.MUTE));
    }

    @Test
    void activeRangesAreTheRangeVictimsOnly() {
        this.punish(PunishmentType.BAN, Cidrs.parse("10.0.0.0/8").orElseThrow());
        this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE));

        Assertions.assertThat(this.punishments.activeRanges(this.now.get()).join())
                .singleElement().satisfies(punishment ->
                        Assertions.assertThat(punishment.victim())
                                .isEqualTo(Cidrs.parse("10.0.0.0/8").orElseThrow()));
    }
}
