package net.serlith.sakurabans.core.command;

import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Errors, help and the lifecycle through Cloud on Adventure 5.2 (open question 14.1). */
class SakuraCommandsTest extends CommandTest {

    @Test
    void parserErrorsUseOurCaptionsWithThePrefix() {
        this.run(this.console, "ban not-a-name");

        Assertions.assertThat(this.console.last()).startsWith("SakuraBans ...🖉 ")
                .contains("'not-a-name' is not a player, UUID, address or #id.");
    }

    @Test
    void cloudsOwnErrorsComeFromTheLanguageFileToo() {
        this.run(this.console, "tempban Steve forever");
        Assertions.assertThat(this.console.last()).contains("'forever' is not a duration");

        this.run(this.console, "ban");
        Assertions.assertThat(this.console.last()).contains("Usage: /ban <target> [--silent");

        final FakeCommandActor player = FakeCommandActor.player(MOD, "Mod");
        this.run(player, "ban Steve");
        Assertions.assertThat(player.last()).contains("You don't have permission to do that.");
    }

    @Test
    void flagsTheSenderMayNotUseAreRefused() {
        final FakeCommandActor mod =
                FakeCommandActor.player(MOD, "Mod", Permissions.punish(PunishmentType.BAN));

        this.run(mod, "ban Steve -s -r griefing");

        Assertions.assertThat(mod.last()).contains("You can't use the flag");
    }

    @Test
    void helpRenders() {
        this.run(this.console, "sakurabans help");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("Help (1/", "/alts <target> [strictness]");

        this.console.clear();
        this.run(this.console, "sakurabans help ban");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("/ban <target> [--silent");
    }

    @Test
    void handlersRefuseWorkUntilRunning() {
        this.lifecycle.advanceTo(LifecycleState.STOPPING);

        this.run(this.console, "banlist");

        Assertions.assertThat(this.console.last()).contains("still starting");
    }

    @Test
    void namesAndAliasesComeFromTheConfiguration() {
        Assertions.assertThat(this.manager.rootCommands())
                .contains("sakurabans", "ban", "tempban", "punish", "unban", "history",
                        "lastuuid");

        this.run(this.console, "sb help");
        Assertions.assertThat(this.console.texts()).anyMatch(text -> text.contains("Help (1/"));
    }
}
