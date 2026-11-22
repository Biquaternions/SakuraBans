package net.serlith.sakurabans.core.command.module;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryCommandsTest extends CommandTest {

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
    }

    private void warns(final int count) {
        for (int i = 1; i <= count; i++) {
            this.run(this.console, "warn Steve reason-" + i);
        }
        this.console.clear();
    }

    private static List<String> clicks(final Component component) {
        final List<String> commands = new ArrayList<>();
        collect(component, commands);
        return commands;
    }

    private static void collect(final Component component, final List<String> commands) {
        final ClickEvent<?> click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            commands.add(text.value());
        }
        component.children().forEach(child -> collect(child, commands));
    }

    @Test
    void emptyHistoriesSaySo() {
        this.run(this.console, "history Steve");

        Assertions.assertThat(this.console.texts()).containsExactly(
                "SakuraBans » Nothing to show.");
    }

    @Test
    void onePageHasNoFooter() {
        this.warns(3);

        this.run(this.console, "history Steve");

        Assertions.assertThat(this.console.texts()).hasSize(4);
        Assertions.assertThat(this.console.texts().getFirst())
                .contains("History of Steve (3, page 1/1)");
        Assertions.assertThat(this.console.texts().get(1)).contains("reason-3", "warning");
    }

    @Test
    void manyPagesGetClickableArrows() {
        this.warns(25);

        this.run(this.console, "history Steve 2");

        final List<String> texts = this.console.texts();
        Assertions.assertThat(texts.getFirst()).contains("(25, page 2/3)");
        Assertions.assertThat(texts).hasSize(12);
        Assertions.assertThat(texts.getLast()).contains("Page 2 of 3");
        Assertions.assertThat(clicks(this.console.components().getLast()))
                .containsExactly("/history Steve 1", "/history Steve 3");

        this.console.clear();
        this.run(this.console, "history Steve 3");
        Assertions.assertThat(clicks(this.console.components().getLast()))
                .containsExactly("/history Steve 2");
        this.console.clear();
        this.run(this.console, "history Steve 9");
        Assertions.assertThat(this.console.last()).contains("only 3 pages");
    }

    @Test
    void listsShowActivePunishmentsOfEveryone() {
        this.run(this.console, "ban Steve one");
        this.run(this.console, "ban Alex two 1h");
        this.run(this.console, "kick Alex three");
        this.console.clear();

        this.run(this.console, "banlist");

        Assertions.assertThat(this.console.texts()).hasSize(3);
        Assertions.assertThat(this.console.texts().getFirst()).contains("Active bans (2");
    }

    @Test
    void staffHistoryByOperatorAndConsole() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod",
                Permissions.punish(PunishmentType.KICK));
        this.known(MOD, "Mod");
        this.run(mod, "kick Steve spam");
        this.run(this.console, "kick Alex spam");
        this.console.clear();

        this.run(this.console, "staffhistory Mod");
        Assertions.assertThat(this.console.texts()).hasSize(2);
        Assertions.assertThat(this.console.texts().get(1)).contains("Steve");

        this.console.clear();
        this.run(this.console, "staffhistory console");
        Assertions.assertThat(this.console.texts().get(1)).contains("Alex");
    }

    @Test
    void warnsOfOneselfNeedTheSelfPermission() {
        this.warns(1);
        final FakeCommandActor steve = FakeCommandActor.player(STEVE, "Steve",
                Permissions.LIST_WARNS_SELF);

        this.run(steve, "warns");
        Assertions.assertThat(steve.texts().getFirst()).contains("Warnings of Steve (1");

        this.run(steve, "warns Alex");
        Assertions.assertThat(steve.last()).contains("don't have permission");
    }
}
