package net.serlith.sakurabans.api.punish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import org.junit.jupiter.api.Test;

class RevocationAndEditTest {

    @Test
    void idTargetsArePositive() {
        assertThat(RevocationTarget.ById.of(7).id()).isEqualTo(7);
        assertThatThrownBy(() -> RevocationTarget.ById.of(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void victimTargetsCoverEveryScopeWithoutOne() {
        final RevocationTarget.ByVictim target = RevocationTarget.ByVictim.of(
                PunishmentType.MUTE, PlayerVictim.of(UUID.randomUUID()), null);

        assertThat(target.scope()).isEmpty();
    }

    @Test
    void kicksCantBeRevoked() {
        assertThatThrownBy(() -> RevocationTarget.ByVictim.of(
                PunishmentType.KICK, PlayerVictim.of(UUID.randomUUID()), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestsHaveDefaultsAndCopy() {
        final RevocationRequest request = RevocationRequest.builder(
                RevocationTarget.ById.of(3), ConsoleOperator.INSTANCE).reason("appeal").build();

        assertThat(request.visibility()).isEqualTo(Visibility.NORMAL);
        assertThat(request.ownOnly()).isFalse();
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThatThrownBy(() -> request.toBuilder().reason("x".repeat(Lengths.MAX_REASON + 1))
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editsCarryOneChange() {
        final Instant later = Instant.parse("2030-01-01T00:00:00Z");

        assertThat(PunishmentEdit.NewReason.of("new").reason()).isEqualTo("new");
        assertThat(PunishmentEdit.NewExpiry.of(later).expiresAt()).contains(later);
        assertThat(PunishmentEdit.NewExpiry.of(null).expiresAt()).isEmpty();
        assertThatThrownBy(() -> PunishmentEdit.NewReason.of("x".repeat(Lengths.MAX_REASON + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
