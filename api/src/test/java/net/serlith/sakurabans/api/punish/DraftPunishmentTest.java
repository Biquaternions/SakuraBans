package net.serlith.sakurabans.api.punish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import org.junit.jupiter.api.Test;

class DraftPunishmentTest {

    private static final Victim VICTIM = PlayerVictim.of(UUID.randomUUID());

    private static DraftPunishment.Builder ban() {
        return DraftPunishment.builder(PunishmentType.BAN, VICTIM, ConsoleOperator.INSTANCE);
    }

    @Test
    void unsetValuesAreLeftToTheConfiguration() {
        final DraftPunishment draft = ban().build();

        assertThat(draft.scope()).isEmpty();
        assertThat(draft.reason()).isEmpty();
        assertThat(draft.duration()).isEmpty();
        assertThat(draft.template()).isEmpty();
    }

    @Test
    void toBuilderCopiesEveryValue() {
        final DraftPunishment draft = ban().scope(ServerScope.of("lobby")).reason("spam")
                .duration(Duration.ofDays(1)).build();

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
