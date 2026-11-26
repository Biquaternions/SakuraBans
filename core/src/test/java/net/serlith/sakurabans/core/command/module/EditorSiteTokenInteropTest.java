package net.serlith.sakurabans.core.command.module;

import java.nio.file.Files;
import net.serlith.sakurabans.core.command.CommandTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * {@code web.token} against a running editor site started with an upload token. Opt in with
 * {@code -PeditorSiteSecured=<url> -PeditorSiteUploadToken=<token>}; skipped otherwise.
 */
@EnabledIfSystemProperty(named = "sakurabans.editorSiteSecured", matches = ".+")
class EditorSiteTokenInteropTest extends CommandTest {

    private static final String SITE = System.getProperty("sakurabans.editorSiteSecured", "");
    private static final String TOKEN = System.getProperty("sakurabans.editorSiteUploadToken",
            "");

    @Override
    protected String configYaml() {
        return "web:\n  url: '" + SITE + "'\n  token: '" + TOKEN + "'\n  live-apply: false\n";
    }

    private String open() {
        this.console.clear();
        this.run(this.console, "sb editor");
        return this.console.last();
    }

    @Test
    void theUploadTokenIsSentAndWrongOrMissingOnesAreUnauthorized() throws Exception {
        Assertions.assertThat(this.open()).contains("Open the editor: " + SITE + "/editor/");

        for (final String token : new String[] {"wrong-token", ""}) {
            Files.writeString(this.dir.resolve("config.yml"), "web:\n  url: '" + SITE
                    + "'\n  token: '" + token + "'\n");
            this.config.reload();
            Assertions.assertThat(this.open()).as("token '%s'", token)
                    .contains("The web editor refused the upload: check web.token");
        }
    }
}
