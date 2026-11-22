package net.serlith.sakurabans.core.command;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.assertj.core.api.Assertions;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins down how Cloud 2.1's flag-yielding greedy string combines with flags (plan 9.3, S21):
 * flags work after the free text only, and a word starting with {@code -} inside it is taken
 * for a flag.
 */
class GreedyTailTest {

    private final TestCommandManager manager = new TestCommandManager();
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void register() {
        this.manager.command(this.manager.commandBuilder("probe")
                .required("target", StringParser.stringParser())
                .optional("tail", StringParser.greedyFlagYieldingStringParser())
                .flag(CommandFlag.<CommandActor>builder("silent").withAliases("s"))
                .flag(CommandFlag.<CommandActor>builder("server")
                        .withComponent(StringParser.<CommandActor>stringParser()))
                .handler(context -> this.seen.add(context.<String>optional("tail").orElse("")
                        + "|" + context.flags().isPresent("silent")
                        + "|" + context.flags().getValue("server").orElse("-"))));
    }

    private Throwable failure(final String line) {
        return this.manager.commandExecutor()
                .executeCommand(FakeCommandActor.theConsole(), line)
                .handle((result, failure) -> failure == null ? null : failure.getCause())
                .join();
    }

    @Test
    void flagsAfterTheTextWork() {
        this.manager.run(FakeCommandActor.theConsole(), "probe Steve 1d spam -s --server lobby");
        this.manager.run(FakeCommandActor.theConsole(), "probe Steve -s");

        Assertions.assertThat(this.seen).containsExactly("1d spam|true|lobby", "|true|-");
    }

    @Test
    void flagsBeforeOrInsideTheTextAreSyntaxErrors() {
        Assertions.assertThat(this.failure("probe Steve -s 1d spam"))
                .isInstanceOf(InvalidSyntaxException.class);
        Assertions.assertThat(this.failure("probe Steve spam --server lobby more"))
                .isInstanceOf(InvalidSyntaxException.class);
    }

    @Test
    void wordsStartingWithDashesAreFlags() {
        Assertions.assertThat(this.failure("probe Steve spam -x"))
                .isInstanceOf(ArgumentParseException.class);
        this.manager.run(FakeCommandActor.theConsole(), "probe Steve self-harm - bad");

        Assertions.assertThat(this.seen).containsExactly("self-harm - bad|false|-");
    }
}
