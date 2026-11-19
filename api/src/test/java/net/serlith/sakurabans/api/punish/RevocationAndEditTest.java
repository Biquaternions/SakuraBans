package net.serlith.sakurabans.api.punish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.junit.jupiter.api.Test;

class RevocationAndEditTest {

    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

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
                RevocationTarget.ById.of(3), CONSOLE).reason("appeal").build();

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
                .isInstanceOf(ValidationException.class);
        assertThat(PunishmentEdit.NewScope.of(GlobalScope.INSTANCE).scope())
                .isEqualTo(GlobalScope.INSTANCE);
    }

    @Test
    void editRequestsCarryAnExpectedVersion() {
        final EditRequest request = EditRequest.builder(5, PunishmentEdit.NewReason.of("x"),
                CONSOLE).expectedVersion(3).build();

        assertThat(request.expectedVersion()).hasValue(3);
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(EditRequest.builder(0, PunishmentEdit.NewReason.of("x"), CONSOLE)
                .expectedVersion(0).validate()).extracting(FieldError::path)
                .containsExactlyInAnyOrder("id", "expectedVersion");
    }

    @Test
    void rollbacksHaveDefaults() {
        final RollbackRequest request = RollbackRequest.builder(
                PlayerOperator.of(UUID.randomUUID(), "Mod"), Instant.EPOCH, CONSOLE).build();

        assertThat(request.visibility()).isEqualTo(Visibility.NORMAL);
        assertThat(request.reason()).isEmpty();
        assertThat(request.toBuilder().build()).isEqualTo(request);
    }
}
