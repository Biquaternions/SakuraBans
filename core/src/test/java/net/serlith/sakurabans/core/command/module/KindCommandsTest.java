package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Punish commands for kinds and /sb kinds list (plan S31a). */
class KindCommandsTest extends CommandTest {

    @Override
    protected String configYaml() {
        return
                """
                commands:
                  kinds:
                    blacklist:
                      type: ban
                      labels: [blacklist, bl]
                    shadow-mute:
                      type: mute
                      labels: [shadowmute]
                    vanished:
                      type: ban
                      labels: [vanished]
                """;
    }

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().build()).join().items();
    }

    @Test
    void kindCommandsPunishWithTheirKind() {
        this.run(this.console, "bl Steve -r ban evasion");
        this.run(this.console, "shadowmute Steve -d 1h -r spam");

        Assertions.assertThat(this.all()).extracting(Punishment::kind)
                .containsExactlyInAnyOrder("blacklist", "shadow-mute");
        Assertions.assertThat(this.all()).filteredOn(p -> p.kind().equals("shadow-mute"))
                .singleElement().satisfies(mute -> Assertions.assertThat(mute.shadow()).isTrue());
    }

    @Test
    void theKindNeedsItsOwnPermissionAndMustExist() {
        final FakeCommandActor mod = FakeCommandActor.player(UUID.randomUUID(), "Mod")
                .grant(Permissions.punish(PunishmentType.BAN));
        this.run(mod, "blacklist Steve -r evading");
        Assertions.assertThat(this.all()).isEmpty();
        mod.grant(Permissions.kind("blacklist"));
        this.run(mod, "blacklist Steve -r evading");
        Assertions.assertThat(this.all()).hasSize(1);

        this.run(this.console, "vanished Steve");
        Assertions.assertThat(this.console.last()).contains("no such punishment kind");
    }

    @Test
    void kindsAreListed() {
        this.run(this.console, "sakurabans kinds list");

        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("blacklist", "strictness=STRICT",
                        "scope=global", "permanent-only"));
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("shadow-mute", "[shadow]"));
    }
}
