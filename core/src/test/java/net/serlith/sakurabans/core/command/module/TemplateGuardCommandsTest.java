package net.serlith.sakurabans.core.command.module;

import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The commands say why a guarded template's punishment can't be changed. */
class TemplateGuardCommandsTest extends CommandTest {

    private static final UUID HELPER = UUID.fromString("7d3e5c1a-2b4f-4e6a-8c9d-0e1f2a3b4c5d");

    @Test
    void revokeAndEditNameTheTemplateGuard() {
        this.known(STEVE, "Steve");
        this.templates.create(TemplateBuilder.preset("vip", PunishmentType.BAN).reason("VIP")
                .permissionRequired(true).build(), CONSOLE).join();
        final Punishment ban = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.BAN, PlayerVictim.of(STEVE), CONSOLE).template("vip")
                .build(), PunishOptions.defaults()).join()).punishment();
        final FakeCommandActor helper = FakeCommandActor.player(HELPER, "Helper",
                staffPermissions()).grant(Permissions.flag(PunishmentType.BAN, "override"),
                Permissions.editOwn(PunishmentType.BAN));

        this.run(helper, "unban Steve");
        Assertions.assertThat(helper.last())
                .contains("made from a template that needs its permission");
        this.run(helper, "sb edit " + ban.id() + " reason other");
        Assertions.assertThat(helper.last())
                .contains("made from a template that needs its permission");
        this.run(helper, "ban Steve -o -r replaced");
        Assertions.assertThat(helper.last())
                .contains("made from a template that needs its permission");

        this.run(this.console, "unban Steve");
        Assertions.assertThat(this.punishments.byId(ban.id()).join().orElseThrow()
                .isActiveAt(this.now.get())).as("the console may").isFalse();
    }
}
