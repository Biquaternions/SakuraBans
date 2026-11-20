package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.event.PunishmentExpiredEvent;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatGateTest extends ServiceTest {

    private FakeOnlinePlayer steve;

    @BeforeEach
    void join() {
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        this.steve = this.platform.join(STEVE, "Steve", this.home);
    }

    private Punishment mute(final DraftPunishment.Builder draft) {
        return ((PunishResult.Success) this.service.punish(draft.build(),
                PunishOptions.defaults()).join()).punishment();
    }

    private DraftPunishment.Builder muteDraft() {
        return DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR)
                .reason("spam");
    }

    @Test
    void unmutedPlayersChat() {
        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.chatGate.checkCommand(this.steve, "/msg Alex hi"))
                .isInstanceOf(ChatDecision.Allowed.class);
    }

    @Test
    void mutedPlayersAreDeniedAndStaffAreToldOnce() {
        final FakeOnlinePlayer staff = this.platform.join(MOD, "Mod",
                InetAddresses.parseLiteral("198.51.100.1").orElseThrow())
                .grant(Permissions.NOTIFY_ATTEMPT_CHAT);
        this.mute(this.muteDraft());
        final int before = staff.messages().size();

        Assertions.assertThat(this.chatGate.checkChat(this.steve, "buy cheap gold"))
                .isInstanceOfSatisfying(ChatDecision.Denied.class, denied ->
                        Assertions.assertThat(plain(denied.message())).contains("spam"));
        this.chatGate.checkChat(this.steve, "again");

        Assertions.assertThat(staff.messages()).hasSize(before + 1);
        Assertions.assertThat(plain(staff.messages().getLast()))
                .contains("Steve", "buy cheap gold");
        Assertions.assertThat(this.events(PlayerDenyEvent.class)).hasSize(2)
                .allSatisfy(event -> Assertions.assertThat(event.denial())
                        .isEqualTo(PlayerDenyEvent.Denial.CHAT_MUTED));

        this.now.set(this.now.get().plus(Duration.ofSeconds(30)));
        this.chatGate.checkChat(this.steve, "third");
        Assertions.assertThat(staff.messages()).hasSize(before + 2);
    }

    @Test
    void blacklistedCommandsAreBlockedEvenNamespaced() {
        this.mute(this.muteDraft());

        Assertions.assertThat(this.chatGate.checkCommand(this.steve, "/MSG Alex hi"))
                .isInstanceOf(ChatDecision.Denied.class);
        Assertions.assertThat(this.chatGate.checkCommand(this.steve, "essentials:msg Alex hi"))
                .isInstanceOf(ChatDecision.Denied.class);
        Assertions.assertThat(this.chatGate.checkCommand(this.steve, "/spawn"))
                .isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.events(PlayerDenyEvent.class))
                .allSatisfy(event -> Assertions.assertThat(event.denial())
                        .isEqualTo(PlayerDenyEvent.Denial.COMMAND_MUTED));
    }

    @Test
    void shadowMutesShowTheMessageToTheSenderOnly() {
        this.mute(this.muteDraft().kind("shadow-mute"));

        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOf(ChatDecision.ShadowMuted.class);
        Assertions.assertThat(this.events(PlayerDenyEvent.class)).singleElement()
                .satisfies(event -> {
                    Assertions.assertThat(event.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.CHAT_SHADOW_MUTED);
                    Assertions.assertThat(plain(event.message())).isEmpty();
                });
    }

    @Test
    void denyHandlersCanLetTheMessageThrough() {
        this.mute(this.muteDraft());
        this.bus.subscribe(PlayerDenyEvent.class, event -> event.cancelled(true));

        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOf(ChatDecision.Allowed.class);
    }

    @Test
    void playersNotLoadedYetAreAskedToRetryWhileTheyLoad() {
        this.mute(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(ALEX), MODERATOR));
        final FakeOnlinePlayer alex = this.platform.join(ALEX, "Alex", this.home);

        Assertions.assertThat(this.chatGate.checkChat(alex, "hi"))
                .isInstanceOfSatisfying(ChatDecision.Denied.class, denied ->
                        Assertions.assertThat(plain(denied.message())).contains("Try again"));
        this.mutes.load(ALEX, this.home).join();
        Assertions.assertThat(this.chatGate.checkChat(alex, "hi"))
                .isInstanceOf(ChatDecision.Denied.class);
        Assertions.assertThat(this.mutes.activeMute(ALEX)).isPresent();
    }

    @Test
    void mutesExpireWhileThePlayerIsOnline() {
        final Punishment mute = this.mute(this.muteDraft().duration(Duration.ofMinutes(5)));
        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOf(ChatDecision.Denied.class);

        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));

        Assertions.assertThat(this.chatGate.checkChat(this.steve, "hi"))
                .isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.sweeper.sweep().join()).isOne();
        Assertions.assertThat(this.service.byId(mute.id()).join().orElseThrow().end())
                .hasValueSatisfying(end -> {
                    Assertions.assertThat(end.kind()).isEqualTo(EndKind.EXPIRED);
                    Assertions.assertThat(end.at()).isEqualTo(mute.expiresAt().orElseThrow());
                });
        Assertions.assertThat(this.events(PunishmentExpiredEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.punishment().id())
                        .isEqualTo(mute.id()));
    }
}
