package net.serlith.sakurabans.core.message;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S73: {@code <total_*>} in every message, {@code <player_*>} where a message shows a
 * punishment, for online (cached) and offline (fetched when used) victims. The texts are this
 * test's own, so only the values are asserted.
 */
class CountTagsTest extends ServiceTest {

    private FakeOnlinePlayer staff;

    @BeforeEach
    void texts() throws Exception {
        final Path lang = this.dir.resolve("lang");
        Files.createDirectories(lang);
        Files.writeString(lang.resolve("en_US.yml"),
                """
                notify:
                  ban: '<victim>=<player_bans>/<player_mutes>/<player_warns>/<player_kicks>'
                  warn: '<victim> warned'
                screen:
                  ban: 'bans <player_bans> mutes <player_mutes>'
                check:
                  detail: '#<id> <player_bans>'
                error:
                  internal: 'totals <total_bans> <active_bans> <total_kicks>'
                """);
        this.messages.load();
        this.staff = this.platform.join(UUID.randomUUID(), "Staff", this.home)
                .grant(Permissions.notify(PunishmentType.BAN),
                        Permissions.notify(PunishmentType.WARN));
    }

    private Punishment punish(final PunishmentType type, final UUID victim,
            final boolean shadow) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type,
                PlayerVictim.of(victim), MODERATOR).shadow(shadow).build(),
                PunishOptions.builder().build()).join()).punishment();
    }

    private String lastNotice() {
        return plain(this.staff.messages().getLast());
    }

    @Test
    void totalsWorkInAnyMessageAndAreEmptyBeforeTheFirstRead() {
        Assertions.assertThat(plain(this.messages.render(MessageKey.ERROR_INTERNAL, Locale.US)))
                .isEqualTo("totals   ");
        this.punish(PunishmentType.BAN, ALEX, false);
        this.counts.refreshTotals().join();

        Assertions.assertThat(plain(this.messages.render(MessageKey.ERROR_INTERNAL, Locale.US)))
                .isEqualTo("totals 1 1 0");
    }

    @Test
    void anOnlineVictimsCountsComeFromTheCacheAndIncludeTheNewPunishment() {
        this.names.put(STEVE, "Steve");
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.counts.cached(STEVE).isPresent());
        this.platform.join(STEVE, "Steve", this.home);
        this.punish(PunishmentType.MUTE, STEVE, true);
        this.punish(PunishmentType.WARN, STEVE, false);

        this.punish(PunishmentType.BAN, STEVE, false);

        eventually(() -> this.lastNotice().startsWith("Steve="));
        Assertions.assertThat(this.lastNotice()).as("staff count shadow mutes")
                .isEqualTo("Steve=1/1/1/0");
    }

    @Test
    void anOfflineVictimsCountsAreReadWhenTheMessageUsesThem() {
        this.names.put(ALEX, "Alex");
        this.punish(PunishmentType.WARN, ALEX, false);
        this.punish(PunishmentType.BAN, ALEX, false);

        eventually(() -> this.lastNotice().startsWith("Alex="));
        Assertions.assertThat(this.lastNotice()).isEqualTo("Alex=1/0/1/0");
    }

    @Test
    void thePunishedPlayerDoesntSeeShadowMutesInTheirCount() {
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.counts.cached(STEVE).isPresent());
        this.platform.join(STEVE, "Steve", this.home);
        this.punish(PunishmentType.MUTE, STEVE, true);
        final Punishment ban = this.punish(PunishmentType.BAN, STEVE, false);

        Assertions.assertThat(plain(this.enforcementMessages.banScreen(ban, Locale.US)))
                .isEqualTo("bans 1 mutes 0");
    }

    @Test
    void screenBeforeCountsAreKnownLeavesThemEmpty() {
        final Punishment ban = this.punish(PunishmentType.BAN, ALEX, false);

        Assertions.assertThat(plain(this.enforcementMessages.banScreen(ban, Locale.US)))
                .isEqualTo("bans  mutes ");
    }

    @Test
    void repliesReadAnOfflineVictimsCountsForTheDetail() {
        final Punishment ban = this.punish(PunishmentType.BAN, ALEX, false);
        final CommandRunner runner =
                new CommandRunner(this.lifecycle, this.messages, this.log.logger());
        final Replies replies = new Replies(runner, this.placeholders, this.names, this.config,
                this.clock, this.messages, this.counts, this.delegatingCountries());
        final FakeCommandActor console = FakeCommandActor.theConsole();

        runner.send(console, MessageKey.CHECK_DETAIL, replies.punishment(console, ban));
        runner.send(console, MessageKey.CHECK_DETAIL,
                replies.punishmentCounted(console, ban, MessageKey.CHECK_DETAIL).join());

        Assertions.assertThat(console.texts()).containsExactly("#" + ban.displayId() + " ",
                "#" + ban.displayId() + " 1");
    }

    @Test
    void editorListsPlayerTagsWhereMessagesShowPunishments() {
        Assertions.assertThat(MessageKey.all().stream().map(MessageKey::key).toList())
                .as("every listed key exists")
                .containsAll(Placeholders.PLAYER_COUNT_KEYS);
        Assertions.assertThat(this.messageTemplates.schema()).filteredOn(field ->
                        field.key().equals("screen.ban")).singleElement()
                .satisfies(field -> Assertions.assertThat(field.placeholders())
                        .containsAll(Placeholders.PLAYER_COUNT_TAGS));
        Assertions.assertThat(this.messageTemplates.schema()).filteredOn(field ->
                        field.key().equals("error.internal")).singleElement()
                .satisfies(field -> Assertions.assertThat(field.placeholders())
                        .doesNotContainAnyElementsOf(Placeholders.PLAYER_COUNT_TAGS));
    }
}
