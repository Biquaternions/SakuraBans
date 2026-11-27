package net.serlith.sakurabans.core.limit;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.SakuraLadderTemplate;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.PunishmentFactory.Origin;
import net.serlith.sakurabans.core.punish.PunishmentFactory.Prepared;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Limit groups, reduce-to-limit, cooldowns and require-template. */
class LimitServiceTest extends ServiceTest {

    private FakeOnlinePlayer mod;

    @BeforeEach
    void limits() throws Exception {
        this.writeLimits(true);
        this.mod = this.platform.join(MOD, "Mod", this.home).grant(staffPermissions());
    }

    private void writeLimits(final boolean reduce) throws Exception {
        Files.writeString(this.dir.resolve("limits.yml"),
                """
                reduce-to-limit: %s
                groups:
                  helper:
                    permission: sakurabans.group.helper
                    max-duration: {ban: 1d, mute: permanent}
                    cooldown: {ban: 30s}
                    require-template: false
                  trainee:
                    permission: sakurabans.group.trainee
                    require-template: true
                """.formatted(reduce));
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
    }

    private PunishResult ban(final @Nullable Duration duration, final PunishOptions options) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(STEVE), MODERATOR).duration(duration).build(), options).join();
    }

    private PunishResult ban(final @Nullable Duration duration) {
        return this.ban(duration, PunishOptions.builder()
                .override(OverridePolicy.REPLACE).build());
    }

    private static Punishment punishment(final PunishResult result) {
        return ((PunishResult.Success) result).punishment();
    }

    private static PunishResult.Failure.Reason reason(final PunishResult result) {
        return ((PunishResult.Failure) result).reason();
    }

    @Test
    void staffInNoGroupAndTheConsoleAreUnlimited() {
        Assertions.assertThat(punishment(this.ban(null)).permanent()).isTrue();
        Assertions.assertThat(punishment(this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(ALEX), CONSOLE).build(),
                PunishOptions.defaults()).join()).permanent()).isTrue();
    }

    @Test
    void longPunishmentsAreReducedOrRefused() throws Exception {
        this.mod.grant("sakurabans.group.helper");

        Assertions.assertThat(punishment(this.ban(null)).expiresAt())
                .hasValue(this.now.get().plus(Duration.ofDays(1)));
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        Assertions.assertThat(punishment(this.ban(Duration.ofHours(2))).expiresAt())
                .hasValue(this.now.get().plus(Duration.ofHours(2)));

        this.writeLimits(false);
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        Assertions.assertThat(reason(this.ban(Duration.ofDays(2))))
                .isEqualTo(PunishResult.Failure.Reason.LIMIT_EXCEEDED);
        Assertions.assertThat(punishment(this.ban(Duration.ofDays(2), PunishOptions.builder()
                .bypassLimits(true).override(
                        OverridePolicy.REPLACE).build()))
                .expiresAt()).hasValue(this.now.get().plus(Duration.ofDays(2)));

        this.mod.grant(Permissions.LIMITS_UNLIMITED);
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        Assertions.assertThat(punishment(this.ban(null)).permanent()).isTrue();
    }

    @Test
    void cooldownsWaitBetweenPunishments() {
        this.mod.grant("sakurabans.group.helper");
        this.ban(Duration.ofHours(1));

        Assertions.assertThat(reason(this.ban(Duration.ofHours(1))))
                .isEqualTo(PunishResult.Failure.Reason.COOLDOWN);
        this.now.set(this.now.get().plus(Duration.ofSeconds(31)));
        Assertions.assertThat(this.ban(Duration.ofHours(1)))
                .isInstanceOf(PunishResult.Success.class);

        this.mod.grant(Permissions.COOLDOWN_BYPASS);
        Assertions.assertThat(this.ban(Duration.ofHours(1)))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void theFirstMatchingGroupApplies() {
        this.mod.grant("sakurabans.group.trainee");
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking")
                .duration(TemplateDuration.fixed(Duration.ofDays(30))).build(), CONSOLE).join();

        Assertions.assertThat(reason(this.ban(Duration.ofHours(1))))
                .isEqualTo(PunishResult.Failure.Reason.TEMPLATE_REQUIRED);
        final PunishResult templated = this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).template("hacking")
                .build(), PunishOptions.defaults()).join();
        Assertions.assertThat(punishment(templated).expiresAt())
                .hasValue(this.now.get().plus(Duration.ofDays(30)));

        // helper comes first in the file, so its 1d limit applies to the template's 30d.
        this.mod.grant("sakurabans.group.helper");
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        final PunishResult limited = this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(ALEX), MODERATOR).template("hacking")
                .build(), PunishOptions.defaults()).join();
        Assertions.assertThat(punishment(limited).expiresAt())
                .hasValue(this.now.get().plus(Duration.ofDays(1)));
    }

    @Test
    void ladderStepIsKnownByItsMarkerNotByItsUse() {
        // Prepared.Ready says where it comes from; the limits read that.
        this.mod.grant("sakurabans.group.helper");
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking")
                .duration(TemplateDuration.fixed(Duration.ofDays(30))).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("cheating").reason("Cheating")
                .steps(List.of(TemplateStep.of(PunishmentType.BAN,
                        TemplateDuration.fixed(Duration.ofDays(30)))))
                .build(), CONSOLE).join();
        final PunishmentFactory factory = new PunishmentFactory(this.scopes, this.config,
                this.players, this.platform, this.clock, this.templates);

        final Prepared.Ready plain = (Prepared.Ready) factory.prepare(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).build(),
                PunishOptions.defaults()).join();
        Assertions.assertThat(plain.origin()).isEqualTo(Origin.PLAIN);
        final Prepared.Ready preset = (Prepared.Ready) factory.prepare(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).template("hacking")
                .build(), PunishOptions.defaults()).join();
        Assertions.assertThat(preset.origin()).isEqualTo(Origin.PRESET);
        final Prepared.Ready step = (Prepared.Ready) factory.prepare(LadderRequest.builder(
                "cheating", PlayerVictim.of(STEVE), MODERATOR).build(),
                (SakuraLadderTemplate) this.templates.find("cheating").orElseThrow().template(), 0,
                PunishOptions.defaults()).join();
        Assertions.assertThat(step.origin()).isEqualTo(Origin.LADDER_STEP);
        Assertions.assertThat(step.template()).isEqualTo(this.templates.find("cheating")
                .orElseThrow().template());

        final Instant capped = this.now.get().plus(Duration.ofDays(1));
        final Instant full = this.now.get().plus(Duration.ofDays(30));
        Assertions.assertThat(expiry(this.limits.apply(preset, PunishOptions.defaults())))
                .isEqualTo(capped);
        Assertions.assertThat(expiry(this.limits.apply(step, PunishOptions.defaults())))
                .isEqualTo(full);
        // The preset's use (no step) marked as a ladder step: the marker decides.
        Assertions.assertThat(expiry(this.limits.apply(new Prepared.Ready(preset.punishment(),
                Origin.LADDER_STEP, preset.use(), preset.template()), PunishOptions.defaults())))
                .isEqualTo(full);
        Assertions.assertThatThrownBy(() -> new Prepared.Ready(preset.punishment(),
                Origin.PLAIN, preset.use(), preset.template()))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> new Prepared.Ready(preset.punishment(),
                Origin.PRESET, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void writeLimitsYaml(final String yaml) throws Exception {
        Files.writeString(this.dir.resolve("limits.yml"), yaml);
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
    }

    private PunishResult punish(final PunishmentType type, final UUID victim,
            final Actor actor) {
        return this.service.punish(DraftPunishment.builder(type, PlayerVictim.of(victim), actor)
                .build(), PunishOptions.builder().override(OverridePolicy.REPLACE).build())
                .join();
    }

    @Test
    void theRedoCooldownWaitsAfterLiftingOwnPunishmentOfThePlayer() throws Exception {
        this.writeLimitsYaml(
                """
                reduce-to-limit: true
                console-cooldown: 0s
                groups:
                  redo:
                    permission: sakurabans.group.redo
                    cooldown-redo: 10s
                """);
        this.mod.grant("sakurabans.group.redo");
        punishment(this.punish(PunishmentType.BAN, STEVE, MODERATOR));
        this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build())
                .join();

        Assertions.assertThat(reason(this.punish(PunishmentType.BAN, STEVE, MODERATOR)))
                .isEqualTo(PunishResult.Failure.Reason.COOLDOWN);
        // Another player is not affected, nor is another punishment type... of another player.
        Assertions.assertThat(this.punish(PunishmentType.BAN, ALEX, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);
        this.now.set(this.now.get().plus(Duration.ofSeconds(11)));
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);

        // Lifting it again, but with the bypass permission: no wait.
        this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build())
                .join();
        this.mod.grant(Permissions.COOLDOWN_BYPASS);
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void theRedoCooldownDefaultsToOneSecondAndOnlyFollowsOwnPunishments() throws Exception {
        this.writeLimitsYaml(
                """
                reduce-to-limit: true
                console-cooldown: 0s
                groups:
                  redo:
                    permission: sakurabans.group.redo
                """);
        this.mod.grant("sakurabans.group.redo");
        // Somebody else's ban, lifted by the moderator: no redo cooldown.
        this.punish(PunishmentType.BAN, STEVE, CONSOLE);
        this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build())
                .join();
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);

        this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build())
                .join();
        Assertions.assertThat(reason(this.punish(PunishmentType.BAN, STEVE, MODERATOR)))
                .isEqualTo(PunishResult.Failure.Reason.COOLDOWN);
        this.now.set(this.now.get().plusMillis(1_001));
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);
    }

    @Test
    void theConsoleCooldownProtectsOnePlayerFromTwoPunishmentsNotManyPlayers()
            throws Exception {
        this.writeLimitsYaml(
                """
                reduce-to-limit: true
                console-cooldown: 5s
                groups: {}
                """);

        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, CONSOLE))
                .isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(reason(this.punish(PunishmentType.BAN, STEVE, CONSOLE)))
                .isEqualTo(PunishResult.Failure.Reason.COOLDOWN);
        // Other players and other types go through.
        Assertions.assertThat(this.punish(PunishmentType.BAN, ALEX, CONSOLE))
                .isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(this.punish(PunishmentType.MUTE, STEVE, CONSOLE))
                .isInstanceOf(PunishResult.Success.class);
        // The moderator isn't the console.
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, MODERATOR))
                .isInstanceOf(PunishResult.Success.class);
        this.now.set(this.now.get().plusSeconds(6));
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, CONSOLE))
                .isInstanceOf(PunishResult.Success.class);

        this.writeLimitsYaml(
                """
                reduce-to-limit: true
                console-cooldown: 0s
                groups: {}
                """);
        Assertions.assertThat(this.punish(PunishmentType.BAN, STEVE, CONSOLE))
                .isInstanceOf(PunishResult.Success.class);
    }

    private static @Nullable Instant expiry(final Prepared prepared) {
        return ((Prepared.Ready) prepared).punishment().expiresAt();
    }
}
