package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentRepositoryTest extends DatabaseTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final UUID MOD = UUID.fromString("0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b");
    private static final Actor MODERATOR = Actor.of(PlayerOperator.of(MOD, "Mod"), "in-game");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private PunishmentRepository punishments;
    private PlayerRepository players;
    private InetAddress home;
    private InetAddress old;

    @BeforeEach
    void create() throws Exception {
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(id -> "D" + id));
        this.players = new PlayerRepository(this.storage);
        this.home = InetAddress.getByName("203.0.113.7");
        this.old = InetAddress.getByName("198.51.100.9");
    }

    private NewPunishment punishment(final PunishmentType type, final Victim victim,
            final Scope scope, final @Nullable Duration duration, final Actor actor) {
        final Instant at = this.now.get();
        return new NewPunishment(type, type.name().toLowerCase(Locale.ROOT), false,
                null, victim, scope, "reason", actor, at,
                duration == null ? null : at.plus(duration), "lobby", Visibility.NORMAL,
                null, null, List.of(), null);
    }

    private NewPunishment ban(final Victim victim, final @Nullable Duration duration) {
        return this.punishment(PunishmentType.BAN, victim, GlobalScope.INSTANCE, duration,
                MODERATOR);
    }

    private Punishment inserted(final NewPunishment punishment) {
        final InsertOutcome outcome =
                this.punishments.insert(punishment, OverridePolicy.REJECT).join();
        Assertions.assertThat(outcome).isInstanceOf(InsertOutcome.Inserted.class);
        return ((InsertOutcome.Inserted) outcome).punishment();
    }

    private void advance(final Duration duration) {
        this.now.set(this.now.get().plus(duration));
    }

    @Test
    void everyFieldRoundTrips() {
        final NewPunishment shadowMute = new NewPunishment(PunishmentType.MUTE, "shadow-mute",
                true, AddressStrictness.LENIENT, PlayerVictim.of(STEVE), ServerScope.of("lobby"),
                "spam", MODERATOR, this.now.get(), this.now.get().plus(Duration.ofDays(1)),
                "lobby", Visibility.SILENT, null, null,
                List.of(EvidenceRef.of("hydra:chat-snapshot:42")), "import-1");

        final Punishment stored = this.inserted(shadowMute);

        Assertions.assertThat(this.punishments.byId(stored.id()).join()).contains(stored);
        Assertions.assertThat(stored.displayId()).isEqualTo("D" + stored.id());
        Assertions.assertThat(stored.kind()).isEqualTo("shadow-mute");
        Assertions.assertThat(stored.shadow()).isTrue();
        Assertions.assertThat(stored.strictness()).contains(AddressStrictness.LENIENT);
        Assertions.assertThat(stored.victim()).isEqualTo(PlayerVictim.of(STEVE));
        Assertions.assertThat(stored.operator()).isEqualTo(MODERATOR.operator());
        Assertions.assertThat(stored.source()).isEqualTo("in-game");
        Assertions.assertThat(stored.scope()).isEqualTo(ServerScope.of("lobby"));
        Assertions.assertThat(stored.expiresAt()).contains(this.now.get().plus(Duration.ofDays(1)));
        Assertions.assertThat(stored.originServer()).contains("lobby");
        Assertions.assertThat(stored.visibility()).isEqualTo(Visibility.SILENT);
        Assertions.assertThat(stored.evidence()).singleElement().satisfies(evidence -> {
            Assertions.assertThat(evidence.ref().uri()).isEqualTo("hydra:chat-snapshot:42");
            Assertions.assertThat(evidence.addedBy()).isEqualTo(MODERATOR);
        });
        Assertions.assertThat(stored.end()).isEmpty();
        Assertions.assertThat(stored.version()).isOne();
    }

    @Test
    void onlyOneActiveBanPerVictimAndScope() {
        final Punishment first = this.inserted(this.ban(PlayerVictim.of(STEVE), null));

        final InsertOutcome second = this.punishments.insert(
                this.ban(PlayerVictim.of(STEVE), null), OverridePolicy.REJECT).join();

        Assertions.assertThat(second).isEqualTo(new InsertOutcome.Rejected(first));
        this.inserted(this.punishment(PunishmentType.BAN, PlayerVictim.of(STEVE),
                ServerScope.of("lobby"), null, MODERATOR));
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).as("other scope")
                .isEqualTo(2);
    }

    @Test
    void replaceSupersedesTheActiveBan() {
        final Punishment first = this.inserted(this.ban(PlayerVictim.of(STEVE), null));
        this.advance(Duration.ofMinutes(1));

        final InsertOutcome outcome = this.punishments.insert(
                this.ban(PlayerVictim.of(STEVE), Duration.ofDays(1)), OverridePolicy.REPLACE)
                .join();

        final InsertOutcome.Inserted inserted = (InsertOutcome.Inserted) outcome;
        Assertions.assertThat(inserted.superseded()).isNotNull();
        Assertions.assertThat(inserted.superseded().id()).isEqualTo(first.id());
        Assertions.assertThat(inserted.superseded().end()).hasValueSatisfying(end -> {
            Assertions.assertThat(end.kind()).isEqualTo(EndKind.SUPERSEDED);
            Assertions.assertThat(end.at()).isEqualTo(this.now.get());
        });
        Assertions.assertThat(this.punishments.active(PunishmentType.BAN, PlayerVictim.of(STEVE),
                GlobalScope.INSTANCE, this.now.get()).join())
                .containsExactly(inserted.punishment());
    }

    @Test
    void replaceIfLongerReplacesOnlyWithLongerPunishments() {
        final Victim steve = PlayerVictim.of(STEVE);
        this.inserted(this.ban(steve, Duration.ofDays(2)));

        Assertions.assertThat(this.punishments.insert(this.ban(steve, Duration.ofDays(1)),
                OverridePolicy.REPLACE_IF_LONGER).join())
                .isInstanceOf(InsertOutcome.Rejected.class);
        Assertions.assertThat(this.punishments.insert(this.ban(steve, Duration.ofDays(3)),
                OverridePolicy.REPLACE_IF_LONGER).join())
                .isInstanceOf(InsertOutcome.Inserted.class);
        Assertions.assertThat(this.punishments.insert(this.ban(steve, null),
                OverridePolicy.REPLACE_IF_LONGER).join())
                .as("permanent outlasts").isInstanceOf(InsertOutcome.Inserted.class);
        Assertions.assertThat(this.punishments.insert(this.ban(steve, Duration.ofDays(9)),
                OverridePolicy.REPLACE_IF_LONGER).join())
                .as("nothing outlasts permanent").isInstanceOf(InsertOutcome.Rejected.class);
    }

    @Test
    void warnsStackAndKicksAreNeverActive() {
        for (int i = 0; i < 3; i++) {
            this.inserted(this.punishment(PunishmentType.WARN, PlayerVictim.of(STEVE),
                    GlobalScope.INSTANCE, null, MODERATOR));
        }
        final Punishment kick = this.inserted(this.punishment(PunishmentType.KICK,
                PlayerVictim.of(STEVE), GlobalScope.INSTANCE, null, MODERATOR));

        Assertions.assertThat(this.punishments.active(PunishmentType.WARN, PlayerVictim.of(STEVE),
                GlobalScope.INSTANCE, this.now.get()).join()).hasSize(3);
        Assertions.assertThat(this.punishments.active(PunishmentType.KICK, PlayerVictim.of(STEVE),
                GlobalScope.INSTANCE, this.now.get()).join()).isEmpty();
        Assertions.assertThat(this.punishments.byId(kick.id()).join()).contains(kick);
        Assertions.assertThat(this.punishments.end(kick.id(), EndKind.REVOKED, this.now.get(),
                MODERATOR,
                null).join()).isEmpty();
    }

    @Test
    void expiredRowsAreIgnoredUntilSweptAndThenEndedAsExpired() {
        final Punishment temporary = this.inserted(this.ban(PlayerVictim.of(STEVE),
                Duration.ofMinutes(1)));
        this.advance(Duration.ofMinutes(2));

        Assertions.assertThat(this.punishments.active(PunishmentType.BAN, PlayerVictim.of(STEVE),
                GlobalScope.INSTANCE, this.now.get()).join()).isEmpty();
        Assertions.assertThat(this.punishments.activeApplicable(PunishmentType.BAN,
                Applicability.of(STEVE, null, AddressStrictness.LENIENT),
                List.of(GlobalScope.INSTANCE), this.now.get()).join()).isEmpty();
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).as("still lingering")
                .isOne();

        this.inserted(this.ban(PlayerVictim.of(STEVE), null));

        Assertions.assertThat(this.punishments.byId(temporary.id()).join().orElseThrow().end())
                .hasValueSatisfying(end -> {
                    Assertions.assertThat(end.kind()).isEqualTo(EndKind.EXPIRED);
                    Assertions.assertThat(end.at()).isEqualTo(temporary.expiresAt().orElseThrow());
                });
    }

    @Test
    void endingRemovesFromTheActiveSetOnce() {
        final Punishment ban = this.inserted(this.ban(PlayerVictim.of(STEVE), null));

        final Punishment ended = this.punishments.end(ban.id(), EndKind.REVOKED,
                this.now.get(), CONSOLE, "appeal").join().orElseThrow();

        Assertions.assertThat(ended.end()).hasValueSatisfying(end -> {
            Assertions.assertThat(end.kind()).isEqualTo(EndKind.REVOKED);
            Assertions.assertThat(end.actor()).contains(CONSOLE);
            Assertions.assertThat(end.reason()).contains("appeal");
        });
        Assertions.assertThat(ended.version()).isEqualTo(2);
        Assertions.assertThat(this.punishments.end(ban.id(), EndKind.REVOKED, this.now.get(),
                CONSOLE,
                null).join()).isEmpty();
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isZero();
        Assertions.assertThatThrownBy(() -> this.punishments.end(ban.id(), EndKind.REVOKED,
                this.now.get(), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicabilityFollowsStrictness() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.old, null).join();
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, null).join();
        final Punishment oldAddressBan = this.inserted(this.ban(AddressVictim.of(this.old),
                null));
        final Punishment compositeBan = this.inserted(this.ban(
                CompositeVictim.of(ALEX, this.home), Duration.ofDays(1)));

        final List<Punishment> lenient = this.applicable(STEVE, this.home,
                AddressStrictness.LENIENT);
        final List<Punishment> normal = this.applicable(STEVE, this.home,
                AddressStrictness.NORMAL);

        Assertions.assertThat(lenient).as("the composite names this address")
                .containsExactly(compositeBan);
        Assertions.assertThat(normal).as("permanent first, then the latest expiry")
                .containsExactly(oldAddressBan, compositeBan);
        Assertions.assertThat(this.applicable(ALEX, null, AddressStrictness.LENIENT))
                .as("the composite names this player").containsExactly(compositeBan);
        Assertions.assertThat(this.punishments.activeApplicable(PunishmentType.BAN,
                Applicability.of(STEVE, this.home, AddressStrictness.NORMAL),
                List.of(ServerScope.of("lobby")), this.now.get()).join())
                .as("other scopes don't apply").isEmpty();
    }

    private List<Punishment> applicable(final UUID uuid, final @Nullable InetAddress address,
            final AddressStrictness strictness) {
        return this.punishments.activeApplicable(PunishmentType.BAN,
                Applicability.of(uuid, address, strictness),
                List.of(GlobalScope.INSTANCE, ServerScope.of("survival")), this.now.get())
                .join();
    }

    @Test
    void queriesFilterByEveryCriterion() {
        final Punishment steveBan = this.inserted(this.ban(PlayerVictim.of(STEVE), null));
        this.advance(Duration.ofMinutes(1));
        final Punishment alexMute = this.inserted(this.punishment(PunishmentType.MUTE,
                PlayerVictim.of(ALEX), ServerScope.of("lobby"), null, CONSOLE));
        this.advance(Duration.ofMinutes(1));
        final Punishment hidden = this.inserted(new NewPunishment(PunishmentType.WARN, "warn",
                false, null, PlayerVictim.of(ALEX), GlobalScope.INSTANCE, "", MODERATOR,
                this.now.get(), null, null, Visibility.HIDDEN, null, null, List.of(), null));
        this.punishments.end(steveBan.id(), EndKind.REVOKED, this.now.get(),
                Actor.of(PlayerOperator.of(MOD, "RenamedMod"), "web"), null).join();
        final Punishment voided = this.inserted(this.punishment(PunishmentType.WARN,
                PlayerVictim.of(STEVE), GlobalScope.INSTANCE, null, CONSOLE));
        this.read(dsl -> dsl.update(SakuraTables.PUNISHMENTS)
                .set(SakuraTables.PUNISHMENTS.VOIDED_AT, this.now.get())
                .set(SakuraTables.PUNISHMENTS.VOIDER_KIND,
                        OperatorKind.CONSOLE)
                .set(SakuraTables.PUNISHMENTS.VOIDER_NAME, "Console")
                .set(SakuraTables.PUNISHMENTS.VOIDER_SOURCE, "console")
                .where(SakuraTables.PUNISHMENTS.ID.eq(voided.id())).execute());

        Assertions.assertThat(this.ids(PunishmentQuery.builder())).as("hidden included, voided not")
                .containsExactly(hidden.id(), alexMute.id(), steveBan.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().includeVoided(true))).hasSize(4);
        Assertions.assertThat(this.ids(PunishmentQuery.builder().includeHidden(false)))
                .doesNotContain(hidden.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().types(PunishmentType.MUTE)))
                .containsExactly(alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().kinds(Set.of("ban"))))
                .containsExactly(steveBan.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().victim(PlayerVictim.of(ALEX))))
                .containsExactly(hidden.id(), alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder()
                .operator(PlayerOperator.of(MOD, "AnyName"))))
                .as("by UUID, not name").containsExactly(hidden.id(), steveBan.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder()
                .operator(ConsoleOperator.INSTANCE)))
                .containsExactly(alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder()
                .revokedBy(PlayerOperator.of(MOD, "Mod")))).containsExactly(steveBan.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().scope(ServerScope.of("lobby"))))
                .containsExactly(alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder().activeOnly(true)))
                .containsExactly(hidden.id(), alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder()
                .createdAfter(alexMute.createdAt()).createdBefore(hidden.createdAt())))
                .containsExactly(alexMute.id());
        Assertions.assertThat(this.ids(PunishmentQuery.builder()
                .order(PunishmentQuery.Order.OLDEST_FIRST)))
                .containsExactly(steveBan.id(), alexMute.id(), hidden.id());
        Assertions.assertThat(this.punishments.count(PunishmentQuery.builder().build(),
                this.now.get())
                .join()).isEqualTo(3);
    }

    private List<Long> ids(final PunishmentQuery.Builder query) {
        return this.punishments.query(query.pageSize(PunishmentQuery.MAX_PAGE_SIZE).build(),
                this.now.get()).join().items().stream().map(Punishment::id).toList();
    }

    @Test
    void pagesByNumberAndByCursorInEveryOrder() {
        final List<Long> all = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            all.add(this.inserted(this.punishment(PunishmentType.WARN, PlayerVictim.of(STEVE),
                    GlobalScope.INSTANCE, i % 2 == 0 ? null : Duration.ofHours(i), MODERATOR))
                    .id());
            this.advance(Duration.ofSeconds(1));
        }

        final Page<Punishment> second = this.punishments.query(PunishmentQuery.builder()
                .page(2).pageSize(3).build(), this.now.get()).join();
        Assertions.assertThat(second.total()).isEqualTo(7);
        Assertions.assertThat(second.page()).isEqualTo(2);
        Assertions.assertThat(second.items()).extracting(Punishment::id)
                .containsExactly(all.get(3), all.get(2), all.get(1));

        for (final PunishmentQuery.Order order : PunishmentQuery.Order.values()) {
            final List<Long> walked = new ArrayList<>();
            final List<Integer> pages = new ArrayList<>();
            String cursor = null;
            do {
                final Page<Punishment> page = this.punishments.query(PunishmentQuery.builder()
                        .order(order).pageSize(3).after(cursor).build(), this.now.get()).join();
                page.items().forEach(item -> walked.add(item.id()));
                pages.add(page.page());
                cursor = page.nextCursor().orElse(null);
            } while (cursor != null);

            Assertions.assertThat(walked).as(order.name()).hasSize(7).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(all);
            Assertions.assertThat(pages).as(order.name()).containsExactly(1, 2, 3);
        }
        Assertions.assertThatThrownBy(() -> this.punishments.query(PunishmentQuery.builder()
                .after("bm9wZQ").build(), this.now.get()).join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void racingBansOnOneVictimLeaveOneActive() {
        final Victim victim = PlayerVictim.of(UUID.randomUUID());

        final List<CompletableFuture<InsertOutcome>> racing = IntStream.range(0, 8)
                .mapToObj(ignored -> this.punishments.insert(this.ban(victim, null),
                        OverridePolicy.REJECT)).toList();

        final Set<Class<?>> outcomes = new HashSet<>();
        long inserted = 0;
        for (final CompletableFuture<InsertOutcome> outcome : racing) {
            outcomes.add(outcome.join().getClass());
            if (outcome.join() instanceof InsertOutcome.Inserted) {
                inserted++;
            }
        }
        Assertions.assertThat(inserted).isOne();
        Assertions.assertThat(outcomes).contains(InsertOutcome.Rejected.class);
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isOne();
    }
}
