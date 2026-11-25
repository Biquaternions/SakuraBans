package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        Assertions.assertThat(files).singleElement().satisfies(file -> {
            final JsonObject json = JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject();
            Assertions.assertThat(json.get("uuid").getAsString()).isEqualTo(STEVE.toString());
            Assertions.assertThat(json.getAsJsonArray("names").get(0).getAsJsonObject()
                    .get("name").getAsString()).isEqualTo("Steve");
            Assertions.assertThat(json.get("profile").isJsonObject()).isTrue();
            Assertions.assertThat(json.getAsJsonArray("received")).isEmpty();
        });

        this.run(this.console, "sb gdpr erase Steve");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.players.profile(STEVE).join()).isPresent();
        this.run(this.console, "sb gdpr erase Steve --confirm");
        Assertions.assertThat(this.console.last()).contains("Erased", "1 names");
        Assertions.assertThat(this.players.profile(STEVE).join()).isEmpty();
    }
}
