package net.serlith.sakurabans.core.command.module;

import net.serlith.sakurabans.core.command.CommandTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** /banwave (plan S37c). */
class BanWaveCommandsTest extends CommandTest {

    @Test
    void wavesAreBuiltAndReleasedFromCommands() {
        this.known(STEVE, "Steve");
        this.run(this.console, "banwave create autumn cheaters");
        Assertions.assertThat(this.console.last()).contains("#1 autumn cheaters");
        this.run(this.console, "banwave add 1 Steve -d 7d -r x-ray");
        Assertions.assertThat(this.console.last()).contains("Added Steve", "1 in all");
        this.run(this.console, "banwave info 1");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("pending", "x-ray");
        this.run(this.console, "banwave release 1");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        this.run(this.console, "banwave release 1 --confirm");
        Assertions.assertThat(this.console.last()).contains("1 punished, 0 failed");
        this.run(this.console, "banwave list");
        Assertions.assertThat(this.console.last()).contains("released");
        this.run(this.console, "banwave cancel 9");
        Assertions.assertThat(this.console.last()).contains("no such ban wave");
    }
}
