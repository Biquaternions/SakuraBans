package net.serlith.sakurabans.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActorAndEvidenceTest {

    @Test
    void sourcesAreLowerCased() {
        assertThat(Actor.of(ConsoleOperator.INSTANCE, "API:Bot").source()).isEqualTo("api:bot");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "in game", "web/ui"})
    void sourcesRejectSpacesAndSlashes(final String source) {
        assertThatThrownBy(() -> Actor.of(ConsoleOperator.INSTANCE, source))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.errors())
                        .singleElement().satisfies(error -> {
                            assertThat(error.path()).isEqualTo("source");
                            assertThat(error.code()).isEqualTo(Code.INVALID_FORMAT);
                        }));
    }

    @Test
    void evidenceIsWebLink() {
        assertThat(EvidenceRef.of("https://example.org/clip.mp4").uri())
                .isEqualTo("https://example.org/clip.mp4");
        assertThat(EvidenceRef.of("http://example.org/x").uri()).isEqualTo("http://example.org/x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ftp://x", "reports:chat:1", "https://a b", "example.org"})
    void otherEvidenceIsRejected(final String uri) {
        assertThatThrownBy(() -> EvidenceRef.of(uri)).isInstanceOf(ValidationException.class);
    }

    @Test
    void evidenceIsLimitedInLength() {
        final String tooLong = "https://example.org/" + "x".repeat(Lengths.MAX_EVIDENCE);

        assertThatThrownBy(() -> EvidenceRef.of(tooLong))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.errors().getFirst().code()).isEqualTo(Code.TOO_LONG));
    }

    @Test
    void factoriesReportFieldErrors() {
        assertThatThrownBy(() -> ServerScope.of("lobby.1"))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.errors().getFirst().code())
                                .isEqualTo(Code.INVALID_IDENTIFIER));
    }
}
