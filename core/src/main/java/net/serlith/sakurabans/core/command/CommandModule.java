package net.serlith.sakurabans.core.command;

import org.incendo.cloud.CommandManager;

public interface CommandModule {

    void register(CommandManager<CommandActor> manager);
}
