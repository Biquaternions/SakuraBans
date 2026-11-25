package net.serlith.sakurabans.core.command.module;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.command.CommandTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** /sb gdpr (plan S37e). */
class GdprCommandsTest extends CommandTest {

    @Test
    void exportsAreWrittenAndErasesConfirmed() throws Exception {
        this.known(STEVE, "Steve");
        this.run(this.console, "sb gdpr export Steve");
        Assertions.assertThat(this.console.last()).contains("Exported the data of Steve");
        final List<Path> files;
        try (Stream<Path> listing = Files.list(this.dir.resolve("exports"))) {
            files = listing.toList();
        }
        Assertions.assertThat(files).singleElement().satisfies(file ->
                Assertions.assertThat(Files.readString(file))
                        .startsWith("{\"uuid\":\"" + STEVE + "\"")
                        .contains("\"names\":[{\"name\":\"Steve\""));

        this.run(this.console, "sb gdpr erase Steve");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.players.profile(STEVE).join()).isPresent();
        this.run(this.console, "sb gdpr erase Steve --confirm");
        Assertions.assertThat(this.console.last()).contains("Erased", "1 names");
        Assertions.assertThat(this.players.profile(STEVE).join()).isEmpty();
    }
}
