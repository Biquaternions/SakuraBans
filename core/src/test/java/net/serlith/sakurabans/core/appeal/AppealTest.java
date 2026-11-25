package net.serlith.sakurabans.core.appeal;

import java.time.Duration;
import java.util.Set;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.appeal.AppealQuery;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Appeals: submitting and deciding (plan S37d). */
class AppealTest extends ServiceTest {

    private static <T> T ok(final AdminResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<T>) result).value();
    }

    private static String invalidField(final AdminResult<?> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.ValidationFailed.class);
        return ((AdminResult.ValidationFailed<?>) result).errors().stream()
                .map(FieldError::path).findFirst().orElseThrow();
    }

    private Punishment ban() {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR)
                .duration(Duration.ofDays(7)).build(), PunishOptions.defaults()).join())
                .punishment();
    }

    @Test
    void onlyThePunishedPlayerAppealsOncePerPunishment() {
        final FakeOnlinePlayer staff = this.platform.join(MOD, "Mod", this.home)
                .grant(Permissions.APPEALS_VIEW);
        final Punishment ban = this.ban();

        Assertions.assertThat(invalidField(this.appeals.submit(ban.id(), ALEX, "not me").join()))
                .isEqualTo("appellant");
        Assertions.assertThat(invalidField(this.appeals.submit(ban.id(), STEVE, " ").join()))
                .isEqualTo("message");
        Assertions.assertThat(this.appeals.submit(999, STEVE, "x").join())
                .isInstanceOf(AdminResult.NotFound.class);
        final Appeal appeal = ok(this.appeals.submit(ban.id(), STEVE, "I was AFK").join());
        Assertions.assertThat(appeal.status()).isEqualTo(Appeal.Status.OPEN);
        Assertions.assertThat(appeal.decision()).isEmpty();
        Assertions.assertThat(invalidField(this.appeals.submit(ban.id(), STEVE, "again")
                .join())).isEqualTo("punishmentId");

        eventually(() -> !staff.messages().isEmpty());
        Assertions.assertThat(plain(staff.messages().getLast())).contains("appealed",
                "I was AFK");
        Assertions.assertThat(this.events(EntityChangedEvent.AppealChanged.class)).hasSize(1);
        Assertions.assertThat(this.appeals.appeals(AppealQuery.builder()
                .statuses(Set.of(Appeal.Status.OPEN)).build()).join().items())
                .extracting(Appeal::id).containsExactly(appeal.id());
    }

    @Test
    void acceptingRevokesVoidingVoidsRejectingKeeps() {
        final Punishment ban = this.ban();
        // Back on the server (the ban kicked him), to hear the decision.
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        final Appeal appeal = ok(this.appeals.submit(ban.id(), STEVE, "sorry").join());

        Assertions.assertThat(this.appeals.decide(appeal.id(), AppealOutcome.REVOKE, null, 7,
                MODERATOR).join()).isInstanceOf(AdminResult.Conflict.class);
        final Appeal accepted = ok(this.appeals.decide(appeal.id(), AppealOutcome.REVOKE,
                "second chance", appeal.version(), MODERATOR).join());
        Assertions.assertThat(accepted.status()).isEqualTo(Appeal.Status.ACCEPTED);
        Assertions.assertThat(accepted.decision()).hasValueSatisfying(decision -> {
            Assertions.assertThat(decision.outcome()).isEqualTo(AppealOutcome.REVOKE);
            Assertions.assertThat(decision.note()).contains("second chance");
            Assertions.assertThat(decision.actor()).isEqualTo(MODERATOR);
        });
        Assertions.assertThat(this.service.byId(ban.id()).join().orElseThrow().end())
                .hasValueSatisfying(end -> Assertions.assertThat(end.kind())
                        .isEqualTo(EndKind.REVOKED));
        Assertions.assertThat(steve.messages()).map(ServiceTest::plain)
                .anySatisfy(text -> Assertions.assertThat(text).contains("accepted"));
        Assertions.assertThat(invalidField(this.appeals.decide(appeal.id(),
                AppealOutcome.REJECT, null, accepted.version(), MODERATOR).join()))
                .isEqualTo("status");

        final Punishment second = this.ban();
        final Appeal voiding = ok(this.appeals.submit(second.id(), STEVE, "wrong").join());
        ok(this.appeals.decide(voiding.id(), AppealOutcome.VOID, null, 1, MODERATOR).join());
        Assertions.assertThat(this.service.byId(second.id()).join().orElseThrow().voiding())
                .isPresent();
        Assertions.assertThat(invalidField(this.appeals.submit(second.id(), STEVE, "again")
                .join())).isEqualTo("punishmentId");
    }

    @Test
    void decisionsNeedPermissionAndTheServicesConsent() {
        final Punishment ban = this.ban();
        final Appeal appeal = ok(this.appeals.submit(ban.id(), STEVE, "please").join());
        this.actionCheck = (actor, action) -> java.util.concurrent.CompletableFuture
                .completedFuture(Decision.deny(Decision.Denial.NO_PERMISSION, null));
        Assertions.assertThat(this.appeals.decide(appeal.id(), AppealOutcome.REJECT, null, 1,
                MODERATOR).join()).isInstanceOf(AdminResult.Forbidden.class);
        Assertions.assertThat(this.appeals.appeal(appeal.id()).join().orElseThrow().status())
                .isEqualTo(Appeal.Status.OPEN);
    }
}
