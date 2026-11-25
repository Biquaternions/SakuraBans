package net.serlith.sakurabans.core.kind;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.storage.repository.KindRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Kinds in the database, their modifiers and per-punishment strictness (plan S31a). */
class SakuraKindServiceTest extends ServiceTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");

    private Kind create(final KindDefinition definition) {
        return ((AdminResult.Ok<Kind>) this.kinds.create(definition, CONSOLE).join()).value();
    }

    private Punishment punish(final PunishmentType type, final Victim victim,
            final String kind) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type, victim,
                MODERATOR).kind(kind).build(), PunishOptions.builder()
                .override(OverridePolicy.REPLACE).build()).join()).punishment();
    }

    private LoginDecision join(final UUID uuid, final InetAddress address) {
        return this.loginGate.check(new LoginAttempt(uuid, "Player", address, null)).join();
    }

    @Test
    void builtInKindsAreSeededOnceAndListedFirst() {
        final Kind custom = this.create(KindDefinition.builder("chat-ban", PunishmentType.MUTE)
                .displayName("Chat ban").build());

        Assertions.assertThat(this.kinds.kinds()).extracting(Kind::id)
                .startsWith("ban", "blacklist", "competitive-ban").endsWith("chat-ban");
        Assertions.assertThat(this.kinds.kind("BLACKLIST")).hasValueSatisfying(kind -> {
            Assertions.assertThat(kind.builtIn()).isTrue();
            Assertions.assertThat(kind.strictness()).hasValue(AddressStrictness.STRICT);
        });
        Assertions.assertThat(this.kinds.levels()).containsExactly(AddressStrictness.STRICT);

        this.kinds.start().join();
        Assertions.assertThat(this.kinds.kind("chat-ban")).hasValue(custom);
    }

    @Test
    void changesAreVersionedCheckedSyncedAndAnnounced() {
        final Kind created = this.create(KindDefinition.builder("chat-ban", PunishmentType.MUTE)
                .build());

        Assertions.assertThat(this.kinds.create(KindDefinition.builder("chat-ban",
                PunishmentType.MUTE).build(), CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.kinds.update(KindDefinition.builder("chat-ban",
                PunishmentType.BAN).build(), created.version(), CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.kinds.update(KindDefinition.builder(created).shadow(true)
                .build(), 5, CONSOLE).join()).isInstanceOf(AdminResult.Conflict.class);
        Assertions.assertThat(this.kinds.update(KindDefinition.builder(created).shadow(true)
                .build(), created.version(), CONSOLE).join())
                .isInstanceOfSatisfying(AdminResult.Ok.class, ok ->
                        Assertions.assertThat(((Kind) ok.value()).version()).isEqualTo(2));
        Assertions.assertThat(this.kinds.kind("chat-ban").orElseThrow().shadow()).isTrue();

        Assertions.assertThat(this.kinds.delete("ban", 1, CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
        Assertions.assertThat(this.kinds.delete("chat-ban", 2, CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.kinds.kind("chat-ban")).isEmpty();

        Assertions.assertThat(this.events(EntityChangedEvent.KindChanged.class))
                .extracting(EntityChangedEvent::change)
                .containsExactly(ChangeType.CREATED, ChangeType.UPDATED, ChangeType.DELETED);
        Assertions.assertThat(this.published).filteredOn(packet -> packet
                instanceof SyncPacket.EntityChangedPacket changed
                && changed.entity() == SubjectType.KIND).hasSize(3);
    }

    @Test
    void kindsInUseCantBeDeletedAndForbiddenActorsChangeNothing() {
        final Kind custom = this.create(KindDefinition.builder("chat-ban", PunishmentType.MUTE)
                .build());
        this.punish(PunishmentType.MUTE, PlayerVictim.of(STEVE), "chat-ban");

        Assertions.assertThat(this.kinds.delete("chat-ban", custom.version(), CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);

        this.actionCheck = (actor, action) -> CompletableFuture.completedFuture(
                Decision.deny(Decision.Denial.NO_PERMISSION, null));
        Assertions.assertThat(this.kinds.create(KindDefinition.builder("other",
                PunishmentType.BAN).build(), MODERATOR).join())
                .isInstanceOf(AdminResult.Forbidden.class);
    }

    @Test
    void punishmentsKeepTheModifiersTheyCopied() {
        final Kind custom = this.create(KindDefinition.builder("event-ban", PunishmentType.BAN)
                .defaultScope(CategoryScope.of("events")).permanentOnly(true).build());
        final Punishment before = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE),
                "event-ban");

        this.kinds.update(KindDefinition.builder(custom).defaultScope(null).permanentOnly(false)
                .build(), custom.version(), CONSOLE).join();
        final Punishment after = this.punish(PunishmentType.BAN, PlayerVictim.of(ALEX),
                "event-ban");

        Assertions.assertThat(before.scope()).isEqualTo(CategoryScope.of("events"));
        Assertions.assertThat(after.scope()).isNotEqualTo(CategoryScope.of("events"));
        Assertions.assertThat(this.service.byId(before.id()).join().orElseThrow().scope())
                .isEqualTo(CategoryScope.of("events"));
    }

    @Test
    void eachPunishmentReachesWhatItsOwnStrictnessReaches() throws Exception {
        final InetAddress shared = InetAddress.getByName("198.51.100.20");
        final InetAddress elsewhere = InetAddress.getByName("198.51.100.21");
        this.players.recordConnection(STEVE, "Steve", this.now.get(), shared, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), shared, null).join();

        // A normal ban covers only Steve; a blacklist (STRICT) also covers his alt Alex.
        this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE), "ban");
        Assertions.assertThat(this.join(ALEX, elsewhere)).isInstanceOf(LoginDecision.Allowed.class);
        this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE), "blacklist");
        Assertions.assertThat(this.join(ALEX, elsewhere)).isInstanceOf(LoginDecision.Denied.class);

        // A LENIENT address ban reaches only joins from that address, not its past users.
        this.create(KindDefinition.builder("door-ban", PunishmentType.BAN)
                .strictness(AddressStrictness.LENIENT).addressMode(AddressMode.AS_GIVEN).build());
        final InetAddress door = InetAddress.getByName("198.51.100.30");
        this.players.recordConnection(BOB, "Bob", this.now.get(), door, null).join();
        this.punish(PunishmentType.BAN, AddressVictim.of(door), "door-ban");
        Assertions.assertThat(this.join(BOB, elsewhere)).isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.join(BOB, door)).isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void otherInstancesReloadAndAnnounceRemotely() {
        new KindRepository(this.storage)
                .create(KindDefinition.builder("chat-ban", PunishmentType.MUTE).build(),
                        CONSOLE, this.now.get()).join();

        this.kinds.changed(new SyncPacket.EntityChangedPacket(SubjectType.KIND, "chat-ban",
                ChangeType.CREATED, 1, CONSOLE)).join();

        Assertions.assertThat(this.kinds.kind("chat-ban")).isPresent();
        Assertions.assertThat(this.events(EntityChangedEvent.KindChanged.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.remote()).isTrue());
    }
}
