package net.serlith.sakurabans.core.permission;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
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
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Authorization and exemption (plan S33); there are no guards any more (D32). */
class AuthorizationServiceTest extends ServiceTest {

    private static final UUID ADMIN = UUID.fromString("5a0f1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
    private static final Actor ADMIN_ACTOR = Actor.of(PlayerOperator.of(ADMIN, "Admin"),
            "in-game");

    private final FakeLuckPerms luckPerms = new FakeLuckPerms();
    private FakeOnlinePlayer mod;

    /** What LuckPerms knows about offline accounts. */
    private static class FakeLuckPerms implements OfflinePermissions {

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
        // Online, but without the staff permissions ServiceTest gives the moderator.
        this.mod = this.platform.join(MOD, "Mod", this.home);
    }

    private PunishResult ban(final Actor actor, final UUID victim) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(victim), actor).build(), PunishOptions.defaults()).join();
    }

    private static PunishResult.Failure.Reason reason(final PunishResult result) {
        return ((PunishResult.Failure) result).reason();
    }

    private Decision decide(final Actor actor, final Action action) {
        return this.authorization.check(actor, action).join();
    }

    @Test
    void punishingNeedsTheTypePermission() {
        Assertions.assertThat(reason(this.ban(MODERATOR, STEVE)))
                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.MUTE,
                null)).allowed()).isFalse();
        this.mod.grant(Permissions.punish(PunishmentType.BAN));
        Assertions.assertThat(this.ban(MODERATOR, STEVE))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void offlinePlayersAreAskedOfLuckPermsForEveryAction() {
        // S64 (D38): an actor who isn't online is checked with LuckPerms' answer.
        this.offline = this.luckPerms.grant(ADMIN, Permissions.ADMIN_TEMPLATES,
                Permissions.punish(PunishmentType.BAN), Permissions.PUNISH,
                Permissions.revoke(PunishmentType.BAN));
        Assertions.assertThat(this.templates.create(TemplateBuilder.preset("spam",
                PunishmentType.MUTE).reason("Spam").build(), ADMIN_ACTOR).join())
                .isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Manage(
                Action.Manage.Area.SETTINGS))).isEqualTo(Decision.NO_PERMISSION);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Punish(PunishmentType.BAN,
                null))).isEqualTo(Decision.ALLOWED);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Punish(PunishmentType.MUTE,
                null))).isEqualTo(Decision.NO_PERMISSION);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.ApplyLadder("misconduct",
                false, null))).isEqualTo(Decision.ALLOWED);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.ApplyLadder("misconduct",
                true, null))).as("the template node is missing")
                .isEqualTo(Decision.NO_PERMISSION);

        final Punishment ban = ((PunishResult.Success) this.ban(ADMIN_ACTOR, STEVE))
                .punishment();
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.ChangePunishment(
                Action.ChangePunishment.Operation.REVOKE, ban.id()))).isEqualTo(Decision.ALLOWED);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.ChangePunishment(
                Action.ChangePunishment.Operation.VOID, ban.id())))
                .isEqualTo(Decision.NO_PERMISSION);
    }

    @Test
    void customOperatorWithUuidIsAskedOfLuckPermsToo() {
        final Actor plugin = Actor.of(CustomOperator.of("Panel", ADMIN), "api:panel");
        Assertions.assertThat(this.decide(plugin, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.NO_PERMISSION);
        this.offline = this.luckPerms.grant(ADMIN, Permissions.ADMIN_TEMPLATES);
        Assertions.assertThat(this.decide(plugin, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void offlinePlayersAreDeniedWithoutLuckPermsOrWithoutAnAnswer() {
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.NO_PERMISSION);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Punish(PunishmentType.BAN,
                null))).isEqualTo(Decision.NO_PERMISSION);
        Assertions.assertThat(this.log.entries("warn")).as("warned once")
                .filteredOn(entry -> entry.format().contains("without LuckPerms")).hasSize(1);

        // LuckPerms installed, but it has nothing to say about this account.
        this.offline = new FakeLuckPerms() {
            @Override
            public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.NO_PERMISSION);
    }

    @Test
    void anOnlinePlayerHasTheirLivePermissionsWhateverLuckPermsSays() {
        this.offline = this.luckPerms.grant(ADMIN, Permissions.ADMIN_TEMPLATES);
        this.platform.join(ADMIN, "Admin", this.home);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.NO_PERMISSION);
        this.platform.online(ADMIN).orElseThrow().grant(Permissions.ADMIN_TEMPLATES);
        Assertions.assertThat(this.decide(ADMIN_ACTOR, new Action.Manage(
                Action.Manage.Area.TEMPLATES))).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void theConsoleConsoleSendersAndPluginsAreTrusted() {
        Assertions.assertThat(this.decide(CONSOLE, new Action.Punish(PunishmentType.BAN,
                GlobalScope.INSTANCE)).allowed()).isTrue();
        Assertions.assertThat(this.decide(Actor.of(PlayerOperator.of(ADMIN, "Admin"), "console"),
                new Action.Manage(Action.Manage.Area.TEMPLATES)).allowed()).isTrue();
        Assertions.assertThat(this.decide(Actor.of(CustomOperator.of("Anticheat", null),
                "api:anticheat"), new Action.Punish(PunishmentType.BAN, null)).allowed())
                .isTrue();
    }

    @Test
    void scopesNeedPermissionsWhenConfigured() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                "scopes:\n  require-permissions: true\n");
        this.config.reload();
        this.mod.grant(Permissions.punish(PunishmentType.BAN));

        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.BAN,
                ServerScope.of("lobby"))).allowed()).isFalse();
        this.mod.grant(Permissions.scopeServer("lobby"));
        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.BAN,
                ServerScope.of("lobby"))).allowed()).isTrue();
        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.BAN,
                null)).allowed()).isFalse();
    }

    @Test
    void templateScopesNeedTheirPermission() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                "scopes:\n  require-permissions: true\n");
        this.config.reload();
        this.templates.create(TemplateBuilder.preset("elimination", PunishmentType.BAN)
                .reason("Out of hearts").scope(CategoryScope.of("lifesteal")).build(), CONSOLE)
                .join();
        this.mod.grant(Permissions.punish(PunishmentType.BAN), Permissions.SCOPE_DEFAULT);
        final DraftPunishment.Builder draft = DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(STEVE), MODERATOR).template("elimination");

        Assertions.assertThat(reason(this.service.punish(draft.build(),
                PunishOptions.defaults()).join())).isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
        this.mod.grant(Permissions.scopeCategory("lifesteal"));
        Assertions.assertThat(this.service.punish(draft.build(), PunishOptions.defaults()).join())
                .isInstanceOfSatisfying(PunishResult.Success.class, success ->
                        Assertions.assertThat(success.punishment().scope())
                                .isEqualTo(CategoryScope.of("lifesteal")));
        // A scope named with the punishment wins over the template's.
        Assertions.assertThat(reason(this.service.punish(draft.victim(PlayerVictim.of(ALEX))
                .scope(GlobalScope.INSTANCE).build(), PunishOptions.defaults()).join()))
                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
    }

    @Test
    void revokingOthersNeedsMoreThanRevokingOwn() {
        final Punishment byConsole = ((PunishResult.Success) this.ban(CONSOLE, STEVE))
                .punishment();
        this.mod.grant(Permissions.punish(PunishmentType.BAN),
                Permissions.revokeOwn(PunishmentType.BAN));
        final Punishment own = ((PunishResult.Success) this.ban(MODERATOR, ALEX))
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

        Assertions.assertThat(reason(this.ban(MODERATOR, STEVE)))
                .isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(this.ban(CONSOLE, STEVE))
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

        Assertions.assertThat(reason(this.ban(MODERATOR, ADMIN)))
                .isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(reason(this.ban(MODERATOR, ALEX)))
                .as("same weight").isEqualTo(PunishResult.Failure.Reason.EXEMPT);
        Assertions.assertThat(this.ban(MODERATOR, STEVE))
                .isInstanceOf(PunishResult.Success.class);

        // The admin's ban can't be lifted by the moderator (exemption.protects-edits).
        this.platform.join(ADMIN, "Admin", this.home).grant(Permissions.punish(PunishmentType.BAN));
        final Punishment adminsBan = ((PunishResult.Success) this.ban(ADMIN_ACTOR,
                UUID.randomUUID())).punishment();
        Assertions.assertThat(this.revoke(MODERATOR, adminsBan))
                .isEqualTo(RevokeResult.Failure.Reason.EXEMPT);
    }

    @Test
    void permissionsAloneDecide() {
        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.BAN, null)))
                .isEqualTo(Decision.NO_PERMISSION);
        this.mod.grant(Permissions.punish(PunishmentType.BAN));
        Assertions.assertThat(this.decide(MODERATOR, new Action.Punish(PunishmentType.BAN, null)))
                .isEqualTo(Decision.ALLOWED);
    }
}
