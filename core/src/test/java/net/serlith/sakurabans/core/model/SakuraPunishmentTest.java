package net.serlith.sakurabans.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
import org.junit.jupiter.api.Test;

class SakuraPunishmentTest {

    private static final Instant CREATED = Instant.parse("2026-11-18T12:00:00Z");
    private static final Victim VICTIM = PlayerVictim.of(UUID.randomUUID());
    private static final Operator CONSOLE = ConsoleOperator.INSTANCE;

    private static SakuraPunishment punishment(final PunishmentType type,
            final Optional<Instant> expiresAt, final Optional<PunishmentEnd> end) {
        return punishment(1, "A1B2C3", type, "reason", expiresAt, end);
    }

    private static SakuraPunishment punishment(final long id, final String displayId,
            final PunishmentType type, final String reason, final Optional<Instant> expiresAt,
            final Optional<PunishmentEnd> end) {
        return new SakuraPunishment(id, displayId, type, VICTIM, CONSOLE, GlobalScope.INSTANCE,
                reason, CREATED, expiresAt, Optional.empty(), Visibility.NORMAL, Optional.empty(),
                end);
    }

    private static Optional<PunishmentEnd> revokedAt(final Instant at) {
        return Optional.of(new SakuraPunishmentEnd(
                EndKind.REVOKED, at, Optional.of(CONSOLE), Optional.empty()));
    }

    @Test
    void temporaryPunishmentsAreActiveUntilTheyExpire() {
        final Instant expires = CREATED.plus(Duration.ofDays(1));
        final SakuraPunishment ban =
                punishment(PunishmentType.BAN, Optional.of(expires), Optional.empty());

        assertThat(ban.permanent()).isFalse();
        assertThat(ban.isActiveAt(CREATED)).isTrue();
        assertThat(ban.isActiveAt(expires.minusMillis(1))).isTrue();
        assertThat(ban.isActiveAt(expires)).isFalse();
    }

    @Test
    void permanentPunishmentsStayActiveUntilTheyEnd() {
        final SakuraPunishment ban =
                punishment(PunishmentType.BAN, Optional.empty(), Optional.empty());
        final SakuraPunishment revoked = punishment(
                PunishmentType.BAN, Optional.empty(), revokedAt(CREATED.plusSeconds(60)));

        assertThat(ban.permanent()).isTrue();
        assertThat(ban.isActiveAt(CREATED.plus(Duration.ofDays(9999)))).isTrue();
        assertThat(revoked.isActiveAt(CREATED)).isFalse();
    }

    @Test
    void kicksAreNeverActiveAndCantExpireOrEnd() {
        assertThat(punishment(PunishmentType.KICK, Optional.empty(), Optional.empty())
                .isActiveAt(CREATED)).isFalse();
        assertThatThrownBy(() -> punishment(
                PunishmentType.KICK, Optional.of(CREATED.plusSeconds(1)), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(
                PunishmentType.KICK, Optional.empty(), revokedAt(CREATED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryMustBeAfterCreation() {
        assertThatThrownBy(() -> punishment(
                PunishmentType.MUTE, Optional.of(CREATED), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(
                PunishmentType.MUTE, Optional.of(CREATED.minusSeconds(1)), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEndCantPrecedeCreation() {
        assertThatThrownBy(() -> punishment(
                PunishmentType.BAN, Optional.empty(), revokedAt(CREATED.minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idsAreValidatedAndReasonsAreLimited() {
        final Optional<Instant> none = Optional.empty();
        final PunishmentType ban = PunishmentType.BAN;
        final String tooLong = "x".repeat(Lengths.MAX_REASON + 1);

        assertThatThrownBy(() -> punishment(0, "X", ban, "", none, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(1, " ", ban, "", none, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> punishment(1, "X", ban, tooLong, none, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(punishment(1, "X", ban, "", none, Optional.empty()).reason()).isEmpty();
    }

    @Test
    void revocationsNeedAnOperatorButExpiriesDont() {
        assertThatThrownBy(() -> new SakuraPunishmentEnd(
                EndKind.REVOKED, CREATED, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SakuraPunishmentEnd(
                EndKind.EXPIRED, CREATED, Optional.empty(), Optional.empty()).operator())
                .isEmpty();
        assertThatThrownBy(() -> new SakuraPunishmentEnd(EndKind.REVOKED, CREATED,
                Optional.of(CONSOLE), Optional.of("x".repeat(Lengths.MAX_REASON + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
