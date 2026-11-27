package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every address strictness against one fixture (plan S28). The integration tests run it on
 * MariaDB, MySQL and PostgreSQL.
 *
 * <pre>
 * STEVE  joins from A1, has used A1 and A2
 * ALEX   has used A2 and A3   (shares A2 with STEVE: his alt)
 * BOB    has used A3          (shares A3 with ALEX only: two hops from STEVE)
 * CAROL  has used A4          (unrelated)
 * </pre>
 */
class StrictnessTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");
    private static final UUID CAROL = UUID.fromString("5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private final Policy policy = new Policy();
    private PunishmentRepository punishments;
    private PlayerRepository players;
    private InetAddress a1;
    private InetAddress a2;
    private InetAddress a3;
    private InetAddress a4;
    private Punishment banA1;
    private Punishment banA2;
    private Punishment banA3;
    private Punishment banA4;
    private Punishment banAlex;
    private Punishment banBob;

    private static final class Policy implements AltPolicy {
        private final Set<UUID> accounts = new HashSet<>();
        private final Set<InetAddress> addresses = new HashSet<>();
        private @Nullable Duration expiration;

        @Override
        public Set<UUID> allowedAccounts() {
            return Set.copyOf(this.accounts);
        }

        @Override
        public Set<InetAddress> allowedAddresses() {
            return Set.copyOf(this.addresses);
        }

        @Override
        public Optional<Duration> recordExpiration() {
            return Optional.ofNullable(this.expiration);
        }
    }

    @BeforeEach
    void fixture() throws Exception {
        this.punishments = new PunishmentRepository(this.storage, this.hasher,
                new PunishmentMapper(Long::toString), this.policy);
        this.players = new PlayerRepository(this.storage, this.hasher);
        this.a1 = InetAddress.getByName("203.0.113.1");
        this.a2 = InetAddress.getByName("203.0.113.2");
        this.a3 = InetAddress.getByName("203.0.113.3");
        this.a4 = InetAddress.getByName("203.0.113.4");
        this.seen(STEVE, "Steve", this.a2, this.now.get());
        this.seen(STEVE, "Steve", this.a1, this.now.get());
        this.seen(ALEX, "Alex", this.a2, this.now.get());
        this.seen(ALEX, "Alex", this.a3, this.now.get());
        this.seen(BOB, "Bob", this.a3, this.now.get());
        this.seen(CAROL, "Carol", this.a4, this.now.get());
        this.banA1 = this.ban(AddressVictim.of(this.a1));
        this.banA2 = this.ban(AddressVictim.of(this.a2));
        this.banA3 = this.ban(AddressVictim.of(this.a3));
        this.banA4 = this.ban(AddressVictim.of(this.a4));
        this.banAlex = this.ban(PlayerVictim.of(ALEX));
        this.banBob = this.ban(PlayerVictim.of(BOB));
    }

    private void seen(final UUID uuid, final String name, final InetAddress address,
            final Instant at) {
        this.players.recordConnection(uuid, name, at, address, null).join();
    }

    private Punishment ban(final Victim victim) {
        final NewPunishment punishment = new NewPunishment(PunishmentType.BAN, false,
                victim, GlobalScope.INSTANCE, "reason", CONSOLE, this.now.get(), null, null,
                Visibility.NORMAL, null, null, List.of(), null);
        return ((InsertOutcome.Inserted) this.punishments.insert(punishment,
                OverridePolicy.REJECT).join()).punishment();
    }

    private List<Punishment> steve(final AddressStrictness strictness) {
        return this.punishments.activeApplicable(PunishmentType.BAN,
                Applicability.of(STEVE, this.a1, strictness), List.of(GlobalScope.INSTANCE),
                this.now.get()).join();
    }

    @Test
    void eachLevelReachesOneStepFurther() {
        Assertions.assertThat(this.steve(AddressStrictness.LENIENT))
                .as("the address he joins from").containsExactlyInAnyOrder(this.banA1);
        Assertions.assertThat(this.steve(AddressStrictness.NORMAL))
                .as("every address he used").containsExactlyInAnyOrder(this.banA1, this.banA2);
        Assertions.assertThat(this.steve(AddressStrictness.STERN))
                .as("and the addresses of his alts")
                .containsExactlyInAnyOrder(this.banA1, this.banA2, this.banA3);
        Assertions.assertThat(this.steve(AddressStrictness.STRICT))
                .as("and his alts' player bans, one hop only (not Bob)")
                .containsExactlyInAnyOrder(this.banA1, this.banA2, this.banA3, this.banAlex);
    }

    @Test
    void unrelatedAccountsStayUntouched() {
        Assertions.assertThat(this.punishments.activeApplicable(PunishmentType.BAN,
                        Applicability.of(CAROL, this.a4, AddressStrictness.STRICT),
                        List.of(GlobalScope.INSTANCE), this.now.get()).join())
                .containsExactly(this.banA4);
    }

    @Test
    void anAllowListedAddressReachesAndLinksNobody() {
        this.policy.addresses.add(this.a2);

        Assertions.assertThat(this.steve(AddressStrictness.NORMAL))
                .containsExactlyInAnyOrder(this.banA1);
        Assertions.assertThat(this.steve(AddressStrictness.STRICT))
                .as("A2 no longer links Steve and Alex").containsExactlyInAnyOrder(this.banA1);
    }

    @Test
    void anAllowListedAccountIsReachedOnlyByPunishmentsNamingIt() {
        final Punishment composite = this.ban(CompositeVictim.of(STEVE, this.a4));
        this.policy.accounts.add(STEVE);

        Assertions.assertThat(this.steve(AddressStrictness.STRICT))
                .containsExactlyInAnyOrder(composite);
    }

    @Test
    void allowListedAltsAreNoOnesAlts() {
        this.policy.accounts.add(ALEX);

        Assertions.assertThat(this.steve(AddressStrictness.STRICT))
                .as("Alex links nobody, so neither A3 nor his ban reach Steve")
                .containsExactlyInAnyOrder(this.banA1, this.banA2);
    }

    @Test
    void expiredRecordsDontLink() {
        this.seen(ALEX, "Alex", this.a2, this.now.get().minus(Duration.ofDays(40)));
        this.policy.expiration = Duration.ofDays(30);

        Assertions.assertThat(this.steve(AddressStrictness.STRICT))
                .as("Alex was last on A2 too long ago")
                .containsExactlyInAnyOrder(this.banA1, this.banA2);
    }

    @Test
    void reachesAnswersLikeTheLoginQuery() {
        Assertions.assertThat(this.punishments.reaches(this.banA3.id(),
                Applicability.of(STEVE, this.a1, AddressStrictness.STERN), this.now.get()).join())
                .isTrue();
        Assertions.assertThat(this.punishments.reaches(this.banA3.id(),
                Applicability.of(STEVE, this.a1, AddressStrictness.NORMAL), this.now.get()).join())
                .isFalse();
    }
}
