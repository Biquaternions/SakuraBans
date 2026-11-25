package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WarningsTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return
                """
                warnings:
                  expire-after: 7d
                  actions:
                    2: ["tempmute {victim} 1h {count} warnings, last by {operator}"]
                    3: ["tempban {victim} 1d {count} warnings", "say {uuid} reached 3"]
                """;
    }

    private Punishment warn(final String reason) {
        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.WARN, PlayerVictim.of(STEVE), MODERATOR).reason(reason).build(),
                PunishOptions.defaults()).join();
        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        return ((PunishResult.Success) result).punishment();
    }

    @Test
    void warningsExpireAfterTheConfiguredTimeUnlessGivenOne() {
        final Punishment usual = this.warn("spam");
        Assertions.assertThat(usual.expiresAt()).contains(usual.createdAt().plus(
                Duration.ofDays(7)));
        final PunishResult longer = this.service.punish(DraftPunishment.builder(
                PunishmentType.WARN, PlayerVictim.of(STEVE), MODERATOR)
                .duration(Duration.ofDays(30)).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(((PunishResult.Success) longer).punishment().expiresAt())
                .contains(usual.createdAt().plus(Duration.ofDays(30)));

        this.now.set(this.now.get().plus(Duration.ofDays(8)));
        this.sweeper.sweep().join();
        Assertions.assertThat(this.service.byId(usual.id()).join().orElseThrow().end())
                .hasValueSatisfying(end -> Assertions.assertThat(end.kind())
                        .isEqualTo(EndKind.EXPIRED));
        Assertions.assertThat(this.punishments.activeWarnings(STEVE, Long.MAX_VALUE,
                this.clock.instant()).join()).isEqualTo(1);
    }

    @Test
    void reachingThresholdsRunsTheirCommandsOnce() {
        this.names.put(STEVE, "Steve");
        this.warn("one");
        Assertions.assertThat(this.platform.consoleCommands()).isEmpty();

        this.warn("two");
        eventually(() -> this.platform.consoleCommands().size() == 1);
        Assertions.assertThat(this.platform.consoleCommands())
                .containsExactly("tempmute Steve 1h 2 warnings, last by Mod");

        this.warn("three");
        eventually(() -> this.platform.consoleCommands().size() == 3);
        Assertions.assertThat(this.platform.consoleCommands()).endsWith(
                "tempban Steve 1d 3 warnings", "say " + STEVE + " reached 3");

        // A fourth has no action, and warnings of other victims don't count.
        this.warn("four");
        this.service.punish(DraftPunishment.builder(PunishmentType.WARN,
                AddressVictim.of(this.home), MODERATOR).build(), PunishOptions.defaults())
                .join();
        Assertions.assertThat(this.platform.consoleCommands()).hasSize(3);
    }

    @Test
    void warningsFromOtherServersRunNoActionsHere() {
        this.warn("one");
        final Punishment second = this.warn("two");
        eventually(() -> this.platform.consoleCommands().size() == 1);

        this.bus.post(new SakuraPunishmentCreatedEvent(second, null, true));
        Assertions.assertThat(this.platform.consoleCommands()).hasSize(1);
    }

    @Test
    void playersSeeWarningsTheyGotWhileAwayOnce() {
        final Punishment first = this.warn("griefing");
        final Punishment second = this.warn("spam");

        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.loginGate.joined(STEVE);
        eventually(() -> steve.messages().size() == 2);
        Assertions.assertThat(steve.messages()).map(ServiceTest::plain).satisfiesExactly(
                message -> Assertions.assertThat(message).contains("While you were away",
                        "Mod", "griefing"),
                message -> Assertions.assertThat(message).contains("spam"));

        this.loginGate.joined(STEVE);
        // Online now: a new warning is shown right away and not again at the next join.
        this.warn("language");
        Assertions.assertThat(steve.messages()).hasSize(3);
        Assertions.assertThat(plain(steve.messages().getLast()))
                .contains("You were warned", "language");
        this.loginGate.joined(STEVE);
        Assertions.assertThat(this.punishments.unacknowledgedWarnings(STEVE,
                this.clock.instant()).join()).isEmpty();
        Assertions.assertThat(steve.messages()).hasSize(3);
        Assertions.assertThat(this.punishments.acknowledge(first.id()).join()).isFalse();
        Assertions.assertThat(this.punishments.acknowledge(second.id()).join()).isFalse();
    }
}
