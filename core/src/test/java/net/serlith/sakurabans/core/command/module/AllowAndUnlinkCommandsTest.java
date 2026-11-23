package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllowAndUnlinkCommandsTest extends CommandTest {

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
    }

    @Test
    void allowListedEntriesCanBeAddedListedAndRemoved() {
        this.run(this.console, "sakurabans allow list");
        Assertions.assertThat(this.console.last()).contains("allow list is empty");

        this.run(this.console, "sakurabans allow add Steve shares the school network");
        Assertions.assertThat(this.console.last()).contains("Allow-listed Steve");
        Assertions.assertThat(this.allowList.isAllowed(STEVE)).isTrue();
        this.run(this.console, "sakurabans allow add Steve");
        Assertions.assertThat(this.console.last()).contains("already allow-listed");
        this.run(this.console, "sakurabans allow add 198.51.100.4");

        this.run(this.console, "sakurabans allow list");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("Steve", "shares the school network"));
        Assertions.assertThat(this.console.texts())
                .anySatisfy(line -> Assertions.assertThat(line).contains("198.51.100.4"));

        this.run(this.console, "sakurabans allow remove Steve");
        Assertions.assertThat(this.allowList.isAllowed(STEVE)).isFalse();
        this.run(this.console, "sakurabans allow remove Steve");
        Assertions.assertThat(this.console.last()).contains("isn't allow-listed");
    }

    @Test
    void unlinkForgetsOneOrEveryAddress() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.alsoHome(), null)
                .join();

        this.run(this.console, "sakurabans unlink Steve 203.0.113.7");
        Assertions.assertThat(this.console.last()).contains("Forgot 1 address(es) of Steve");
        this.run(this.console, "sakurabans unlink Steve");
        Assertions.assertThat(this.console.last()).contains("Forgot 1 address(es) of Steve");
        this.run(this.console, "sakurabans unlink Steve");
        Assertions.assertThat(this.console.last()).contains("No such address");
        this.run(this.console, "sakurabans unlink Steve nonsense");
        Assertions.assertThat(this.console.last()).contains("is not an address");
        Assertions.assertThat(this.players.addresses(STEVE).join()).isEmpty();
    }

    private InetAddress alsoHome() {
        return InetAddresses.parseLiteral("198.51.100.9").orElseThrow();
    }
}
