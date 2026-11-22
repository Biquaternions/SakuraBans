package net.serlith.sakurabans.core.command;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jspecify.annotations.NonNull;

/** A Cloud manager without a platform, like the platforms build it (simple coordinator). */
public final class TestCommandManager extends CommandManager<CommandActor> {

    public TestCommandManager() {
        super(ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler());
    }

    @Override
    public boolean hasPermission(final CommandActor sender, final @NonNull String permission) {
        return sender.hasPermission(permission);
    }

    /** Runs a command line (no slash) and waits for its handler. */
    public void run(final CommandActor sender, final String line) {
        this.commandExecutor().executeCommand(sender, line)
                .exceptionally(failure -> null)
                .join();
    }
}
