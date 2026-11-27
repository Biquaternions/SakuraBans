package net.serlith.sakurabans.core.command.module;

import java.util.List;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The quiet visibility level, --server-origin and the kicks that leave no record. */
class QuietAndKickCommandsTest extends CommandTest {

    private FakeOnlinePlayer staff;

    @BeforeEach
    void staffAndSteve() {
        this.known(STEVE, "Steve");
        this.staff = this.platform.join(ALEX, "Alex",
                InetAddresses.parseLiteral("198.51.100.1").orElseThrow())
                .grant(Permissions.notify(PunishmentType.BAN),
                        Permissions.notifySilent(PunishmentType.BAN),
                        Permissions.notify(PunishmentType.KICK),
                        Permissions.notifyRevoke(PunishmentType.BAN));
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().pageSize(100).build()).join()
                .items();
    }

    private Punishment only() {
        return Assertions.assertThat(this.all()).singleElement().actual();
    }

    @Test
    void quietPunishmentsTellNobodyButAreStoredEnforcedAndListed() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.run(this.console, "ban Steve --quiet -r xray");

        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.QUIET);
        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.staff.messages()).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).isEmpty();
        // Listed in the history and shown by /sb info.
        this.run(this.console, "history Steve");
        Assertions.assertThat(String.join("\n", this.console.texts())).contains("xray");
        final String code = this.only().displayId().substring(
                this.only().displayId().indexOf('/') + 1);
        this.run(this.console, "sb info #" + code);
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("Visibility: quiet");
        // Revoked just as quietly, on request.
        this.run(this.console, "unban Steve --quiet");
        Assertions.assertThat(this.staff.messages()).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).isEmpty();
        this.run(this.console, "ban Steve -r again");
        Assertions.assertThat(this.staff.messages()).isNotEmpty();
    }

    @Test
    void hideIsToldToTheConsoleOnlyAndNormalIsToldToStaff() {
        this.run(this.console, "ban Steve --hide -r a");
        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.HIDDEN);
        Assertions.assertThat(this.staff.messages()).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).hasSize(1);
        this.run(this.console, "unban Steve --hide");
        this.platform.consoleMessages();
        this.run(this.console, "ban Steve -r b");
        Assertions.assertThat(this.staff.messages()).hasSize(1);
    }

    @Test
    void capitalLetterFlagIsCloudsSilentFlagNotHide() {
        // There is no -S flag: Cloud matches flag aliases without regard to case, so -S is -s.
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.BAN),
                Permissions.flag(PunishmentType.BAN, "silent"));

        this.run(mod, "ban Steve -S -r a");
        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.SILENT);
        Assertions.assertThat(this.only().reason()).isEqualTo("a");
    }

    @Test
    void theQuietFlagNeedsItsPermissionPerType() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.BAN), Permissions.punish(PunishmentType.MUTE),
                Permissions.flag(PunishmentType.MUTE, "quiet"));

        this.run(mod, "ban Steve --quiet -r spam");
        Assertions.assertThat(mod.last()).contains("You can't use the flag");
        this.run(mod, "mute Steve --quiet -r spam");

        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.QUIET);
        Assertions.assertThat(this.only().type()).isEqualTo(PunishmentType.MUTE);
    }

    @Test
    void ladderStepsCanBeQuietToo() {
        this.templates.create(TemplateBuilder.ladder("toxicity").reason("Toxic")
                .steps(List.of(TemplateStep.of(PunishmentType.BAN, TemplateDuration.permanent())))
                .build(), CONSOLE).join();

        this.run(this.console, "punish Steve toxicity --quiet");

        Assertions.assertThat(this.only().visibility()).isEqualTo(Visibility.QUIET);
        Assertions.assertThat(this.staff.messages()).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).isEmpty();
    }

    @Test
    void anOriginServerOverridesTheStoredOrigin() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.BAN));

        this.run(mod, "ban Steve --server-origin Hub -r a");
        Assertions.assertThat(mod.last()).contains("You can't use the flag");
        this.run(this.console, "ban Steve --server-origin Hub -r a");

        Assertions.assertThat(this.only().originServer()).contains("hub");
        this.run(this.console, "unban Steve");
        this.run(this.console, "ban Steve -r b");
        Assertions.assertThat(this.all()).extracting(p -> p.originServer().orElse(""))
                .contains("lobby");
    }

    @Test
    void theApiOptionOriginServerIsValidated() {
        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).reason("x").build(),
                PunishOptions.builder().originServer("Survival-2").build()).join();

        Assertions.assertThat(((PunishResult.Success) result).punishment().originServer())
                .contains("survival-2");
        Assertions.assertThatThrownBy(() -> PunishOptions.builder().originServer("no good!")
                .build()).hasMessageContaining("originServer");
    }

    @Test
    void noRecordKicksWithoutStoringOrTellingAnyone() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.run(this.console, "kick Steve --no-record -r Server shutting down");

        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(plain(steve.disconnectReason().orElseThrow()))
                .contains("Username: Steve", "Reason: Server shutting down");
        Assertions.assertThat(this.all()).isEmpty();
        Assertions.assertThat(this.console.last()).contains("Kicked Steve without recording it");
        Assertions.assertThat(this.staff.messages()).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).isEmpty();
        Assertions.assertThat(this.published).anySatisfy(packet ->
                Assertions.assertThat(packet).isEqualTo(new SyncPacket.PlayerKickedPacket(STEVE,
                        "Server shutting down")));
    }

    @Test
    void noRecordNeedsItsOwnPermissionAndOnlyExistsOnKicks() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.KICK), Permissions.punish(PunishmentType.BAN));

        this.run(mod, "kick Steve --no-record -r x");
        Assertions.assertThat(mod.last()).contains("You can't use the flag");
        this.run(mod, "ban Steve --no-record -r x");
        Assertions.assertThat(mod.last()).contains("Unknown flag");
        mod.grant("sakurabans.kick.norecord");
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.run(mod, "kick Steve --no-record -r x");

        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.all()).isEmpty();
    }

    @Test
    void ignoredReasonsMatchBySubstringAnyCaseAndLeaveNoRecord() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.run(this.console, "kick Steve -r Server RESTARTING in 5 minutes");

        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.all()).isEmpty();
        Assertions.assertThat(this.staff.messages()).isEmpty();

        final FakeOnlinePlayer again = this.platform.join(STEVE, "Steve", this.home);
        this.run(this.console, "kick Steve -r being rude");

        Assertions.assertThat(again.disconnectReason()).isPresent();
        Assertions.assertThat(this.only().type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(this.staff.messages()).hasSize(1);
    }

    @Test
    void anUnrecordedKickRefusesWhatItCouldNotKeep() {
        this.run(this.console, "kick Steve --no-record --server lobby2 -r x");
        Assertions.assertThat(this.console.last()).contains("can't take a scope");
        this.run(this.console, "kick 203.0.113.9 --no-record -r x");
        Assertions.assertThat(this.console.last()).contains("needs a player name or UUID");
        Assertions.assertThat(this.all()).isEmpty();
    }

    @Test
    void theApiAlwaysRecordsKicksWhateverTheirReason() {
        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.KICK, PlayerVictim.of(STEVE), MODERATOR).reason("afk").build(),
                PunishOptions.defaults()).join();

        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(this.only().reason()).isEqualTo("afk");
    }
}
