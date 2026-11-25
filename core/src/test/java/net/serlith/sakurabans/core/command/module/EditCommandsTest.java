package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.command.CommandTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** /sb info, edit, void, delete and evidence (plan S35). */
class EditCommandsTest extends CommandTest {

    private Punishment ban;

    @Override
    protected String configYaml() {
        return
                """
                display:
                  id-style: BOTH
                """;
    }

    @BeforeEach
    void fixture() {
        this.known(STEVE, "Steve");
        this.ban = ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).reason("hacking")
                .duration(Duration.ofDays(1)).build(), PunishOptions.defaults()).join())
                .punishment();
    }

    private Punishment current() {
        return this.service.byId(this.ban.id()).join().orElseThrow();
    }

    @Test
    void infoShowsDetailsEvidenceAndChanges() {
        this.service.attachEvidence(this.ban.id(), EvidenceRef.of("https://example.org/a"),
                MODERATOR).join();
        final String code = this.ban.displayId().substring(this.ban.displayId().indexOf('/') + 1);

        this.run(this.console, "sb info #" + code);

        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains(this.ban.displayId(), "hacking", "https://example.org/a",
                        "Changes:", "create", "evidence_attach");
    }

    @Test
    void editsChangeReasonDurationAndScope() {
        this.run(this.console, "sb edit " + this.ban.id() + " reason griefing at spawn");
        Assertions.assertThat(this.current().reason()).isEqualTo("griefing at spawn");

        this.run(this.console, "sb edit " + this.ban.id() + " extend 1d");
        Assertions.assertThat(this.current().expiresAt())
                .contains(this.ban.createdAt().plus(Duration.ofDays(2)));
        this.run(this.console, "sb edit " + this.ban.id() + " duration perm");
        Assertions.assertThat(this.current().permanent()).isTrue();
        this.console.clear();
        this.run(this.console, "sb edit " + this.ban.id() + " extend 1d");
        Assertions.assertThat(this.console.last()).contains("is permanent");

        this.run(this.console, "sb edit " + this.ban.id() + " scope server:survival");
        Assertions.assertThat(this.current().scope().toString()).contains("survival");
        this.run(this.console, "sb edit nonsense reason x");
        Assertions.assertThat(this.console.last()).contains("not a punishment id");
    }

    @Test
    void voidAndDeleteNeedTheirStepsAndPermissions() {
        this.run(this.console, "sb void " + this.ban.id() + " -r wrong person");
        Assertions.assertThat(this.current().voiding()).isPresent();
        this.run(this.console, "sb void " + this.ban.id());
        Assertions.assertThat(this.console.last()).contains("already voided");

        this.run(this.console, "sb delete " + this.ban.id());
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.service.byId(this.ban.id()).join()).isPresent();
        this.run(this.console, "sb delete " + this.ban.id() + " --confirm");
        Assertions.assertThat(this.service.byId(this.ban.id()).join()).isEmpty();
    }

    @Test
    void evidenceIsAddedAndRemoved() {
        this.run(this.console, "sb evidence " + this.ban.id() + " add https://example.org/b");
        Assertions.assertThat(this.current().evidence()).hasSize(1);
        this.run(this.console, "sb evidence " + this.ban.id() + " add https://example.org/b");
        Assertions.assertThat(this.console.last()).contains("already attached");
        this.run(this.console, "sb evidence " + this.ban.id() + " remove https://example.org/b");
        Assertions.assertThat(this.current().evidence()).isEmpty();
        this.run(this.console, "sb evidence " + this.ban.id() + " add not a uri");
        Assertions.assertThat(this.current().evidence()).isEmpty();
    }
}
