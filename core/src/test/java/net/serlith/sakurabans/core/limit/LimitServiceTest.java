package net.serlith.sakurabans.core.limit;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Limit groups, reduce-to-limit, cooldowns and require-template (plan S32). */
class LimitServiceTest extends ServiceTest {

    private FakeOnlinePlayer mod;

    @BeforeEach
    void limits() throws Exception {
        this.writeLimits(true);
        this.mod = this.platform.join(MOD, "Mod", this.home);
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
        this.templates.create(TemplateDefinition.builder("hacking", "ban").reason("Hacking")
                .ladder(List.of(TemplateStep.of(Duration.ofDays(30)))).build(), CONSOLE).join();

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
}
