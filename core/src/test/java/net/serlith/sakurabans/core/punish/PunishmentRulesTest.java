package net.serlith.sakurabans.core.punish;

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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PunishmentRulesTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return "scopes:\n  default: local\n"
                + "reasons:\n  require:\n    ban: true\n    revoke: true\n";
    }

    private PunishResult punish(final DraftPunishment.Builder draft) {
        return this.service.punish(draft.build(), PunishOptions.defaults()).join();
    }

    @Test
    void requiredReasonsAreEnforced() {
        Assertions.assertThat(this.punish(DraftPunishment.builder(PunishmentType.BAN,
                        PlayerVictim.of(STEVE), MODERATOR).reason("   ")))
                .isInstanceOfSatisfying(PunishResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(PunishResult.Failure.Reason.REASON_REQUIRED));
        Assertions.assertThat(this.punish(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), MODERATOR))).isInstanceOf(PunishResult.Success.class);

        final Punishment ban = ((PunishResult.Success) this.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR)
                .reason("line one\nline two"))).punishment();
        Assertions.assertThat(ban.reason()).isEqualTo("line one line two");
        Assertions.assertThat(ban.scope()).isEqualTo(ServerScope.of("lobby"));

        Assertions.assertThat(this.service.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()), CONSOLE).build()).join())
                .isInstanceOfSatisfying(RevokeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(RevokeResult.Failure.Reason.REASON_REQUIRED));
    }
}
