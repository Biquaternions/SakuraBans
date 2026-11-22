package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckAndAltCommandsTest extends CommandTest {

    private InetAddress other;

    @BeforeEach
    void players() {
        this.other = InetAddresses.parseLiteral("198.51.100.1").orElseThrow();
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        this.players.recordConnection(STEVE, "Steve2", this.now.get().plusSeconds(60),
                this.other, "survival").join();
    }

    @Test
    void checkShowsWhatReachesThePlayer() {
        this.run(this.console, "checkban Steve2");
        Assertions.assertThat(this.console.last()).contains("Steve2 has nothing active");

        this.run(this.console, "ban 198.51.100.1 shared network");
        this.console.clear();
        this.run(this.console, "checkban Steve2");

        Assertions.assertThat(this.console.texts().getFirst()).contains("1 active");
        Assertions.assertThat(this.console.texts().get(1)).contains("shared network");
    }

    @Test
    void checkByIdShowsAnyPunishment() {
        this.run(this.console, "kick Steve2 bye");
        final Punishment kick = this.service.query(PunishmentQuery.builder().build()).join()
                .items().getFirst();
        this.console.clear();

        this.run(this.console, "checkwarn #" + kick.id());

        Assertions.assertThat(String.join("\n", this.console.texts())).contains("bye", "kick");
    }

    @Test
    void altsAreAccountsSharingAnAddress() {
        this.run(this.console, "alts Alex");

        Assertions.assertThat(this.console.texts().getFirst())
                .contains("Alex shares addresses with 1 accounts");
        Assertions.assertThat(this.console.texts().get(1)).contains("Steve2");
    }

    @Test
    void addressAndNameHistories() {
        this.run(this.console, "iphistory Steve2");
        Assertions.assertThat(this.console.texts()).hasSize(3)
                .anyMatch(text -> text.contains("198.51.100.1") && text.contains("survival"));

        this.console.clear();
        this.run(this.console, "namehistory Steve2");
        Assertions.assertThat(this.console.texts()).hasSize(3)
                .anyMatch(text -> text.contains(" Steve "));
    }

    @Test
    void lastUuidOfName() {
        this.run(this.console, "lastuuid steve2");
        Assertions.assertThat(this.console.last()).contains(STEVE.toString());

        this.run(this.console, "lastuuid Nobody");
        Assertions.assertThat(this.console.last()).contains("Nobody has used the name Nobody");
    }
}
