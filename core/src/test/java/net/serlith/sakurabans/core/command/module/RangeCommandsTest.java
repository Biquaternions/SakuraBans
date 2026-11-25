package net.serlith.sakurabans.core.command.module;

import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RangeCommandsTest extends CommandTest {

    @Test
    void rangesNeedTheirPermission() {
        final FakeCommandActor mod = FakeCommandActor.player(UUID.randomUUID(), "Mod",
                Permissions.punish(PunishmentType.BAN));
        this.run(mod, "ban 10.0.*.* -r spam");
        Assertions.assertThat(mod.last()).contains("can't punish address ranges");

        mod.grant(Permissions.range(PunishmentType.BAN));
        this.run(mod, "ban 10.0.*.* -r spam");
        Assertions.assertThat(mod.last()).contains("Banned 10.0.0.0/16");
    }

    @Test
    void checkAndHistoryShowExactlyThatRange() {
        this.run(this.console, "ban 2001:db8::/32 -r spam");
        this.run(this.console, "ban 10.0.0.0/8 -r spam");

        this.run(this.console, "checkban 2001:db8::/32");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("2001:db8:0:0:0:0:0:0/32"));
        this.console.clear();
        this.run(this.console, "history 10.0.0.0/8");
        Assertions.assertThat(this.console.texts()).noneSatisfy(line ->
                Assertions.assertThat(line).contains("2001:db8"));

        this.run(this.console, "unban 10.0.0.0/8");
        Assertions.assertThat(this.console.last()).contains("Unbanned");
    }
}
