package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.command.CommandTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@code /sb settings list|reset|publish} with scopes (S50); no scope means global. */
class ScopedSettingsCommandsTest extends CommandTest {

    @Override
    protected String configYaml() {
        return "reasons:\n  default: From the file\n";
    }

    private List<String> lines(final String command) {
        this.console.clear();
        this.run(this.console, command);
        return EditorTest.lines(this.console);
    }

    @Test
    void listAndResetNameTheirScope() {
        this.settingsService.change(ServerScope.of("lobby"), Map.of("reasons.default", "Lobby"),
                Set.of(), 0, CONSOLE).join();

        Assertions.assertThat(this.lines("sb settings list")).containsExactly(
                "No setting is overridden in global.");
        Assertions.assertThat(this.lines("sb settings list server:lobby")).containsExactly(
                "Settings overridden in server:lobby (1; they win over the layers below and"
                        + " config.yml)",
                " - reasons.default: this server's file From the file, override Lobby");
        Assertions.assertThat(this.lines("sb settings list --effective")).contains(
                "This server's settings (layers: server:lobby, global, then config.yml)",
                " - reasons.default: Lobby (server:lobby)",
                " - lockdown.persist: true (default)");
        Assertions.assertThat(this.lines("sb settings list local")).singleElement().asString()
                .contains("is not a scope");

        Assertions.assertThat(this.lines("sb settings reset reasons.default")).containsExactly(
                "reasons.default isn't overridden in global.");
        Assertions.assertThat(this.lines("sb settings reset reasons.default server:lobby"))
                .containsExactly("Removed the override of reasons.default in server:lobby; the"
                        + " next layer applies again.");
        Assertions.assertThat(this.config.settings().reasons().defaultReason())
                .isEqualTo("From the file");
    }

    @Test
    void publishShowsTheDiffAndWritesOnConfirm() {
        Assertions.assertThat(this.lines("sb settings publish reasons.default")).containsExactly(
                "Publishing the files of lobby as global overrides would change 1 settings:",
                " - reasons.default: No reason specified → From the file",
                "Run it again with --confirm within 30 seconds to publish these.");
        Assertions.assertThat(this.settingsService.overrides()).isEmpty();

        Assertions.assertThat(this.lines("sb settings publish reasons.default --confirm"))
                .last().isEqualTo("Published 1 settings of lobby as global overrides.");
        Assertions.assertThat(this.settingsService.overrides(GlobalScope.INSTANCE))
                .containsOnlyKeys("reasons.default");
        Assertions.assertThat(this.lines("sb settings publish reasons.default"))
                .containsExactly("The files of lobby give the same values as the global layer.");
    }
}
