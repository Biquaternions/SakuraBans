package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.event.PunishmentsRolledBackEvent;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** /lockdown, /staffrollback, /prunehistory and /sb status|reload (plan S36). */
class AdminCommandsTest extends CommandTest {

    private Punishment punish(final PunishmentType type, final UUID victim) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type,
                PlayerVictim.of(victim), MODERATOR).duration(Duration.ofDays(1)).build(),
                PunishOptions.defaults()).join()).punishment();
    }

    private LoginDecision join(final UUID uuid, final String name) {
        return this.loginGate.check(new LoginAttempt(uuid, name, this.home, null)).join();
    }

    @Test
    void lockdownsKeepNewPlayersOutUnlessTheyMayBypass() {
        this.run(this.console, "lockdown -r maintenance");
        Assertions.assertThat(this.console.last()).contains("Locked down", "global");
        Assertions.assertThat(this.published).contains(new SyncPacket.LockdownChangedPacket());

        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOfSatisfying(
                LoginDecision.Denied.class, denied -> {
                    Assertions.assertThat(denied.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.LOGIN_LOCKDOWN);
                    Assertions.assertThat(plain(denied.screen())).contains("maintenance");
                });
        // Allow-listed accounts pass, and so does anyone with the bypass (here online).
        this.allowList.allow(ALEX, CONSOLE, null).join();
        Assertions.assertThat(this.join(ALEX, "Alex")).isInstanceOf(LoginDecision.Allowed.class);
        this.platform.join(MOD, "Mod", this.home).grant(Permissions.LOCKDOWN_BYPASS);
        Assertions.assertThat(this.join(MOD, "Mod")).isInstanceOf(LoginDecision.Allowed.class);

        this.run(this.console, "lockdown status");
        Assertions.assertThat(String.join("\n", this.console.texts())).contains("Lockdowns (1)",
                "maintenance");
        this.run(this.console, "lockdown end");
        Assertions.assertThat(this.console.last()).contains("Ended the lockdown");
        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(
                LoginDecision.Allowed.class);
        this.run(this.console, "lockdown end");
        Assertions.assertThat(this.console.last()).contains("isn't locked down");
    }

    @Test
    void lockdownsOfOtherServersAndEndedOnesLetPlayersIn() {
        this.run(this.console, "lockdown --server survival");
        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(
                LoginDecision.Allowed.class);
        this.run(this.console, "lockdown --server lobby -d 1h");
        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(
                LoginDecision.Denied.class);
        this.now.set(this.now.get().plus(Duration.ofHours(2)));
        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(
                LoginDecision.Allowed.class);
        Assertions.assertThat(this.lockdowns.reload().thenApply(ignored ->
                this.lockdowns.lockdowns()).join()).extracting(Lockdown::scope)
                .containsExactly(ServerScope.of("survival"));
    }

    @Test
    void staffRollbacksLiftWhatStaffIssuedSinceThen() {
        this.known(MOD, "Mod");
        final Punishment old = this.punish(PunishmentType.BAN, ALEX);
        this.now.set(this.now.get().plus(Duration.ofHours(3)));
        final Punishment ban = this.punish(PunishmentType.BAN, STEVE);
        final Punishment mute = this.punish(PunishmentType.MUTE, STEVE);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(mute.id()),
                MODERATOR).build()).join();

        this.run(this.console, "staffrollback Mod 1h");
        Assertions.assertThat(this.console.last()).contains("lifts 1 active");
        Assertions.assertThat(this.service.byId(ban.id()).join().orElseThrow().end()).isEmpty();
        this.run(this.console, "staffrollback Mod 1h --confirm -r compromised account");
        Assertions.assertThat(this.console.last()).contains("Lifted 1");
        Assertions.assertThat(this.service.byId(ban.id()).join().orElseThrow().end())
                .hasValueSatisfying(end -> {
                    Assertions.assertThat(end.kind()).isEqualTo(EndKind.ROLLED_BACK);
                    Assertions.assertThat(end.reason()).contains("compromised account");
                });
        Assertions.assertThat(this.service.byId(old.id()).join().orElseThrow().end()).isEmpty();
        Assertions.assertThat(this.events(PunishmentsRolledBackEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.punishments())
                        .extracting(Punishment::id).containsExactly(ban.id()));
        Assertions.assertThat(this.published).filteredOn(
                SyncPacket.PunishmentsEndedPacket.class::isInstance).hasSize(1);

        this.run(this.console, "staffrollback Mod 1h");
        Assertions.assertThat(this.console.last()).contains("issued nothing");
    }

    @Test
    void historiesArePrunedOfEndedPunishmentsOnly() {
        this.known(STEVE, "Steve");
        final Punishment active = this.punish(PunishmentType.BAN, STEVE);
        final Punishment ended = this.punish(PunishmentType.MUTE, STEVE);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ended.id()),
                MODERATOR).build()).join();

        this.run(this.console, "prunehistory Steve");
        Assertions.assertThat(this.console.last()).contains("deletes 1 ended");
        this.run(this.console, "prunehistory Steve --confirm");
        Assertions.assertThat(this.console.last()).contains("Deleted 1 of 1");
        Assertions.assertThat(this.service.query(PunishmentQuery.builder().build()).join()
                .items()).extracting(Punishment::id).containsExactly(active.id());
        this.run(this.console, "prunehistory Steve");
        Assertions.assertThat(this.console.last()).contains("no ended punishments");
    }

    @Test
    void statusAndReload() {
        this.punish(PunishmentType.BAN, STEVE);
        this.registry.start().join();
        this.run(this.console, "sb status");
        final List<String> texts = this.console.texts();
        Assertions.assertThat(texts.get(texts.size() - 3)).contains("H2",
                "Active punishments: 1", "lobby");
        Assertions.assertThat(texts.subList(texts.size() - 2, texts.size())).containsExactly(
                "SakuraBans » Servers on this database: 1",
                "SakuraBans » - lobby (paper, " + BuildConstants.VERSION + "), last seen "
                        + new Formats(this.messages, this.config).instant(this.now.get(),
                                Locale.US));

        this.run(this.console, "sb reload");
        Assertions.assertThat(this.console.last()).contains("Reloaded");
        final FakeCommandActor player = FakeCommandActor.player(ALEX, "Alex");
        this.run(player, "sb reload");
        Assertions.assertThat(player.texts()).noneMatch(text -> text.contains("Reloaded"));
    }
}
