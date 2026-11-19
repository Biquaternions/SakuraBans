package net.serlith.sakurabans.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MessageKeyTest {

    private final Map<String, String> english = LangLoader.builtIn();

    @Test
    void everyKeyExistsInEnglish() {
        assertThat(this.english.keySet())
                .containsAll(MessageKey.all().stream().map(MessageKey::key).toList());
    }

    @Test
    void englishHasNoUnknownKeys() {
        final Set<String> known =
                MessageKey.all().stream().map(MessageKey::key).collect(Collectors.toSet());

        assertThat(this.english.keySet()).allMatch(known::contains,
                "a key of lang/en_US.yml that has no MessageKey constant");
    }

    @Test
    void keysAreDotSeparatedSnakeCase() {
        assertThat(MessageKey.all()).extracting(MessageKey::key)
                .allMatch(key -> key.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"));
    }
}
