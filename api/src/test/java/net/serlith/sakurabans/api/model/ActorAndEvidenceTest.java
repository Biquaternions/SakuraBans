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
        assertThat(Actor.of(ConsoleOperator.INSTANCE, "API:Hydra").source()).isEqualTo("api:hydra");
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
    void evidenceIsUrlOrNamespacedReference() {
        assertThat(EvidenceRef.of("https://example.org/clip.mp4").type()).isEmpty();
        assertThat(EvidenceRef.of("hydra:chat-snapshot:A1.b2").type())
                .contains("hydra:chat-snapshot");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ftp://x", "hydra:chat-snapshot", "Hydra:report:1", "a b:c:d"})
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
