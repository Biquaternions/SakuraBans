package net.serlith.sakurabans.api.punish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import org.junit.jupiter.api.Test;

class DraftPunishmentTest {

    private static final Victim VICTIM = PlayerVictim.of(UUID.randomUUID());
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private static DraftPunishment.Builder ban() {
        return DraftPunishment.builder(PunishmentType.BAN, VICTIM, CONSOLE);
    }

    @Test
    void unsetValuesAreLeftToTheConfiguration() {
        final DraftPunishment draft = ban().build();

        assertThat(draft.scope()).isEmpty();
        assertThat(draft.reason()).isEmpty();
        assertThat(draft.duration()).isEmpty();
        assertThat(draft.template()).isEmpty();
        assertThat(draft.kind()).isEmpty();
        assertThat(draft.evidence()).isEmpty();
    }

    @Test
    void toBuilderCopiesEveryValue() {
        final DraftPunishment draft = ban().scope(ServerScope.of("lobby")).reason("spam")
                .duration(Duration.ofDays(1)).kind("ip-ban")
                .evidence(List.of(EvidenceRef.of("hydra:report:7"))).build();

        assertThat(draft.toBuilder().build()).isEqualTo(draft);
        assertThat(draft.toBuilder().permanent().build().duration()).isEmpty();
    }

    @Test
    void durationsMustBePositive() {
        assertThatThrownBy(() -> ban().duration(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ban().duration(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anErasedAddressCantBePunishedAgain() {
        final DraftPunishment.Builder builder = DraftPunishment.builder(PunishmentType.BAN,
                ErasedAddressVictim.of("0".repeat(64), null), CONSOLE);

        assertThat(builder.validate()).extracting(FieldError::path, FieldError::code)
                .containsExactly(tuple("victim", Code.NOT_ALLOWED));
    }

    @Test
    void kicksHaveNoDuration() {
        assertThatThrownBy(() -> ban().type(PunishmentType.KICK).duration(Duration.ofHours(1))
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void templatesDecideTheDurationAndAreNormalised() {
        assertThat(ban().template("Hacking").build().template()).contains("hacking");
        assertThatThrownBy(() -> ban().template("hacking").duration(Duration.ofDays(1)).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ban().template("two words").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateReportsEveryProblem() {
        final List<FieldError> errors = ban().type(PunishmentType.KICK)
                .reason("x".repeat(Lengths.MAX_REASON + 1))
                .kind("Not A Kind")
                .duration(Duration.ofHours(1))
                .validate();

        assertThat(errors).extracting(FieldError::path, FieldError::code).containsExactlyInAnyOrder(
                tuple("reason", Code.TOO_LONG),
                tuple("kind", Code.INVALID_IDENTIFIER),
                tuple("duration", Code.NOT_ALLOWED));
        assertThat(ban().validate()).isEmpty();
    }

    @Test
    void kindsAreNormalisedAndEvidenceDeduplicated() {
        final EvidenceRef report = EvidenceRef.of("hydra:report:7");
        final DraftPunishment draft = ban().kind("Blacklist")
                .evidence(List.of(report, EvidenceRef.of("hydra:report:7"))).build();

        assertThat(draft.kind()).contains("blacklist");
        assertThat(draft.evidence()).containsExactly(report);
    }

    @Test
    void reasonsAreLimited() {
        assertThatThrownBy(() -> ban().reason("x".repeat(Lengths.MAX_REASON + 1)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionsDefaultToTheSafeChoice() {
        final PunishOptions defaults = PunishOptions.defaults();

        assertThat(defaults.visibility()).isEqualTo(Visibility.NORMAL);
        assertThat(defaults.override()).isEqualTo(OverridePolicy.REJECT);
        assertThat(defaults.enforcement()).isEqualTo(Enforcement.GLOBAL);
        assertThat(defaults.bypassLimits()).isFalse();
        assertThat(defaults.toBuilder().visibility(Visibility.SILENT).build().visibility())
                .isEqualTo(Visibility.SILENT);
    }
}
