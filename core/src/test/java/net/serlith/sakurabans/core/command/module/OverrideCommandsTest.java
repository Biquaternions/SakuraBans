package net.serlith.sakurabans.core.command.module;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.Test;

/** {@code /sb settings}, {@code /sb messages} and the reload notice: overrides are visible. */
class OverrideCommandsTest extends CommandTest {

    @Override
    protected String configYaml() {
        return "reasons:\n  default: From the file\n";
    }

    private void overrideSettings() {
        Assertions.assertThat(this.settingsService.update(Map.of(
                        "reasons.default", "From the network", "warnings.expire-after", "7d"),
                this.settingsService.settings().version(), MODERATOR).join())
                .isInstanceOf(AdminResult.Ok.class);
    }

    private void overrideMessages() {
        Assertions.assertThat(this.messageTemplates.update(Locale.US, Map.of(
                        "victim.warned", "<prefix>Warned: <reason>",
                        "victim.muted", "<prefix>Muted: <reason>"),
                this.messageTemplates.messages(Locale.US).version(), MODERATOR).join())
                .isInstanceOf(AdminResult.Ok.class);
    }

    private List<String> suggest(final String input) {
        return this.manager.suggestionFactory().suggestImmediately(this.console, input).list()
                .stream().map(Suggestion::suggestion).toList();
    }

    private List<RecordingLogger.Entry> overrideLogs() {
        return this.log.entries("info").stream().filter(entry ->
                String.valueOf(entry.arguments()[0]).startsWith("The database overrides"))
                .toList();
    }

    @Test
    void nothingOverriddenSaysSo() {
        this.run(this.console, "sakurabans settings list");
        Assertions.assertThat(this.console.last()).contains("No setting is overridden");
        this.run(this.console, "sakurabans messages list");
        Assertions.assertThat(this.console.last()).contains("No message is overridden");
        Assertions.assertThat(this.suggest("sakurabans settings reset ")).isEmpty();
    }

    @Test
    void settingsListShowsTheFileAndTheDatabaseValue() {
        this.overrideSettings();

        this.run(this.console, "sakurabans settings list");

        Assertions.assertThat(this.console.texts()).anySatisfy(line -> Assertions
                .assertThat(line).contains("reasons.default", "From the file",
                        "From the network"));
        Assertions.assertThat(this.console.texts()).anySatisfy(line -> Assertions
                .assertThat(line).contains("warnings.expire-after", "7d"));
        Assertions.assertThat(this.suggest("sakurabans settings reset "))
                .containsExactly("reasons.default", "warnings.expire-after", "all");
    }

    @Test
    void oneSettingIsResetAndTheFileAppliesAgain() {
        this.overrideSettings();

        this.run(this.console, "sakurabans settings reset reasons.default");

        Assertions.assertThat(this.console.last()).contains("Removed the override of",
                "reasons.default");
        Assertions.assertThat(this.settingsService.overrides())
                .containsOnlyKeys("warnings.expire-after");
        Assertions.assertThat(this.config.settings().reasons().defaultReason())
                .isEqualTo("From the file");
        this.run(this.console, "sakurabans settings reset reasons.default");
        Assertions.assertThat(this.console.last()).contains("isn't overridden");
    }

    @Test
    void resettingEverythingIsConfirmedFirst() {
        this.overrideSettings();

        this.run(this.console, "sakurabans settings reset all");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.settingsService.overrides()).hasSize(2);

        this.run(this.console, "sakurabans settings reset all --confirm");
        Assertions.assertThat(this.console.last())
                .contains("Removed every setting override of global (2)");
        Assertions.assertThat(this.settingsService.overrides()).isEmpty();
    }

    @Test
    void messagesAreListedAndResetPerLocale() {
        this.overrideMessages();

        this.run(this.console, "sakurabans messages list");
        Assertions.assertThat(this.console.texts()).anySatisfy(line -> Assertions
                .assertThat(line).contains("en-US", "2"));
        Assertions.assertThat(this.console.texts()).anySatisfy(line -> Assertions
                .assertThat(line).contains("victim.warned"));
        Assertions.assertThat(this.suggest("sakurabans messages reset en-US "))
                .containsExactly("victim.muted", "victim.warned", "all");

        this.run(this.console, "sakurabans messages reset en-US victim.warned");
        Assertions.assertThat(this.messageTemplates.overrides(GlobalScope.INSTANCE).get(Locale.US))
                .containsExactly("victim.muted");
        this.run(this.console, "sakurabans messages reset en-US all");
        this.run(this.console, "sakurabans messages reset en-US all --confirm");
        Assertions.assertThat(this.messageTemplates.overrides(GlobalScope.INSTANCE)).isEmpty();
    }

    @Test
    void theCommandsNeedTheirPermission() {
        this.overrideSettings();
        final FakeCommandActor helper = FakeCommandActor.player(ALEX, "Helper");

        this.run(helper, "sakurabans settings list");
        Assertions.assertThat(helper.last()).contains("don't have permission");
        this.run(helper, "sakurabans messages list");
        Assertions.assertThat(helper.last()).contains("don't have permission");

        helper.grant(Permissions.ADMIN_SETTINGS);
        this.run(helper, "sakurabans settings reset reasons.default");
        Assertions.assertThat(this.settingsService.overrides())
                .containsOnlyKeys("warnings.expire-after");
    }

    @Test
    void reloadMentionsOverridesOnlyWhenThereAreSome() {
        this.run(this.console, "sakurabans reload");
        Assertions.assertThat(this.console.texts())
                .noneSatisfy(line -> Assertions.assertThat(line).contains("overridden"));
        Assertions.assertThat(this.overrideLogs()).isEmpty();

        this.overrideSettings();
        this.overrideMessages();
        this.console.clear();
        this.run(this.console, "sakurabans reload");

        Assertions.assertThat(this.console.texts()).anySatisfy(line -> Assertions
                .assertThat(line).contains("2 settings (global 2) and 2 messages (global 2) are"));
        Assertions.assertThat(this.overrideLogs()).singleElement().satisfies(entry ->
                Assertions.assertThat(Arrays.deepToString(entry.arguments()))
                        .contains("global 2"));
    }
}
