package net.serlith.sakurabans.core.template;

import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.enforce.ChatDecision;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S61: a template's mute notice and warning as chat or as a dialog. */
class TemplateDisplayTest extends ServiceTest {

    @BeforeEach
    void templates() {
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam").message("<red>Quiet (<reason>)</red>")
                .display(TemplateDisplay.DIALOG).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("language", PunishmentType.WARN)
                .reason("Language").display(TemplateDisplay.DIALOG).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("chatty", PunishmentType.MUTE)
                .reason("Chatty").build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("conduct")
                .reason("Conduct")
                .steps(List.of(
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.KICK, TemplateDuration.open())))
                .display(TemplateDisplay.DIALOG)
                .build(), CONSOLE).join();
    }

    private void preset(final PunishmentType type, final UUID victim, final String template) {
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(type,
                        PlayerVictim.of(victim), MODERATOR).template(template).build(),
                PunishOptions.defaults()).join()).isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void dialogTemplatesShowTheMuteNoticeAsDialogAndRefusalsAsChat() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home)
                .dialogs(true);
        this.preset(PunishmentType.MUTE, STEVE, "spam");

        eventually(() -> !steve.notices().isEmpty());
        Assertions.assertThat(steve.notices()).singleElement().satisfies(notice -> {
            Assertions.assertThat(plain(notice.title())).isEqualTo("You were muted");
            Assertions.assertThat(plain(notice.body())).isEqualTo("Quiet (Spam)");
            Assertions.assertThat(plain(notice.button())).isEqualTo("Close");
        });
        Assertions.assertThat(steve.messages()).extracting(ServiceTest::plain)
                .as("shown once, as the dialog").doesNotContain("Quiet (Spam)");
        // A dialog on every refused chat line would be in the way: the refusal stays chat.
        Assertions.assertThat(this.chatGate.checkChat(steve, "hi"))
                .isInstanceOfSatisfying(ChatDecision.Denied.class, denied -> Assertions
                        .assertThat(plain(denied.message())).isEqualTo("Quiet (Spam)"));
        Assertions.assertThat(steve.notices()).hasSize(1);
    }

    @Test
    void platformsWithoutDialogsGetTheMessageAsChat() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.preset(PunishmentType.MUTE, STEVE, "spam");

        eventually(() -> steve.messages().stream().map(ServiceTest::plain)
                .anyMatch("Quiet (Spam)"::equals));
        Assertions.assertThat(steve.notices()).isEmpty();
    }

    @Test
    void warningsAreDialogsToo() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home)
                .dialogs(true);
        this.preset(PunishmentType.WARN, STEVE, "language");

        eventually(() -> !steve.notices().isEmpty());
        Assertions.assertThat(steve.notices()).singleElement().satisfies(notice -> {
            Assertions.assertThat(plain(notice.title())).isEqualTo("You were warned");
            Assertions.assertThat(plain(notice.body()))
                    .as("the network's warning, as chat would show it")
                    .endsWith("You were warned by Mod: Language");
        });
    }

    @Test
    void chatTemplatesAndDeletedOnesUseChat() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home)
                .dialogs(true);
        this.preset(PunishmentType.MUTE, STEVE, "chatty");
        eventually(() -> !steve.messages().isEmpty());
        Assertions.assertThat(steve.notices()).isEmpty();

        // Enforced (as from another instance) after its template was deleted: chat.
        final FakeOnlinePlayer alex = this.platform.join(ALEX, "Alex", this.home)
                .dialogs(true);
        final Punishment mute = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.MUTE, PlayerVictim.of(ALEX), MODERATOR)
                .template("spam").build(), PunishOptions.builder().enforce(false).build())
                .join()).punishment();
        this.templates.delete(null, "spam", this.templates.find("spam").orElseThrow().version(),
                CONSOLE).join();
        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(mute.id())).join();
        eventually(() -> !alex.messages().isEmpty());
        Assertions.assertThat(alex.notices()).isEmpty();
    }

    @Test
    void laddersShowTheirWarnStepAsDialogButNotTheirKick() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home)
                .dialogs(true);
        this.service.applyLadder(LadderRequest.builder("conduct", PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();
        eventually(() -> !steve.notices().isEmpty());
        Assertions.assertThat(plain(steve.notices().getFirst().title()))
                .isEqualTo("You were warned");

        this.service.applyLadder(LadderRequest.builder("conduct", PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();
        eventually(() -> steve.disconnectReason().isPresent());
        Assertions.assertThat(steve.notices()).as("a kick is a disconnect screen").hasSize(1);
    }
}
