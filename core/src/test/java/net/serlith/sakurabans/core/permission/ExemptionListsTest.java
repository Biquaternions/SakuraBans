package net.serlith.sakurabans.core.permission;

import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S68: {@code exemption.players} and {@code exemption.groups} add to the provider. */
class ExemptionListsTest extends ServiceTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");
    private static final UUID HELPER = UUID.fromString("7d3e5c1a-2b4f-4e6a-8c9d-0e1f2a3b4c5d");

    @Override
    protected String configYaml() {
        // NONE: only the lists exempt anyone.
        return "exemption:\n  provider: NONE\n  players:\n  - steve:mute\n  - " + ALEX
                + "\n  groups: [Staff]\n";
    }

    private PunishResult punish(final PunishmentType type, final UUID victim) {
        return this.service.punish(DraftPunishment.builder(type, PlayerVictim.of(victim),
                MODERATOR).build(), PunishOptions.defaults()).join();
    }

    private static boolean exempt(final PunishResult result) {
        return result instanceof PunishResult.Failure failure
                && failure.reason() == PunishResult.Failure.Reason.EXEMPT;
    }

    @Test
    void namesWithTypesExemptFromThatTypeOnly() {
        this.names.put(STEVE, "Steve");

        Assertions.assertThat(exempt(this.punish(PunishmentType.MUTE, STEVE))).isTrue();
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void namesThisServerDoesNotKnowMatchNobody() {
        Assertions.assertThat(this.punish(PunishmentType.MUTE, STEVE))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void uuidsExemptFromEveryType() {
        for (final PunishmentType type : PunishmentType.values()) {
            Assertions.assertThat(exempt(this.punish(type, ALEX))).as(type.name()).isTrue();
        }
    }

    @Test
    void groupMembersAreExemptAndTheBypassStillWins() {
        this.platform.join(HELPER, "Helper", this.home).grant(Permissions.luckPermsGroup("staff"));
        Assertions.assertThat(exempt(this.punish(PunishmentType.BAN, HELPER))).isTrue();
        Assertions.assertThat(this.punish(PunishmentType.BAN, BOB))
                .isInstanceOf(PunishResult.Success.class);

        this.platform.online(MOD).orElseThrow()
                .grant(Permissions.exemptBypass(PunishmentType.BAN));
        Assertions.assertThat(this.punish(PunishmentType.BAN, HELPER))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void theConsoleIsNeverStopped() {
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(ALEX), CONSOLE).build(), PunishOptions.defaults()).join())
                .isInstanceOf(PunishResult.Success.class);
    }
}
