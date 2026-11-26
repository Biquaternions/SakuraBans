package net.serlith.sakurabans.core.template;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.enforce.ChatDecision;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.model.SakuraTemplateInfo;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.model.TemplateSteps;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D36: a ladder whose steps have their own types, and template messages. */
class MixedLadderTest extends ServiceTest {

    private static final UUID HELPER = UUID.fromString("3a7b4f1c-0f0e-4c1e-9d55-6c1f2b4a8e01");

    private FakeOnlinePlayer steve;

    @BeforeEach
    void toxicity() {
        this.templates.create(TemplateDefinition.builder("toxicity", PunishmentType.BAN)
                .reason("Toxic")
                .ladder(List.of(
                        TemplateStep.of(PunishmentType.KICK, null, List.of(),
                                "<red>Kicked for toxicity: <reason></red>"),
                        TemplateStep.of(PunishmentType.MUTE, Duration.ofHours(1), List.of(),
                                null),
                        TemplateStep.of(PunishmentType.WARN, Duration.ofDays(1), List.of(),
                                null),
                        TemplateStep.of(null, Duration.ofDays(7), List.of(), null)))
                .message("<red>Toxicity (<reason>)</red>")
                .build(), CONSOLE).join();
        this.steve = this.platform.join(STEVE, "Steve", this.home);
    }

    private PunishResult punish(final PunishmentType command, final Actor actor) {
        return this.service.punish(DraftPunishment.builder(command, PlayerVictim.of(STEVE), actor)
                .template("toxicity").build(), PunishOptions.defaults()).join();
    }

    private Punishment punished(final PunishmentType command) {
        return ((PunishResult.Success) this.punish(command, MODERATOR)).punishment();
    }

    private void lift(final Punishment punishment) {
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(punishment.id()),
                MODERATOR).build()).join();
    }

    @Test
    void eachStepHasItsTypeAndTheLadderCountsThemAll() {
        final Punishment kick = this.punished(PunishmentType.BAN);
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        Assertions.assertThat(kick.shadow()).isFalse();
        eventually(() -> this.steve.disconnectReason().isPresent());
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Kicked for toxicity: Toxic");

        // A mixed ladder is usable from every punish command; the step decides.
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        final Punishment mute = this.punished(PunishmentType.KICK);
        Assertions.assertThat(mute.type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(mute.shadow()).isFalse();
        Assertions.assertThat(Duration.between(mute.createdAt(), mute.expiresAt().orElseThrow()))
                .isEqualTo(Duration.ofHours(1));
        eventually(() -> this.mutes.activeMute(STEVE).isPresent());
        this.lift(mute);

        final Punishment warning = this.punished(PunishmentType.MUTE);
        Assertions.assertThat(warning.type()).isEqualTo(PunishmentType.WARN);
        final Punishment ban = this.punished(PunishmentType.WARN);
        Assertions.assertThat(ban.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(ban.template().orElseThrow().step()).isEqualTo(3);
        Assertions.assertThat(Duration.between(ban.createdAt(), ban.expiresAt().orElseThrow()))
                .isEqualTo(Duration.ofDays(7));

        // Shadow applies to the mute steps only.
        this.templates.create(TemplateDefinition.builder("quiet", PunishmentType.MUTE)
                .reason("Quiet").shadow(true).ladder(List.of(TemplateStep.of(
                        PunishmentType.KICK, null, List.of(), null), TemplateStep.of(
                        Duration.ofHours(1)))).build(), CONSOLE).join();
        final List<Punishment> quiet = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            quiet.add(((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                    PunishmentType.MUTE, PlayerVictim.of(ALEX), MODERATOR).template("quiet")
                    .build(), PunishOptions.defaults()).join()).punishment());
        }
        Assertions.assertThat(quiet).extracting(Punishment::type, Punishment::shadow)
                .containsExactly(Assertions.tuple(PunishmentType.KICK, false),
                        Assertions.tuple(PunishmentType.MUTE, true));

        // A single-type template stays on its own command.
        this.templates.create(TemplateDefinition.builder("spam", PunishmentType.MUTE)
                .reason("Spam").build(), CONSOLE).join();
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(ALEX), MODERATOR).template("spam").build(),
                PunishOptions.defaults()).join()).isInstanceOfSatisfying(
                        PunishResult.Failure.class, failure -> Assertions.assertThat(
                                failure.reason()).isEqualTo(
                                PunishResult.Failure.Reason.UNKNOWN_TEMPLATE));
    }

    @Test
    void theSenderNeedsThePermissionOfTheResultingType() {
        this.platform.join(HELPER, "Helper", this.home).grant(
                Permissions.punish(PunishmentType.BAN));
        final Actor helper = Actor.of(PlayerOperator.of(HELPER, "Helper"), "in-game");

        // A /ban whose step is a kick needs the kick permission, not the ban one.
        Assertions.assertThat(this.punish(PunishmentType.BAN, helper)).isInstanceOfSatisfying(
                PunishResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN));
        this.platform.online(HELPER).orElseThrow().grant(Permissions.punish(PunishmentType.KICK));
        Assertions.assertThat(this.punish(PunishmentType.BAN, helper))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void limitsApplyToTheResultingType() throws Exception {
        Files.writeString(this.dir.resolve("limits.yml"),
                """
                reduce-to-limit: true
                groups:
                  helper:
                    permission: sakurabans.group.helper
                    max-duration: {mute: 30m}
                """);
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        this.platform.join(HELPER, "Helper", this.home).grant("sakurabans.group.helper",
                Permissions.punish(PunishmentType.KICK), Permissions.punish(PunishmentType.MUTE),
                Permissions.punish(PunishmentType.BAN));
        final Actor helper = Actor.of(PlayerOperator.of(HELPER, "Helper"), "in-game");

        this.punish(PunishmentType.BAN, helper);
        final Punishment mute = ((PunishResult.Success) this.punish(PunishmentType.BAN, helper))
                .punishment();

        Assertions.assertThat(mute.type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(Duration.between(mute.createdAt(), mute.expiresAt().orElseThrow()))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void templateMessagesReachEveryEnforcementPath() {
        // A scoped network message (S53) still gives way to the template's.
        this.messageTemplates.change(ServerScope.of("lobby"), Locale.US, Map.of(
                "screen.ban_temporary",
                "Lobby screen: <reason>"), Set.of(), 0, CONSOLE).join();
        this.punished(PunishmentType.BAN);
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.punished(PunishmentType.BAN);
        // The mute notice and the chat refusal show the template's message.
        eventually(() -> this.mutes.activeMute(STEVE).isPresent());
        Assertions.assertThat(this.steve.messages()).extracting(ServiceTest::plain)
                .contains("Toxicity (Toxic)");
        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOfSatisfying(ChatDecision.Denied.class, denied -> Assertions
                        .assertThat(plain(denied.message())).isEqualTo("Toxicity (Toxic)"));
        final Punishment mute = this.mutes.activeMute(STEVE).orElseThrow();
        this.lift(mute);

        this.punished(PunishmentType.BAN);
        Assertions.assertThat(this.steve.messages()).extracting(ServiceTest::plain)
                .filteredOn(text -> text.equals("Toxicity (Toxic)")).hasSize(2)
                .as("the warning is shown with the template's message too");

        final Punishment ban = this.punished(PunishmentType.BAN);
        eventually(() -> this.steve.disconnectReason().isPresent());
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Toxicity (Toxic)");
        final LoginDecision login = this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                this.home, null)).join();
        Assertions.assertThat(login).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> Assertions.assertThat(plain(denied.screen()))
                        .isEqualTo("Toxicity (Toxic)"));

        // Without the template, the network's message applies again.
        this.templates.delete("toxicity", this.templates.find("toxicity").orElseThrow()
                .version(), CONSOLE).join();
        final LoginDecision fallback = this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                this.home, null)).join();
        Assertions.assertThat(fallback).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> Assertions.assertThat(plain(denied.screen()))
                        .isEqualTo("Lobby screen: Toxic"));
        Assertions.assertThat(ban.type()).isEqualTo(PunishmentType.BAN);
    }

    @Test
    void anotherInstancesStepsAreEnforcedByType() {
        final List<Punishment> steps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            steps.add(((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                    PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).template("toxicity")
                    .build(), PunishOptions.builder().enforce(false).build()).join())
                    .punishment());
        }
        Assertions.assertThat(steps).extracting(Punishment::type).containsExactly(
                PunishmentType.KICK, PunishmentType.MUTE, PunishmentType.WARN,
                PunishmentType.BAN);

        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(steps.get(1).id()))
                .join();
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isPresent();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();
        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(steps.get(0).id()))
                .join();
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Kicked for toxicity: Toxic");
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(steps.get(3).id()))
                .join();
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Toxicity (Toxic)");
    }

    @Test
    void stepsReadAndWriteInBothFormats() {
        Assertions.assertThat(List.of("7d", "permanent", "kick", "mute 1h", "ban permanent",
                "Mute 1H")).extracting(text -> {
                    final TemplateSteps.Parsed parsed = TemplateSteps.parse(text);
                    return parsed.type() + " " + parsed.duration() + " " + parsed.error();
                }).containsExactly("null PT168H null", "null null null", "KICK null null",
                        "MUTE PT1H null", "BAN null null", "MUTE PT1H null");
        Assertions.assertThat(List.of("kick 1h", "ban", "mute soon", "ban 1d 2d", "jail 1d"))
                .allSatisfy(text -> Assertions.assertThat(TemplateSteps.parse(text).error())
                        .isNotNull());

        final SakuraTemplateInfo stored = this.templates.find("toxicity").orElseThrow();
        final Map<String, Object> tree = TemplateFiles.tree(stored);
        Assertions.assertThat(tree.get("ladder")).isEqualTo(List.of("kick", "mute 1h",
                "warn 1d", "7d"));
        Assertions.assertThat(tree.get("message")).isEqualTo("<red>Toxicity (<reason>)</red>");
        Assertions.assertThat(tree.get("step-messages")).isEqualTo(Map.of("1",
                "<red>Kicked for toxicity: <reason></red>"));
        final TemplateFiles.Read read = TemplateFiles.definition("toxicity", tree, "t");
        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(SakuraTemplateInfo.of(read.templates().getFirst(),
                stored.version())).isEqualTo(stored);

        // A step of the template's own type is written without it.
        Assertions.assertThat(TemplateFiles.tree(SakuraTemplateInfo.of(TemplateDefinition
                .builder("b", PunishmentType.BAN).reason("r").ladder(List.of(TemplateStep.of(
                        PunishmentType.BAN, Duration.ofDays(1), List.of(), null))).build(), 1))
                .get("ladder")).isEqualTo(List.of("1d"));
        Assertions.assertThat(TemplateFiles.definition("x", Map.of("type", "mute",
                "reason", "r", "ladder", List.of("kick 1h"), "step-messages",
                Map.of("2", "m")), "t").errors()).extracting(issue -> issue.path())
                .containsExactlyInAnyOrder("t.ladder[0]", "t.step-messages.2");
        Assertions.assertThatThrownBy(() -> TemplateDefinition.builder("s", PunishmentType.BAN)
                .reason("r").shadow(true).build()).isInstanceOf(ValidationException.class);
    }
}
