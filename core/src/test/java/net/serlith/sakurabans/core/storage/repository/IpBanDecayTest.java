package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code enforcement.ip-ban-decay} in every applicability query: the login query, the
 * live check, "applicable to" queries and their counts. The integration tests run it on
 * MariaDB, MySQL and PostgreSQL.
 *
 * <pre>
 * STEVE  composite ban (Steve + A1), joins from A1
 * ALEX   has used A1 (Steve's address); joins from A2
 * A3     a plain address ban
 * </pre>
 */
class IpBanDecayTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");
    private static final Duration DECAY = Duration.ofDays(30);

    private @Nullable Duration decay = DECAY;
    private PunishmentRepository punishments;
    private InetAddress a1;
    private InetAddress a2;
    private InetAddress a3;
    private Punishment composite;
    private Punishment address;
    private Punishment compositeMute;

    @BeforeEach
    void fixture() throws Exception {
        final AltPolicy policy = new AltPolicy() {
            @Override
            public Set<UUID> allowedAccounts() {
                return Set.of();
            }

            @Override
            public Set<InetAddress> allowedAddresses() {
                return Set.of();
            }

            @Override
            public Optional<Duration> recordExpiration() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> ipBanDecay() {
                return Optional.ofNullable(IpBanDecayTest.this.decay);
            }
        };
        this.punishments = new PunishmentRepository(this.storage, this.hasher,
                new PunishmentMapper(Long::toString), policy);
        final PlayerRepository players = new PlayerRepository(this.storage, this.hasher);
        this.a1 = InetAddress.getByName("203.0.113.1");
        this.a2 = InetAddress.getByName("203.0.113.2");
        this.a3 = InetAddress.getByName("203.0.113.3");
        players.recordConnection(STEVE, "Steve", this.now.get(), this.a1, null).join();
        players.recordConnection(ALEX, "Alex", this.now.get(), this.a1, null).join();
        this.composite = this.punish(PunishmentType.BAN, CompositeVictim.of(STEVE, this.a1));
        this.address = this.punish(PunishmentType.BAN, AddressVictim.of(this.a3));
        this.compositeMute = this.punish(PunishmentType.MUTE, CompositeVictim.of(STEVE, this.a1));
    }

    private Punishment punish(final PunishmentType type, final Victim victim) {
        final NewPunishment punishment = new NewPunishment(type, false, victim,
                GlobalScope.INSTANCE, "reason", CONSOLE, this.now.get(), null, null,
                Visibility.NORMAL, null, null, List.of(), null);
        return ((InsertOutcome.Inserted) this.punishments.insert(punishment,
                OverridePolicy.REJECT).join()).punishment();
    }

    private List<Punishment> applicable(final PunishmentType type, final @Nullable UUID uuid,
            final InetAddress from) {
        return this.punishments.activeApplicable(type,
                Applicability.of(uuid, from, AddressStrictness.NORMAL),
                List.of(GlobalScope.INSTANCE), this.now.get()).join();
    }

    private void age(final Duration by) {
        this.now.set(this.now.get().plus(by));
    }

    @Test
    void beforeTheDecayTheAddressPartReachesOthers() {
        Assertions.assertThat(this.applicable(PunishmentType.BAN, ALEX, this.a2))
                .as("Alex used A1").containsExactly(this.composite);
        Assertions.assertThat(this.applicable(PunishmentType.BAN, null, this.a1))
                .containsExactly(this.composite);
    }

    @Test
    void afterTheDecayCompositesReachTheirPlayerOnly() {
        this.age(DECAY.plusSeconds(1));

        Assertions.assertThat(this.applicable(PunishmentType.BAN, ALEX, this.a2)).isEmpty();
        Assertions.assertThat(this.applicable(PunishmentType.BAN, null, this.a1)).isEmpty();
        Assertions.assertThat(this.applicable(PunishmentType.BAN, STEVE, this.a2))
                .as("by its player, from anywhere").containsExactly(this.composite);
        Assertions.assertThat(this.applicable(PunishmentType.BAN, null, this.a3))
                .as("a plain address ban never decays").containsExactly(this.address);
        Assertions.assertThat(this.applicable(PunishmentType.MUTE, ALEX, this.a2))
                .as("mutes are not covered").containsExactly(this.compositeMute);
        Assertions.assertThat(this.punishments.reaches(this.composite.id(),
                Applicability.of(ALEX, this.a1, AddressStrictness.NORMAL), this.now.get())
                .join()).as("the live check agrees").isFalse();
        final PunishmentQuery applicableToAlex = PunishmentQuery.builder()
                .types(PunishmentType.BAN)
                .applicableTo(Applicability.of(ALEX, this.a1, AddressStrictness.NORMAL))
                .build();
        Assertions.assertThat(this.punishments.query(applicableToAlex, this.now.get()).join()
                .items()).isEmpty();
        Assertions.assertThat(this.punishments.count(applicableToAlex, this.now.get()).join())
                .isZero();
    }

    @Test
    void theDecayIsReversibleBecauseNothingIsRewritten() {
        this.age(DECAY.plusSeconds(1));
        Assertions.assertThat(this.applicable(PunishmentType.BAN, ALEX, this.a2)).isEmpty();

        this.decay = null;
        Assertions.assertThat(this.applicable(PunishmentType.BAN, ALEX, this.a2))
                .containsExactly(this.composite);
    }
}
