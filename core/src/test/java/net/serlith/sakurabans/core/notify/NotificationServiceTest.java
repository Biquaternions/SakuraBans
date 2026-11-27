package net.serlith.sakurabans.core.notify;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest extends ServiceTest {

    private FakeOnlinePlayer normalStaff;
    private FakeOnlinePlayer silentStaff;
    private FakeOnlinePlayer revokeStaff;
    private FakeOnlinePlayer player;

    private FakeOnlinePlayer join(final String name, final String... permissions) {
        final InetAddress address = InetAddresses.parseLiteral("198.51.100.1").orElseThrow();
        return this.platform.join(UUID.randomUUID(), name, address).grant(permissions);
    }

    @BeforeEach
    void staff() {
        this.normalStaff = this.join("Normal", Permissions.notify(PunishmentType.BAN));
        this.silentStaff = this.join("Silent", Permissions.notify(PunishmentType.BAN),
                Permissions.notifySilent(PunishmentType.BAN));
        this.revokeStaff = this.join("Revoke", Permissions.notifyRevoke(PunishmentType.BAN));
        this.player = this.join("Player");
        this.names.put(STEVE, "Steve");
    }

    private Punishment ban(final Visibility visibility) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).reason("xray")
                .build(), PunishOptions.builder().visibility(visibility).build()).join())
                .punishment();
    }

    private void revoke(final Punishment punishment, final Visibility visibility) {
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(punishment.id()),
                MODERATOR).visibility(visibility).build()).join();
    }

    private static List<String> texts(final FakeOnlinePlayer player) {
        return player.messages().stream().map(ServiceTest::plain).toList();
    }

    private List<String> console() {
        return this.platform.consoleMessages().stream().map(ServiceTest::plain).toList();
    }

    @Test
    void normalPunishmentsReachNotifyHolders() {
        this.ban(Visibility.NORMAL);

        Assertions.assertThat(texts(this.normalStaff)).singleElement().satisfies(text ->
                Assertions.assertThat(text).contains("Mod", "banned", "Steve", "xray")
                        .doesNotContain("[Silent]"));
        Assertions.assertThat(texts(this.silentStaff)).hasSize(1);
        Assertions.assertThat(this.revokeStaff.messages()).isEmpty();
        Assertions.assertThat(this.player.messages()).isEmpty();
        Assertions.assertThat(this.console()).hasSize(1);
    }

    @Test
    void silentPunishmentsReachSilentHoldersOnlyTagged() {
        this.ban(Visibility.SILENT);

        Assertions.assertThat(this.normalStaff.messages()).isEmpty();
        Assertions.assertThat(texts(this.silentStaff)).singleElement()
                .satisfies(text -> Assertions.assertThat(text)
                        .startsWith("SakuraBans ...🖉 [Silent]"));
        Assertions.assertThat(this.console()).singleElement()
                .satisfies(text -> Assertions.assertThat(text).contains("[Silent]"));
    }

    @Test
    void hiddenPunishmentsReachTheConsoleOnly() {
        this.ban(Visibility.HIDDEN);

        Assertions.assertThat(this.normalStaff.messages()).isEmpty();
        Assertions.assertThat(this.silentStaff.messages()).isEmpty();
        Assertions.assertThat(this.console()).hasSize(1);
    }

    @Test
    void quietPunishmentsAndRevocationsReachNobodyNotEvenTheConsole() {
        final Punishment ban = this.ban(Visibility.QUIET);

        this.revoke(ban, Visibility.QUIET);
        // Quiet to the end: a loud revocation of a quiet ban is still the revoker's choice.
        final Punishment other = this.ban(Visibility.QUIET);
        Assertions.assertThat(this.normalStaff.messages()).isEmpty();
        Assertions.assertThat(this.silentStaff.messages()).isEmpty();
        Assertions.assertThat(this.revokeStaff.messages()).isEmpty();
        Assertions.assertThat(this.player.messages()).isEmpty();
        Assertions.assertThat(this.console()).isEmpty();
        // A remote instance enforcing it announces nothing either.
        this.notifications.created(other);
        Assertions.assertThat(this.console()).isEmpty();
        Assertions.assertThat(other.visibility()).isEqualTo(Visibility.QUIET);
    }

    @Test
    void revocationsFollowTheirOwnVisibility() {
        final Punishment ban = this.ban(Visibility.HIDDEN);
        this.revoke(ban, Visibility.NORMAL);

        Assertions.assertThat(texts(this.revokeStaff)).singleElement()
                .satisfies(text -> Assertions.assertThat(text)
                        .contains("Mod", "unbanned", "Steve"));
        Assertions.assertThat(this.normalStaff.messages()).isEmpty();

        final FakeOnlinePlayer both = this.join("Both",
                Permissions.notifyRevoke(PunishmentType.BAN),
                Permissions.notifySilent(PunishmentType.BAN));
        this.revoke(this.ban(Visibility.NORMAL), Visibility.SILENT);

        Assertions.assertThat(texts(this.revokeStaff)).hasSize(1);
        Assertions.assertThat(texts(both)).singleElement()
                .satisfies(text -> Assertions.assertThat(text).contains("[Silent]", "unbanned"));
    }

    @Test
    void permissionsArePerType() {
        this.service.punish(DraftPunishment.builder(PunishmentType.WARN, PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();

        Assertions.assertThat(this.normalStaff.messages()).isEmpty();
        Assertions.assertThat(this.console()).singleElement()
                .satisfies(text -> Assertions.assertThat(text).contains("warned"));
    }

    @Test
    void attemptNoticesAreThrottledPerPlayer() {
        final FakeOnlinePlayer watcher = this.join("Watcher", Permissions.NOTIFY_ATTEMPT_JOIN);
        final Punishment ban = this.ban(Visibility.HIDDEN);

        this.notifications.attemptedJoin("Steve", ban, null);
        this.notifications.attemptedJoin("STEVE", ban, null);
        this.notifications.attemptedJoin("Alex", ban, null);

        Assertions.assertThat(texts(watcher)).hasSize(2);
        Assertions.assertThat(watcher.messages()).doesNotContain(Component.empty());
    }
}
