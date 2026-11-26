package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.model.AdminResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S53: message overrides per scope, resolved like settings, and their commands. */
class ScopedMessagesTest extends CommandTest {

    private static final String KEY = "chat.muted";
    private static final Scope LOBBY = ServerScope.of("lobby");

    private void set(final Scope scope, final Locale locale, final String text) {
        Assertions.assertThat(this.messageTemplates.change(scope, locale, Map.of(KEY, text),
                Set.of(), this.messageTemplates.version(scope, locale), CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
    }

    private String text() {
        return this.messages.find(KEY, Locale.US).orElseThrow();
    }

    private List<String> lines(final String command) {
        this.console.clear();
        this.run(this.console, command);
        return EditorTest.lines(this.console);
    }

    @Test
    void theServerWinsOverItsCategoriesAndGlobal() {
        final String shipped = this.text();
        this.set(ServerScope.of("skywars"), Locale.US, "Skywars");
        Assertions.assertThat(this.text()).isEqualTo(shipped);

        this.set(GlobalScope.INSTANCE, Locale.US, "Global");
        Assertions.assertThat(this.text()).isEqualTo("Global");
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("scopes.categories",
                Map.of("hubs", List.of("lobby"))), Set.of(), this.settingsService.version(),
                CONSOLE).join();
        this.set(CategoryScope.of("hubs"), Locale.US, "Hubs");
        Assertions.assertThat(this.text()).isEqualTo("Hubs");
        this.set(LOBBY, Locale.US, "Lobby");
        Assertions.assertThat(this.text()).isEqualTo("Lobby");
        Assertions.assertThat(this.messageTemplates.source(KEY, Locale.US)).contains(LOBBY);

        // Leaving the category is picked up without reloading the messages.
        this.settingsService.change(GlobalScope.INSTANCE, Map.of(), Set.of("scopes.categories"),
                this.settingsService.version(), CONSOLE).join();
        this.messageTemplates.change(LOBBY, Locale.US, Map.of(), Set.of(KEY),
                this.messageTemplates.version(LOBBY, Locale.US), CONSOLE).join();
        Assertions.assertThat(this.text()).isEqualTo("Global");
        Assertions.assertThat(this.messageTemplates.version(LOBBY, Locale.US)).isEqualTo(2);
        Assertions.assertThat(this.messageTemplates.version(LOBBY, Locale.FRANCE)).isZero();
    }

    @Test
    void listAndResetTakeTheScopeLast() {
        this.set(LOBBY, Locale.US, "Lobby");

        Assertions.assertThat(this.lines("sb messages list")).containsExactly(
                "No message is overridden in global.");
        Assertions.assertThat(this.lines("sb messages list server:lobby")).containsExactly(
                "Messages of en-US overridden in server:lobby (1; they win over the layers"
                        + " below and the language file)", " - chat.muted");
        Assertions.assertThat(this.lines("sb messages list en_US server:lobby")).hasSize(2);
        Assertions.assertThat(this.lines("sb messages reset en_US chat.muted")).containsExactly(
                "Nothing of en-US matching chat.muted is overridden in global.");
        Assertions.assertThat(this.lines("sb messages reset en_US all server:lobby"))
                .singleElement().asString().contains("--confirm");
        Assertions.assertThat(this.lines("sb messages reset en_US all server:lobby --confirm"))
                .containsExactly("Removed every message override of en-US in server:lobby"
                        + " (1).");
        Assertions.assertThat(this.messageTemplates.allOverrides().getOrDefault(LOBBY,
                Map.of()).getOrDefault(Locale.US, Map.of())).isEmpty();
    }
}
