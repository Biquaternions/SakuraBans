package net.serlith.sakurabans.core.enforce;

import java.util.List;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S67: wildcards in the mute blacklist, matched from the settings' compiled form. */
class ChatGateWildcardTest extends ServiceTest {

    private FakeOnlinePlayer steve;

    @Override
    protected String configYaml() {
        return
                """
                enforcement:
                  mute-command-blacklist: ['msg', '*:me', 'minecraft:*', 'e*', '/Pm']
                """;
    }

    @BeforeEach
    void mutedSteve() {
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.mutes.loaded(STEVE));
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), MODERATOR).reason("spam").build(),
                PunishOptions.defaults()).join();
    }

    private boolean blocked(final String line) {
        return this.chatGate.checkCommand(this.steve, line) instanceof ChatDecision.Denied;
    }

    @Test
    void wildcardEntriesBlockWhatTheyMatch() {
        for (final String line : List.of("/msg a", "/essentials:msg a", "/foo:me", "/bar:me x",
                "/minecraft:say hi", "/minecraft:tp", "/echo", "/essentials:anything", "/pm",
                "/PM x")) {
            Assertions.assertThat(this.blocked(line)).as(line).isTrue();
        }
        for (final String line : List.of("/me", "/spawn", "/home", "/foo:spawn", "/tell x")) {
            Assertions.assertThat(this.blocked(line)).as(line).isFalse();
        }
    }

    @Test
    void theSettingsKeepOneCompiledFormAcrossReads() {
        Assertions.assertThat(this.config.settings().enforcement().muteCommandBlacklist())
                .isSameAs(this.config.settings().enforcement().muteCommandBlacklist());
        Assertions.assertThat(this.config.settings().enforcement().muteCommandBlacklist()
                .entries()).containsExactlyInAnyOrder("msg", "*:me", "minecraft:*", "e*", "pm");
    }
}
