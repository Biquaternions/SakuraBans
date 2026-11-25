package net.serlith.sakurabans.core.command.module;

import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeCommandsTest extends CommandTest {

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
    }

    private long active(final PunishmentType type) {
        return this.service.count(PunishmentQuery.builder().types(type).activeOnly(true).build())
                .join();
    }

    @Test
    void unbanEndsPlayerAndAddressBansOfTheAccount() {
        this.run(this.console, "ban Steve -r one");
        this.run(this.console, "ipban Steve --server survival -r two");

        this.run(this.console, "unban Steve -r appealed");

        Assertions.assertThat(this.active(PunishmentType.BAN)).isZero();
        Assertions.assertThat(this.console.last()).contains("Unbanned Steve (2)");
    }

    @Test
    void pardonIsUnbanUnderVanillasName() {
        this.run(this.console, "ban Steve -r one");

        this.run(this.console, "pardon Steve -r appealed");

        Assertions.assertThat(this.active(PunishmentType.BAN)).isZero();
        Assertions.assertThat(this.console.last()).contains("Unbanned Steve (1)");
    }

    @Test
    void unwarnRemovesOnlyTheNewestWarning() {
        this.run(this.console, "warn Steve -r one");
        this.run(this.console, "warn Steve -r two");

        this.run(this.console, "unwarn Steve");

        Assertions.assertThat(this.active(PunishmentType.WARN)).isOne();
        Assertions.assertThat(this.service.query(PunishmentQuery.builder()
                        .types(PunishmentType.WARN).activeOnly(true).build())
                .join().items().getFirst().reason()).isEqualTo("one");
    }

    @Test
    void byIdChecksTheType() {
        this.run(this.console, "mute Steve -r spam");
        final Punishment mute = this.service.query(PunishmentQuery.builder().build()).join()
                .items().getFirst();

        this.run(this.console, "unban #" + mute.id());
        Assertions.assertThat(this.console.last()).contains("is a mute");
        this.run(this.console, "unmute #" + mute.id() + " -r sorry");

        final Punishment ended = this.service.byId(mute.id()).join().orElseThrow();
        Assertions.assertThat(ended.end().orElseThrow().kind()).isEqualTo(EndKind.REVOKED);
        Assertions.assertThat(ended.end().orElseThrow().reason()).contains("sorry");
    }

    @Test
    void theOwnPermissionOnlyLiftsOwnPunishments() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.BAN), Permissions.revokeOwn(PunishmentType.BAN));
        this.run(this.console, "ban Steve -r by console");

        this.run(mod, "unban Steve");
        Assertions.assertThat(mod.last()).contains("only lift punishments you issued");
        Assertions.assertThat(this.active(PunishmentType.BAN)).isOne();

        this.run(this.console, "unban Steve");
        this.run(mod, "ban Steve -r by mod");
        this.run(mod, "unban Steve");
        Assertions.assertThat(this.active(PunishmentType.BAN)).isZero();
    }

    @Test
    void nothingToLiftIsSaid() {
        this.run(this.console, "unmute Steve");

        Assertions.assertThat(this.console.last()).contains("No active punishment");
    }
}
