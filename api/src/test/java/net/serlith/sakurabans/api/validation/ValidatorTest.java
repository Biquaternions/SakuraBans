package net.serlith.sakurabans.api.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    @Test
    void collectsEveryError() {
        final Validator validator = Validator.create()
                .name("name", " ", 10)
                .maxLength("reason", "x".repeat(11), 10)
                .identifier("template", "a b")
                .check(true, "ignored", Code.INVALID, "never recorded");

        assertThat(validator.errors()).extracting(FieldError::code).containsExactly(
                Code.BLANK, Code.TOO_LONG, Code.INVALID_IDENTIFIER);
        assertThatThrownBy(validator::throwIfAny)
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).hasSize(3))
                .hasMessageContaining("name: must not be blank");
    }

    @Test
    void absentValuesAreValid() {
        assertThat(Validator.create().name("a", null, 1).identifier("b", null).errors()).isEmpty();
        ValidationException.throwIfAny(List.of());
    }

    @Test
    void identifiersAreNormalised() {
        assertThat(Validator.normalizeIdentifier("Lobby-1")).contains("lobby-1");
        assertThat(Validator.normalizeIdentifier("lobby.1")).isEmpty();
        assertThat(Code.TOO_LONG.id()).isEqualTo("too_long");
    }
}
