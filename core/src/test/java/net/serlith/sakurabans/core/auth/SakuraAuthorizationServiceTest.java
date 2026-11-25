package net.serlith.sakurabans.core.auth;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.integration.ActionGuard;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.permission.OfflinePermissions;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Authorization, exemption and action guards (plan S33). */
class SakuraAuthorizationServiceTest extends ServiceTest {

    private static final UUID ADMIN = UUID.fromString("5a0f1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
    private static final Actor ADMIN_ACTOR = Actor.of(PlayerOperator.of(ADMIN, "Admin"),
            "in-game");

    private final FakeLuckPerms luckPerms = new FakeLuckPerms();
    private FakeOnlinePlayer mod;

    /** What LuckPerms knows about offline accounts. */
    private static final class FakeLuckPerms implements OfflinePermissions {

        final Map<UUID, Set<String>> nodes = new HashMap<>();
        final Map<UUID, Integer> weights = new HashMap<>();

        FakeLuckPerms grant(final UUID uuid, final String... granted) {
            this.nodes.computeIfAbsent(uuid, ignored -> new HashSet<>()).addAll(Set.of(granted));
            return this;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
            return CompletableFuture.completedFuture(Optional.of(
                    this.nodes.getOrDefault(uuid, Set.of()).contains(node)));
        }

        @Override
        public CompletableFuture<OptionalInt> weight(final UUID uuid) {
            final Integer weight = this.weights.get(uuid);
            return CompletableFuture.completedFuture(weight == null ? OptionalInt.empty()
                    : OptionalInt.of(weight));
        }
    }

    @BeforeEach
    void authorize() {
        this.actionCheck = this.authorization;
        this.mod = this.platform.join(MOD, "Mod", this.home);
    }

    private PunishResult ban(final Actor actor, final UUID victim, final String kind) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(victim), actor).kind(kind).build(), PunishOptions.defaults())
                .join();
    }

    private static PunishResult.Failure.Reason reason(final PunishResult result) {
        return ((PunishResult.Failure) result).reason();
    }

    private Decision decide(final Actor actor, final Action action) {
        return this.authorization.check(actor, action).join();
    }

    @Test
    void punishingNeedsTheTypeAndKindPermissions() {
        Assertions.assertThat(reason(this.ban(MODERATOR, STEVE, "ban")))
                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
        this.mod.grant(Permissions.punish(PunishmentType.BAN));
        Assertions.assertThat(reason(this.ban(MODERATOR, STEVE, "blacklist")))
                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
        Assertions.assertThat(this.ban(MODERATOR, STEVE, "ban"))
                .isInstanceOf(PunishResult.Success.class);
        this.mod.grant(Permissions.kind("blacklist"));
        Assertions.assertThat(this.decide(MODERATOR, Action.Punish.of("blacklist", null))
                .allowed()).isTrue();
    }

    @Test
    void offlineActorsNeedLuckPerms() {
        Assertions.assertThat(this.decide(ADMIN_ACTOR, Action.Punish.of("ban", null)).denial())
                .hasValue(Decision.Denial.UNKNOWN_PERMISSIONS);

        this.offline = this.luckPerms.grant(ADMIN, Permissions.ADMIN_TEMPLATES);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, Action.Punish.of("ban", null)).denial())
                .hasValue(Decision.Denial.NO_PERMISSION);
        Assertions.assertThat(this.templates.create(TemplateDefinition.builder("spam", "mute")
                .reason("Spam").build(), ADMIN_ACTOR).join()).isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.kinds.create(KindDefinition
                .builder("x", PunishmentType.BAN).build(), ADMIN_ACTOR).join())
                .isInstanceOf(AdminResult.Forbidden.class);
    }

    @Test
    void theConsoleConsoleSendersAndPluginsAreTrusted() {
        Assertions.assertThat(this.decide(CONSOLE, Action.Punish.of("blacklist", null))
                .allowed()).isTrue();
        Assertions.assertThat(this.decide(Actor.of(PlayerOperator.of(ADMIN, "Admin"), "console"),
                Action.Manage.of(Action.Manage.Area.KINDS)).allowed()).isTrue();
        Assertions.assertThat(this.decide(Actor.of(CustomOperator.of("Anticheat", null),
                "api:anticheat"), Action.Punish.of("ban", null)).allowed()).isTrue();
    }

    @Test
    void scopesNeedPermissionsWhenConfigured() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                "scopes:\n  require-permissions: true\n");
        this.config.reload();
        this.mod.grant(Permissions.punish(PunishmentType.BAN));

        Assertions.assertThat(this.decide(MODERATOR, Action.Punish.of("ban",
                ServerScope.of("lobby"))).allowed()).isFalse();
        this.mod.grant(Permissions.scopeServer("lobby"));
        Assertions.assertThat(this.decide(MODERATOR, Action.Punish.of("ban",
                ServerScope.of("lobby"))).allowed()).isTrue();
        Assertions.assertThat(this.decide(MODERATOR, Action.Punish.of("ban", null)).allowed())
                .isFalse();
    }

    @Test
    void revokingOthersNeedsMoreThanRevokingOwn() {
        final Punishment byConsole = ((PunishResult.Success) this.ban(CONSOLE, STEVE, "ban"))
                .punishment();
        this.mod.grant(Permissions.punish(PunishmentType.BAN),
                Permissions.revokeOwn(PunishmentType.BAN));
        final Punishment own = ((PunishResult.Success) this.ban(MODERATOR, ALEX, "ban"))
                .punishment();

        Assertions.assertThat(this.revoke(MODERATOR, byConsole))
                .isEqualTo(RevokeResult.Failure.Reason.FORBIDDEN);
        Assertions.assertThat(this.revoke(MODERATOR, own)).isNull();
    }

    private RevokeResult.Failure.Reason revoke(final Actor actor, final Punishment punishment) {
        final RevokeResult result = this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ById.of(punishment.id()), actor).build()).join();
        return result instanceof RevokeResult.Failure failure ? failure.reason() : null;
    }

    @Test
    void exemptPlayersCantBePunishedUnlessBypassed() {
        this.mod.grant(Permissions.punish(PunishmentType.BAN));
        this.platform.join(STEVE, "Steve", this.home).grant(
                Permissions.exempt(PunishmentType.BAN));

        Assertions.assertThat(reason(this.ban(MODERATOR, STEVE, "ban")))
                .isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(this.ban(CONSOLE, STEVE, "ban"))
                .isInstanceOf(PunishResult.Success.class);
        this.mod.grant(Permissions.exemptBypass(PunishmentType.BAN));
        Assertions.assertThat(this.exemption.isExempt(MODERATOR, STEVE, PunishmentType.BAN)
                .join()).isFalse();
    }

    @Test
    void weightsProtectHigherStaffAndTheirPunishments() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                "exemption:\n  provider: LUCKPERMS_WEIGHT\n  permit-same-weight: false\n");
        this.config.reload();
        this.offline = this.luckPerms;
        this.luckPerms.weights.put(MOD, 10);
        this.luckPerms.weights.put(ADMIN, 50);
        this.luckPerms.weights.put(ALEX, 10);
        this.mod.grant(Permissions.punish(PunishmentType.BAN),
                Permissions.revoke(PunishmentType.BAN));

        Assertions.assertThat(reason(this.ban(MODERATOR, ADMIN, "ban")))
                .isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(reason(this.ban(MODERATOR, ALEX, "ban")))
                .as("same weight").isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(this.ban(MODERATOR, STEVE, "ban"))
                .isInstanceOf(PunishResult.Success.class);

        // The admin's ban can't be lifted by the moderator (exemption.protects-edits).
        this.luckPerms.grant(ADMIN, Permissions.punish(PunishmentType.BAN));
        final Punishment adminsBan = ((PunishResult.Success) this.ban(ADMIN_ACTOR,
                UUID.randomUUID(), "ban")).punishment();
        Assertions.assertThat(this.revoke(MODERATOR, adminsBan))
                .isEqualTo(RevokeResult.Failure.Reason.EXEMPT);
    }

    @Test
    void guardsHaveTheLastWordAndFailClosed() {
        this.mod.grant(Permissions.punish(PunishmentType.BAN));
        this.integrations.register(new ActionGuard() {
            @Override
            public String name() {
                return "2fa";
            }

            @Override
            public CompletableFuture<Decision> check(final Actor actor, final Action action) {
                final boolean player = actor.operator() instanceof PlayerOperator;
                return CompletableFuture.completedFuture(player
                        ? Decision.deny(Decision.Denial.NO_PERMISSION, "verify first")
                        : Decision.allow());
            }
        });

        final Decision denied = this.decide(MODERATOR, Action.Punish.of("ban", null));
        Assertions.assertThat(denied.denial()).hasValue(Decision.Denial.GUARD);
        Assertions.assertThat(denied.message()).hasValue("verify first");

        this.integrations.register(new ActionGuard() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public CompletableFuture<Decision> check(final Actor actor, final Action action) {
                throw new IllegalStateException("broken guard");
            }
        });
        Assertions.assertThat(this.decide(CONSOLE, Action.Punish.of("ban", null)).denial())
                .hasValue(Decision.Denial.GUARD);
    }
}
