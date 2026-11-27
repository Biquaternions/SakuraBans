package net.serlith.sakurabans.core.template;

import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.EditResult;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.punish.VoidResult;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Silent templates, and a template's permission guarding its punishments. */
class TemplatePolicyTest extends ServiceTest {

    private static final UUID HELPER = UUID.fromString("7d3e5c1a-2b4f-4e6a-8c9d-0e1f2a3b4c5d");
    private static final Actor HELPER_ACTOR = Actor.of(PlayerOperator.of(HELPER, "Helper"),
            "in-game");

    private void create(final TemplateBuilder template) {
        Assertions.assertThat(this.templates.create(template.build(), CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
    }

    private Punishment punish(final String template, final PunishOptions options) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), CONSOLE).template(template).build(),
                options).join()).punishment();
    }

    private void lift(final Punishment punishment) {
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(punishment.id()),
                CONSOLE).build()).join();
    }

    @Test
    void silentTemplatesMakeTheirPunishmentsSilentUnlessHiddenFurther() {
        this.create(TemplateBuilder.preset("raid", PunishmentType.BAN).reason("Raid").silent(true));
        this.create(TemplateBuilder.ladder("abuse").reason("Abuse").silent(true)
                .steps(java.util.List.of(TemplateStep.of(PunishmentType.WARN,
                        TemplateDuration.open(), null, null, java.util.List.of()))));
        this.create(TemplateBuilder.preset("loud", PunishmentType.BAN).reason("Loud"));

        final Punishment first = this.punish("raid", PunishOptions.defaults());
        Assertions.assertThat(first.visibility()).isEqualTo(Visibility.SILENT);
        this.lift(first);
        final Punishment hidden = this.punish("raid", PunishOptions.builder()
                .visibility(Visibility.HIDDEN).build());
        Assertions.assertThat(hidden.visibility()).isEqualTo(Visibility.HIDDEN);
        this.lift(hidden);
        Assertions.assertThat(this.punish("loud", PunishOptions.defaults()).visibility())
                .isEqualTo(Visibility.NORMAL);

        final LadderResult step = this.service.applyLadder(LadderRequest.builder("abuse",
                PlayerVictim.of(ALEX), CONSOLE).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(((LadderResult.Punished) step).punishment().visibility())
                .isEqualTo(Visibility.SILENT);
        Assertions.assertThat(this.templates.find("raid").orElseThrow().template().silent())
                .as("stored and read back").isTrue();
    }

    @Test
    void guardedTemplatesPunishmentsNeedItsPermission() {
        this.create(TemplateBuilder.preset("vip", PunishmentType.BAN).reason("VIP")
                .permissionRequired(true));
        final Punishment ban = this.punish("vip", PunishOptions.defaults());
        this.platform.join(HELPER, "Helper", this.home).grant(staffPermissions());

        Assertions.assertThat(this.service.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()), HELPER_ACTOR).build()).join())
                .isInstanceOfSatisfying(RevokeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(RevokeResult.Failure.Reason.TEMPLATE_PROTECTED));
        Assertions.assertThat(this.service.edit(EditRequest.builder(ban.id(),
                        PunishmentEdit.NewReason.of("x"), HELPER_ACTOR).build()).join())
                .isInstanceOfSatisfying(EditResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(EditResult.Failure.Reason.TEMPLATE_PROTECTED));
        Assertions.assertThat(this.service.voidPunishment(ban.id(), HELPER_ACTOR, null).join())
                .isInstanceOfSatisfying(VoidResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(VoidResult.Failure.Reason.TEMPLATE_PROTECTED));
        Assertions.assertThat(this.service.expunge(ban.id(), HELPER_ACTOR).join())
                .isInstanceOfSatisfying(ExpungeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(ExpungeResult.Failure.Reason.TEMPLATE_PROTECTED));
        // An override would end it: refused, and the ban stays.
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                        PlayerVictim.of(STEVE), HELPER_ACTOR).build(), PunishOptions.builder()
                        .override(OverridePolicy.REPLACE).build()).join())
                .isInstanceOfSatisfying(PunishResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(PunishResult.Failure.Reason.TEMPLATE_PROTECTED));
        Assertions.assertThat(this.punishments.byId(ban.id()).join().orElseThrow()
                .isActiveAt(this.now.get())).isTrue();

        // With the node it works; the console needs none.
        this.platform.online(HELPER).orElseThrow().grant(Permissions.template("vip"));
        Assertions.assertThat(this.service.edit(EditRequest.builder(ban.id(),
                        PunishmentEdit.NewReason.of("x"), HELPER_ACTOR).build()).join())
                .isInstanceOf(EditResult.Success.class);
        Assertions.assertThat(this.service.voidPunishment(ban.id(), CONSOLE, null).join())
                .isInstanceOf(VoidResult.Success.class);
    }

    @Test
    void deletedTemplatesGuardNothing() {
        this.create(TemplateBuilder.preset("vip", PunishmentType.BAN).reason("VIP")
                .permissionRequired(true));
        final Punishment ban = this.punish("vip", PunishOptions.defaults());
        this.platform.join(HELPER, "Helper", this.home).grant(staffPermissions());
        final long version = this.templates.find("vip").orElseThrow().version();
        Assertions.assertThat(this.templates.delete(null, "vip", version, CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);

        Assertions.assertThat(this.service.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()), HELPER_ACTOR).build()).join())
                .isInstanceOf(RevokeResult.Success.class);
    }
}
