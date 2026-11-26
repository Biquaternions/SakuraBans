package net.serlith.sakurabans.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.junit.jupiter.api.Test;

class TemplateStepTest {

    @Test
    void templateStepsNeedPositiveDurations() {
        assertThat(TemplateStep.of(null).duration()).isEmpty();
        assertThat(TemplateStep.of(Duration.ofHours(1)).duration()).contains(Duration.ofHours(1));
        assertThatThrownBy(() -> TemplateStep.of(Duration.ZERO))
                .isInstanceOf(ValidationException.class);
    }
}
