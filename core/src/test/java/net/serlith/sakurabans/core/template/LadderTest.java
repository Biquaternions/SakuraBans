package net.serlith.sakurabans.core.template;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.TemplateAppliedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.enforce.ChatDecision;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ladders whose steps name their own type or none, applied with {@code applyLadder}. */
class LadderTest extends ServiceTest {

    private static final UUID HELPER = UUID.fromString("3a7b4f1c-0f0e-4c1e-9d55-6c1f2b4a8e01");
    private static final Actor HELPER_ACTOR = Actor.of(PlayerOperator.of(HELPER, "Helper"),
            "in-game");

    private FakeOnlinePlayer steve;

    @BeforeEach
    void misconduct() {
        this.templates.create(TemplateBuilder.ladder("misconduct")
                .reason("Misconduct")
                .steps(List.of(
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.KICK, TemplateDuration.open(), null,
                                "<red>Kicked for misconduct: <reason></red>", List.of()),
                        TemplateStep.of(PunishmentType.MUTE,
                                TemplateDuration.fixed(Duration.ofHours(1))),
                        TemplateStep.of(null, TemplateDuration.open(), "On probation", null,
                                List.of("say {victim} {reason} step {step} id '{id}'")),
                        TemplateStep.of(PunishmentType.BAN,
                                TemplateDuration.fixed(Duration.ofDays(7)), "Repeated misconduct",
                                null, List.of())))
                .expiry(Duration.ofDays(30))
                .groups(Map.of("conduct", 1))
                .message("<red>Misconduct (<reason>)</red>")
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("language", PunishmentType.WARN)
                .reason("Language").groups(Map.of("conduct", 1)).build(), CONSOLE).join();
        this.steve = this.platform.join(STEVE, "Steve", this.home);
    }

    private LadderResult apply(final UUID victim, final Actor actor) {
        return this.apply("misconduct", victim, actor, PunishOptions.defaults());
    }

    private LadderResult apply(final String ladder, final UUID victim, final Actor actor,
            final PunishOptions options) {
        return this.service.applyLadder(LadderRequest.builder(ladder, PlayerVictim.of(victim),
                actor).build(), options).join();
    }

    private Punishment punished(final UUID victim) {
        return ((LadderResult.Punished) this.apply(victim, MODERATOR)).punishment();
    }

    private int next(final UUID victim) {
        return this.templates.nextStep("misconduct", PlayerVictim.of(victim)).join()
                .orElseThrow();
    }

    private void lift(final Punishment punishment) {
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(punishment.id()),
                MODERATOR).build()).join();
    }

    private static Duration length(final Punishment punishment) {
        return Duration.between(punishment.createdAt(), punishment.expiresAt().orElseThrow());
    }

    private void helper(final String... permissions) {
        this.platform.join(HELPER, "Helper", this.home).grant(permissions);
    }

    @Test
    void eachStepHasItsTypeAndTheActionOnlyStepCountsToo() {
        this.names.put(STEVE, "Steve");
        final Punishment warning = this.punished(STEVE);
        Assertions.assertThat(warning.type()).isEqualTo(PunishmentType.WARN);
        Assertions.assertThat(length(warning)).as("warnings.expire-after")
                .isEqualTo(this.config.settings().warnings().expireAfter());
        Assertions.assertThat(warning.template().orElseThrow().step()).hasValue(0);
        Assertions.assertThat(warning.reason()).isEqualTo("Misconduct");

        final Punishment kick = this.punished(STEVE);
        Assertions.assertThat(kick.type()).isEqualTo(PunishmentType.KICK);
        eventually(() -> this.steve.disconnectReason().isPresent());
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Kicked for misconduct: Misconduct");
        this.steve = this.platform.join(STEVE, "Steve", this.home);

        final Punishment mute = this.punished(STEVE);
        Assertions.assertThat(mute.type()).isEqualTo(PunishmentType.MUTE);
        Assertions.assertThat(length(mute)).isEqualTo(Duration.ofHours(1));
        this.lift(mute);

        final int punishments = this.events(PunishmentCreatedEvent.class).size();
        Assertions.assertThat(this.apply(STEVE, MODERATOR)).isInstanceOfSatisfying(
                LadderResult.ActionsOnly.class, only -> Assertions.assertThat(only.step())
                        .isEqualTo(3));
        Assertions.assertThat(this.events(PunishmentCreatedEvent.class)).hasSize(punishments);
        Assertions.assertThat(this.platform.consoleCommands())
                .containsExactly("say Steve On probation step 4 id ''");
        Assertions.assertThat(this.events(TemplateAppliedEvent.class)).last()
                .satisfies(event -> {
                    Assertions.assertThat(event.punishment()).isEmpty();
                    Assertions.assertThat(event.victim()).isEqualTo(PlayerVictim.of(STEVE));
                    Assertions.assertThat(event.template().step()).hasValue(3);
                });

        // The action-only step moved the ladder on: the ban comes next, with the step's reason.
        Assertions.assertThat(this.apply(STEVE, MODERATOR)).isInstanceOfSatisfying(
                LadderResult.Punished.class, punished -> {
                    Assertions.assertThat(punished.step()).isEqualTo(4);
                    Assertions.assertThat(punished.punishment().type())
                            .isEqualTo(PunishmentType.BAN);
                    Assertions.assertThat(punished.punishment().reason())
                            .isEqualTo("Repeated misconduct");
                    Assertions.assertThat(length(punished.punishment()))
                            .isEqualTo(Duration.ofDays(7));
                });
        Assertions.assertThat(this.next(STEVE)).as("the last step repeats").isEqualTo(4);
        Assertions.assertThat(this.events(TemplateAppliedEvent.class)).hasSize(5)
                .allSatisfy(event -> Assertions.assertThat(event.victim())
                        .isEqualTo(PlayerVictim.of(STEVE)));
        // A reason given with the request wins over the step's.
        this.lift(((LadderResult.Punished) this.service.applyLadder(LadderRequest.builder(
                "misconduct", PlayerVictim.of(ALEX), MODERATOR).reason("Given").build(),
                PunishOptions.defaults()).join()).punishment());
        Assertions.assertThat(this.next(ALEX)).isOne();
    }

    @Test
    void voidedUsesDontCountAndTheExpiryForgets() {
        final Punishment warning = this.punished(STEVE);
        Assertions.assertThat(this.next(STEVE)).isOne();
        this.service.voidPunishment(warning.id(), MODERATOR, null).join();
        Assertions.assertThat(this.next(STEVE)).isZero();

        this.punished(STEVE);
        this.punished(STEVE);
        Assertions.assertThat(this.next(STEVE)).isEqualTo(2);
        this.now.set(this.now.get().plus(Duration.ofDays(31)));
        Assertions.assertThat(this.next(STEVE)).isZero();
    }

    @Test
    void presetsInTheGroupCountTowardsTheLadder() {
        for (int i = 0; i < 2; i++) {
            Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.WARN,
                    PlayerVictim.of(STEVE), MODERATOR).template("language").build(),
                    PunishOptions.defaults()).join()).isInstanceOf(PunishResult.Success.class);
        }

        Assertions.assertThat(this.next(STEVE)).isEqualTo(2);
        Assertions.assertThat(this.punished(STEVE).type()).isEqualTo(PunishmentType.MUTE);
    }

    @Test
    void laddersNeedThePunishPermissionNotTheStepsType() {
        this.helper(Permissions.punish(PunishmentType.WARN),
                Permissions.punish(PunishmentType.BAN));
        Assertions.assertThat(this.apply(STEVE, HELPER_ACTOR)).isInstanceOfSatisfying(
                LadderResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN));

        this.helper(Permissions.PUNISH);
        Assertions.assertThat(this.apply(STEVE, HELPER_ACTOR))
                .isInstanceOf(LadderResult.Punished.class);
        Assertions.assertThat(this.apply(STEVE, HELPER_ACTOR))
                .as("a kick without the kick permission").isInstanceOf(LadderResult.Punished.class);

        this.templates.create(TemplateBuilder.ladder("secret").reason("Secret")
                .permissionRequired(true)
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .build(), CONSOLE).join();
        Assertions.assertThat(this.apply("secret", ALEX, HELPER_ACTOR, PunishOptions.defaults()))
                .isInstanceOfSatisfying(LadderResult.Failure.class, failure -> Assertions
                        .assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN));
        this.platform.online(HELPER).orElseThrow().grant(Permissions.template("secret"));
        Assertions.assertThat(this.apply("secret", ALEX, HELPER_ACTOR, PunishOptions.defaults()))
                .isInstanceOf(LadderResult.Punished.class);
        Assertions.assertThat(this.apply("secret", ALEX, CONSOLE, PunishOptions.defaults()))
                .isInstanceOf(LadderResult.Punished.class);
    }

    @Test
    void cooldownsAndExemptionApplyByTheStepsTypeButNoDurationCap() throws Exception {
        Files.writeString(this.dir.resolve("limits.yml"),
                """
                reduce-to-limit: true
                groups:
                  helper:
                    permission: sakurabans.group.helper
                    max-duration: {mute: 30m, warn: 1d}
                    cooldown: {kick: 1h}
                """);
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        this.helper("sakurabans.group.helper", Permissions.PUNISH);

        Assertions.assertThat(length(((LadderResult.Punished) this.apply(STEVE, HELPER_ACTOR))
                .punishment())).as("the warn step isn't cut to 1d")
                .isEqualTo(this.config.settings().warnings().expireAfter());
        this.apply(STEVE, HELPER_ACTOR);
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        Assertions.assertThat(length(((LadderResult.Punished) this.apply(STEVE, HELPER_ACTOR))
                .punishment())).isEqualTo(Duration.ofHours(1));

        // Alex's kick comes within the kick cooldown; nothing is recorded.
        this.apply(ALEX, HELPER_ACTOR);
        Assertions.assertThat(this.apply(ALEX, HELPER_ACTOR)).isInstanceOfSatisfying(
                LadderResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.COOLDOWN));
        Assertions.assertThat(this.next(ALEX)).isOne();

        // Exempt from every type: the punishing steps are refused, the action-only one isn't.
        final List<String> exempt = new ArrayList<>();
        for (final PunishmentType type : PunishmentType.values()) {
            exempt.add(Permissions.exempt(type));
        }
        this.steve.grant(exempt.toArray(String[]::new));
        Assertions.assertThat(this.apply(STEVE, HELPER_ACTOR)).isInstanceOf(
                LadderResult.ActionsOnly.class);
        Assertions.assertThat(this.apply(STEVE, HELPER_ACTOR)).isInstanceOfSatisfying(
                LadderResult.Failure.class, failure -> Assertions.assertThat(failure.reason())
                        .isEqualTo(PunishResult.Failure.Reason.EXEMPT));
    }

    @Test
    void templateMessagesReachEveryEnforcementPath() {
        // A scoped network message still gives way to the template's.
        this.messageTemplates.change(ServerScope.of("lobby"), Locale.US, Map.of(
                "screen.ban_temporary",
                "Lobby screen: <reason>"), Set.of(), 0, CONSOLE).join();
        this.punished(STEVE);
        Assertions.assertThat(this.steve.messages()).extracting(ServiceTest::plain)
                .contains("Misconduct (Misconduct)")
                .as("the warning is shown with the template's message");
        this.punished(STEVE);
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.punished(STEVE);
        // The mute notice and the chat refusal show the template's message.
        eventually(() -> this.mutes.activeMute(STEVE).isPresent());
        Assertions.assertThat(this.steve.messages()).extracting(ServiceTest::plain)
                .contains("Misconduct (Misconduct)");
        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOfSatisfying(ChatDecision.Denied.class, denied -> Assertions
                        .assertThat(plain(denied.message())).isEqualTo("Misconduct (Misconduct)"));
        this.lift(this.mutes.activeMute(STEVE).orElseThrow());
        this.apply(STEVE, MODERATOR);

        final Punishment ban = this.punished(STEVE);
        eventually(() -> this.steve.disconnectReason().isPresent());
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Misconduct (Repeated misconduct)");
        final LoginDecision login = this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                this.home, null)).join();
        Assertions.assertThat(login).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> Assertions.assertThat(plain(denied.screen()))
                        .isEqualTo("Misconduct (Repeated misconduct)"));

        // Without the template, the network's message applies again.
        this.templates.delete(null, "misconduct", this.templates.find("misconduct").orElseThrow()
                .version(), CONSOLE).join();
        final LoginDecision fallback = this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                this.home, null)).join();
        Assertions.assertThat(fallback).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> Assertions.assertThat(plain(denied.screen()))
                        .isEqualTo("Lobby screen: Repeated misconduct"));
        Assertions.assertThat(ban.type()).isEqualTo(PunishmentType.BAN);
    }

    @Test
    void presetMessagesAndShadowApplyToo() {
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam").message("<red>Quiet (<reason>)</red>").build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("hush", PunishmentType.MUTE)
                .reason("Hush").shadow(true).build(), CONSOLE).join();
        final Punishment mute = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR)
                .template("spam").build(), PunishOptions.defaults()).join()).punishment();
        final Punishment hush = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.MUTE, PlayerVictim.of(ALEX), MODERATOR)
                .template("hush").build(), PunishOptions.defaults()).join()).punishment();

        Assertions.assertThat(mute.shadow()).isFalse();
        Assertions.assertThat(hush.shadow()).isTrue();
        Assertions.assertThat(mute.template().orElseThrow().step()).isEmpty();
        eventually(() -> this.mutes.activeMute(STEVE).isPresent());
        Assertions.assertThat(this.steve.messages()).extracting(ServiceTest::plain)
                .contains("Quiet (Spam)");
    }

    @Test
    void anotherInstancesStepsAreEnforcedByType() {
        final List<Punishment> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            steps.add(((LadderResult.Punished) this.apply("misconduct", STEVE, MODERATOR,
                    PunishOptions.builder().enforce(false).build())).punishment());
        }
        Assertions.assertThat(steps).extracting(Punishment::type).containsExactly(
                PunishmentType.WARN, PunishmentType.KICK, PunishmentType.MUTE);

        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(steps.get(2).id()))
                .join();
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isPresent();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();
        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(steps.get(1).id()))
                .join();
        Assertions.assertThat(plain(this.steve.disconnectReason().orElseThrow()))
                .isEqualTo("Kicked for misconduct: Misconduct");
    }
}
