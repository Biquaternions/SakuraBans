package net.serlith.sakurabans.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SakuraPunishmentTest {

    private static final Instant CREATED = Instant.parse("2026-11-18T12:00:00Z");
    private static final Victim VICTIM = PlayerVictim.of(UUID.randomUUID());
    private static final Operator CONSOLE = ConsoleOperator.INSTANCE;
    private static final Actor ACTOR = Actor.of(CONSOLE, "console");

    private static SakuraPunishment punishment(final PunishmentType type,
            final @Nullable Instant expiresAt, final @Nullable PunishmentEnd end) {
        return punishment(1, "A1B2C3", type, "reason", expiresAt, end);
    }

    private static SakuraPunishment punishment(final long id, final String displayId,
            final PunishmentType type, final String reason, final @Nullable Instant expiresAt,
            final @Nullable PunishmentEnd end) {
        return new SakuraPunishment(id, displayId, type, false,
                VICTIM, CONSOLE, "console", GlobalScope.INSTANCE, reason, CREATED, expiresAt,
                null, Visibility.NORMAL, null, end, null,
                List.of(), 1);
    }

    private static PunishmentEnd revokedAt(final Instant at) {
        return new SakuraPunishmentEnd(EndKind.REVOKED, at, ACTOR, null);
    }

    @Test
    void temporaryPunishmentsAreActiveUntilTheyExpire() {
        final Instant expires = CREATED.plus(Duration.ofDays(1));
        final SakuraPunishment ban =
                punishment(PunishmentType.BAN, expires, null);

        assertThat(ban.permanent()).isFalse();
        assertThat(ban.isActiveAt(CREATED)).isTrue();
        assertThat(ban.isActiveAt(expires.minusMillis(1))).isTrue();
        assertThat(ban.isActiveAt(expires)).isFalse();
    }

    @Test
    void permanentPunishmentsStayActiveUntilTheyEnd() {
        final SakuraPunishment ban =
                punishment(PunishmentType.BAN, null, null);
        final SakuraPunishment revoked = punishment(
                PunishmentType.BAN, null, revokedAt(CREATED.plusSeconds(60)));

        assertThat(ban.permanent()).isTrue();
        assertThat(ban.isActiveAt(CREATED.plus(Duration.ofDays(9999)))).isTrue();
        assertThat(revoked.isActiveAt(CREATED)).isFalse();
    }

    @Test
    void kicksAreNeverActiveAndCantExpireOrEnd() {
        assertThat(punishment(PunishmentType.KICK, null, null)
                .isActiveAt(CREATED)).isFalse();
        assertThatThrownBy(() -> punishment(
                PunishmentType.KICK, CREATED.plusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(
                PunishmentType.KICK, null, revokedAt(CREATED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryMustBeAfterCreation() {
        assertThatThrownBy(() -> punishment(
                PunishmentType.MUTE, CREATED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(
                PunishmentType.MUTE, CREATED.minusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEndCantPrecedeCreation() {
        assertThatThrownBy(() -> punishment(
                PunishmentType.BAN, null, revokedAt(CREATED.minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idsAreValidatedAndReasonsAreLimited() {
        final PunishmentType ban = PunishmentType.BAN;
        final String tooLong = "x".repeat(Lengths.MAX_REASON + 1);

        assertThatThrownBy(() -> punishment(0, "X", ban, "", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(1, " ", ban, "", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(1, "X", ban, tooLong, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(punishment(1, "X", ban, "", null, null).reason()).isEmpty();
    }

    @Test
    void revocationsNeedAnOperatorButExpiriesDont() {
        assertThatThrownBy(() -> new SakuraPunishmentEnd(
                EndKind.REVOKED, CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SakuraPunishmentEnd(
                EndKind.EXPIRED, CREATED, null, null).actor())
                .isEmpty();
        assertThatThrownBy(() -> new SakuraPunishmentEnd(EndKind.REVOKED, CREATED,
                ACTOR, "x".repeat(Lengths.MAX_REASON + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyMutesAreShadow() {
        assertThat(punishment(PunishmentType.BAN, null, null).shadow()).isFalse();
        assertThatThrownBy(() -> new SakuraPunishment(1, "X", PunishmentType.BAN, true,
                VICTIM, CONSOLE, "console", GlobalScope.INSTANCE, "", CREATED,
                null, null, Visibility.NORMAL, null,
                null, null, List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEndByVoidingNeedsTheVoiding() {
        final PunishmentEnd voided = new SakuraPunishmentEnd(
                EndKind.VOIDED, CREATED, ACTOR, null);

        assertThatThrownBy(() -> punishment(PunishmentType.BAN, null, voided))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endsOtherThanExpiryNeedAnActor() {
        assertThatThrownBy(() -> new SakuraPunishmentEnd(
                EndKind.ROLLED_BACK, CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SakuraPunishmentEnd(
                EndKind.SUPERSEDED, CREATED, null, null).actor()).isEmpty();
    }
}
