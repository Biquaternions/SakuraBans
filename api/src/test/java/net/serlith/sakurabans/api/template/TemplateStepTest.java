package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TemplateStepTest {

    @Test
    void durationsArePositiveAndComparable() {
        Assertions.assertThat(TemplateDuration.fixed(Duration.ofHours(1)).fixedLength())
                .contains(Duration.ofHours(1));
        Assertions.assertThat(TemplateDuration.permanent().fixedLength()).isEmpty();
        Assertions.assertThat(TemplateDuration.ofNullable(null))
                .isInstanceOf(TemplateDuration.Permanent.class)
                .isEqualTo(TemplateDuration.permanent());
        Assertions.assertThat(TemplateDuration.fixed(Duration.ofDays(1)))
                .isEqualTo(TemplateDuration.fixed(Duration.ofDays(1)));
        Assertions.assertThatThrownBy(() -> TemplateDuration.fixed(Duration.ZERO))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void fixedDurationsAreWholeSecondsOfAtLeastOne() {
        Assertions.assertThat(TemplateDuration.fixed(Duration.ofSeconds(1)).fixedLength())
                .contains(Duration.ofSeconds(1));
        Assertions.assertThatThrownBy(() -> TemplateDuration.fixed(Duration.ofMillis(500)))
                .isInstanceOfSatisfying(ValidationException.class, e -> Assertions
                        .assertThat(e.errors()).singleElement().satisfies(error -> {
                            Assertions.assertThat(error.path()).isEqualTo("duration");
                            Assertions.assertThat(error.message())
                                    .isEqualTo("must be at least one second");
                        }));
        Assertions.assertThatThrownBy(() -> TemplateDuration.fixed(Duration.ofMillis(1500)))
                .isInstanceOfSatisfying(ValidationException.class, e -> Assertions
                        .assertThat(e.errors()).singleElement().satisfies(error -> Assertions
                                .assertThat(error.message()).isEqualTo("must be whole seconds")));
        Assertions.assertThatThrownBy(() -> TemplateStep.of(PunishmentType.MUTE,
                        TemplateDuration.ofNullable(Duration.ofMillis(90_500))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void durationsSuitTheStepsType() {
        Assertions.assertThat(TemplateStep.of(PunishmentType.BAN, TemplateDuration.permanent())
                .type()).contains(PunishmentType.BAN);
        Assertions.assertThat(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())
                .duration()).isInstanceOf(TemplateDuration.Open.class);
        Assertions.assertThat(TemplateStep.of(PunishmentType.KICK, TemplateDuration.open())
                .duration().fixedLength()).isEmpty();
        Assertions.assertThatThrownBy(() -> TemplateStep.of(PunishmentType.KICK,
                TemplateDuration.permanent())).isInstanceOf(ValidationException.class);
        Assertions.assertThatThrownBy(() -> TemplateStep.of(PunishmentType.MUTE,
                TemplateDuration.open())).isInstanceOf(ValidationException.class);
    }

    @Test
    void stepsWithoutPunishmentOnlyRunActions() {
        final TemplateStep none = TemplateStep.none(List.of("/say hi"));
        Assertions.assertThat(none.type()).isEmpty();
        Assertions.assertThat(none.actions()).containsExactly("say hi");
        Assertions.assertThatThrownBy(() -> TemplateStep.none(List.of()))
                .isInstanceOf(ValidationException.class);
        Assertions.assertThatThrownBy(() -> TemplateStep.of(null,
                TemplateDuration.fixed(Duration.ofHours(1)), null, null, List.of("say hi")))
                .isInstanceOf(ValidationException.class);
        Assertions.assertThatThrownBy(() -> TemplateStep.of(null, TemplateDuration.open(), null,
                "<red>no</red>", List.of("say hi"))).isInstanceOf(ValidationException.class);
        Assertions.assertThat(TemplateStep.of(null, TemplateDuration.open(), " Spam ", null,
                List.of("say hi")).reason()).contains("Spam");
    }

    @Test
    void reasonsAndMessagesAreChecked() {
        Assertions.assertThatThrownBy(() -> TemplateStep.of(PunishmentType.WARN,
                TemplateDuration.open(), " ", null, List.of()))
                .isInstanceOf(ValidationException.class);
        Assertions.assertThatThrownBy(() -> TemplateStep.of(PunishmentType.WARN,
                TemplateDuration.open(), null, "x".repeat(TemplateStep.MAX_MESSAGE_LENGTH + 1),
                List.of())).isInstanceOf(ValidationException.class);
    }
}
