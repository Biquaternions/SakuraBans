package net.serlith.sakurabans.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Suggestions through a real Brigadier tree, built by cloud-brigadier like Paper builds it, so a
 * client would get them: flag values after the reason, templates, categories.
 */
class BrigadierSuggestionsTest extends CommandTest {

    private final CommandDispatcher<CommandActor> dispatcher = new CommandDispatcher<>();

    @Override
    protected String configYaml() {
        return
                """
                scopes:
                  categories:
                    pvp: [kitpvp]
                reasons:
                  shortcuts:
                    griefing: Griefing
                """;
    }

    @BeforeEach
    void brigadier() {
        final CloudBrigadierManager<CommandActor, CommandActor> brigadier =
                new CloudBrigadierManager<>(this.manager, SenderMapper.identity());
        for (final Command<CommandActor> command : this.manager.commands()) {
            final String name = command.rootComponent().name();
            if (this.dispatcher.getRoot().getChild(name) == null) {
                this.dispatcher.getRoot().addChild(brigadier.literalBrigadierNodeFactory()
                        .createNode(name, command, context -> 1));
            }
        }
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking")
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("secret", PunishmentType.BAN)
                .reason("Secret")
                .permissionRequired(true).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam")
                .build(), CONSOLE).join();
    }

    private List<String> suggest(final CommandActor actor, final String line) {
        return this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(line, actor))
                .join().getList().stream().map(Suggestion::getText).toList();
    }

    @Test
    void templateFlagsSuggestTheTemplatesOfTheCommandsType() {
        Assertions.assertThat(this.suggest(this.console, "ban Steve --template "))
                .containsExactlyInAnyOrder("hacking", "secret", "griefing");
        Assertions.assertThat(this.suggest(this.console, "ban Steve -d 7d -t "))
                .contains("hacking");
        Assertions.assertThat(this.suggest(this.console, "mute Steve --template "))
                .containsExactlyInAnyOrder("spam", "griefing");

        final FakeCommandActor mod = FakeCommandActor.player(UUID.randomUUID(), "Mod")
                .grant(Permissions.punish(PunishmentType.BAN));
        Assertions.assertThat(this.suggest(mod, "ban Steve --template "))
                .containsExactlyInAnyOrder("hacking", "griefing");
    }

    @Test
    void laddersAreNotOfferedOnThePunishCommands() {
        this.templates.create(TemplateBuilder.ladder("toxicity")
                .reason("Toxic")
                .steps(List.of(TemplateStep.of(PunishmentType.KICK, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.BAN,
                                TemplateDuration.fixed(Duration.ofDays(1)))))
                .build(), CONSOLE).join();
        for (final String command : List.of("ban", "mute", "warn", "kick")) {
            Assertions.assertThat(this.suggest(this.console, command + " Steve -t "))
                    .as(command).doesNotContain("toxicity");
        }
        Assertions.assertThat(this.suggest(this.console, "mute Steve -t "))
                .contains("spam").doesNotContain("hacking");
        this.known(STEVE, "Steve");

        this.run(this.console, "ban Steve -t toxicity");
        Assertions.assertThat(this.console.last()).doesNotContain("Banned", "Kicked");
        // A preset that leaves the duration open takes the command's.
        this.run(this.console, "ban Steve -t hacking -d 1h");
        Assertions.assertThat(this.console.last()).contains("Banned Steve");
    }

    @Test
    void eachCommandSuggestsItsOwnPresetsAndPunishItsLadders() {
        this.templates.create(TemplateBuilder.preset("caution", PunishmentType.WARN)
                .reason("Caution").build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("rename", PunishmentType.KICK)
                .reason("Rename").build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("flood", PunishmentType.MUTE)
                .reason("Flood").duration(TemplateDuration.fixed(Duration.ofHours(1)))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("misconduct").reason("Misconduct")
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("hidden").reason("Hidden")
                .permissionRequired(true)
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .build(), CONSOLE).join();

        Assertions.assertThat(this.suggest(this.console, "ban Steve -t "))
                .contains("hacking", "secret").doesNotContain("spam", "misconduct", "caution");
        Assertions.assertThat(this.suggest(this.console, "mute Steve -t "))
                .contains("spam", "flood").doesNotContain("hacking", "misconduct");
        Assertions.assertThat(this.suggest(this.console, "tempmute Steve 1h -t "))
                .as("only presets that leave the duration open").contains("spam")
                .doesNotContain("flood");
        Assertions.assertThat(this.suggest(this.console, "warn Steve -t "))
                .contains("caution").doesNotContain("spam");
        Assertions.assertThat(this.suggest(this.console, "kick Steve -t "))
                .contains("rename").doesNotContain("caution");

        Assertions.assertThat(this.suggest(this.console, "punish Steve "))
                .containsExactlyInAnyOrder("hidden", "misconduct");
        final FakeCommandActor helper = FakeCommandActor.player(UUID.randomUUID(), "Helper")
                .grant(Permissions.PUNISH);
        Assertions.assertThat(this.suggest(helper, "punish Steve "))
                .containsExactly("misconduct");
        Assertions.assertThat(this.suggest(helper, "punish Steve misconduct "))
                .contains("--reason", "-g").doesNotContain("--silent", "--template");
        helper.grant(Permissions.punishFlag(PunishFlags.SILENT));
        Assertions.assertThat(this.suggest(helper, "punish Steve misconduct "))
                .contains("--silent");
    }

    @Test
    void categoryFlagsSuggestCategoriesAfterDurationsAndTemplates() {
        Assertions.assertThat(this.suggest(this.console,
                "ban Steve --template hacking --category "))
                .containsExactlyInAnyOrder("pvp");
        Assertions.assertThat(this.suggest(this.console, "ban Steve -d 1d --category "))
                .containsExactlyInAnyOrder("pvp");
    }

    @Test
    void flagsCompleteRightAfterTheTarget() {
        Assertions.assertThat(this.suggest(this.console, "ban Steve "))
                .contains("--duration", "-d", "--reason", "-r", "--template", "-s");
        Assertions.assertThat(this.suggest(this.console, "ban Steve -d "))
                .contains("7d", "30d");
        Assertions.assertThat(this.suggest(this.console, "ban Steve -d 7d "))
                .contains("--reason", "--silent").doesNotContain("--duration");
        Assertions.assertThat(this.suggest(this.console, "kick Steve "))
                .contains("--reason", "-s").doesNotContain("--duration");
        Assertions.assertThat(this.suggest(this.console, "tempban Steve 7d "))
                .contains("--reason").doesNotContain("--duration");
        Assertions.assertThat(this.suggest(this.console, "unban Steve "))
                .contains("--reason", "--scope");
    }

    @Test
    void theNewFlagsAreSuggestedWhereTheyBelong() {
        final FakeCommandActor mod = FakeCommandActor.player(UUID.randomUUID(), "Mod")
                .grant(Permissions.punish(PunishmentType.BAN),
                        Permissions.punish(PunishmentType.KICK),
                        Permissions.flag(PunishmentType.BAN, "quiet"));

        Assertions.assertThat(this.suggest(this.console, "kick Steve "))
                .contains("--no-record", "--quiet", "--hide", "--server-origin");
        Assertions.assertThat(this.suggest(this.console, "ban Steve "))
                .contains("--quiet", "--hide", "--server-origin")
                .doesNotContain("--no-record");
        Assertions.assertThat(this.suggest(this.console, "unban Steve "))
                .contains("--quiet", "--hide");
        Assertions.assertThat(this.suggest(this.console, "punish Steve toxicity "))
                .contains("--quiet", "--server-origin");
        // Only the flags the sender may use.
        Assertions.assertThat(this.suggest(mod, "ban Steve ")).contains("--quiet")
                .doesNotContain("--hide", "--server-origin");
        Assertions.assertThat(this.suggest(mod, "kick Steve ")).doesNotContain("--no-record");
    }

    @Test
    void resetSuggestsItsFlagsAndTheirValues() {
        Assertions.assertThat(this.suggest(this.console, "sakurabans reset "))
                .contains("--mode", "--scope", "--exclude", "--confirm");
        Assertions.assertThat(this.suggest(this.console, "sakurabans reset --mode "))
                .containsExactlyInAnyOrder("delete", "deactivate", "prune");
        Assertions.assertThat(this.suggest(this.console, "sakurabans reset --exclude "))
                .containsExactlyInAnyOrder("ban", "mute", "warn", "kick");
        Assertions.assertThat(this.suggest(this.console, "sakurabans "))
                .contains("reset", "addlogin");
    }

    @Test
    void auditSuggestsItsFiltersAndTheirValues() {
        this.names.put(STEVE, "Steve");

        Assertions.assertThat(this.suggest(this.console, "sakurabans audit "))
                .contains("export", "--actor", "--player", "--id", "--template", "--setting",
                        "--action", "--since");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit export "))
                .containsExactlyInAnyOrder("--actor", "--player", "--id", "--template",
                        "--setting", "--action", "--since");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --action "))
                .contains("create", "update", "void");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit export --action "))
                .contains("update");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --since "))
                .contains("1h", "7d");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --actor "))
                .contains("console", "Steve");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --player "))
                .contains("Steve").doesNotContain("console");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --template "))
                .containsExactlyInAnyOrder("hacking", "secret", "spam");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --setting "))
                .contains("reasons.default", "display.censor-ip-addresses");
        Assertions.assertThat(this.suggest(this.console, "sakurabans audit --setting reasons.d"))
                .contains("reasons.default");
    }
}
