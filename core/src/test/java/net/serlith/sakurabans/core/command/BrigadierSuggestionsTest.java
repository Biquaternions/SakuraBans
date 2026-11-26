package net.serlith.sakurabans.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Suggestions through a real Brigadier tree, built by cloud-brigadier like Paper builds it, so a
 * client would get them (S31 review): flag values after the reason, templates, categories.
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
        this.templates.create(TemplateDefinition.builder("hacking", PunishmentType.BAN)
                .reason("Hacking")
                .build(), CONSOLE).join();
        this.templates.create(TemplateDefinition.builder("secret", PunishmentType.BAN)
                .reason("Secret")
                .permissionRequired(true).build(), CONSOLE).join();
        this.templates.create(TemplateDefinition.builder("spam", PunishmentType.MUTE)
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
    void mixedLaddersAreOfferedOnEveryPunishCommandAndSayWhatHappened() {
        this.templates.create(TemplateDefinition.builder("toxicity", PunishmentType.BAN)
                .reason("Toxic")
                .ladder(List.of(TemplateStep.of(PunishmentType.KICK, null, List.of(), null),
                        TemplateStep.of(Duration.ofDays(1))))
                .build(), CONSOLE).join();
        for (final String command : List.of("ban", "mute", "warn", "kick")) {
            Assertions.assertThat(this.suggest(this.console, command + " Steve -t "))
                    .as(command).contains("toxicity");
        }
        this.known(STEVE, "Steve");

        this.run(this.console, "ban Steve -t toxicity");
        Assertions.assertThat(this.console.last()).contains("Kicked Steve", "Toxic")
                .doesNotContain("Banned");
        this.run(this.console, "warn Steve -t toxicity");
        Assertions.assertThat(this.console.last()).contains("Banned Steve");
        this.run(this.console, "mute Steve -t toxicity -d 1h");
        Assertions.assertThat(this.console.last()).as("the template decides the duration")
                .doesNotContain("Muted");
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
}
