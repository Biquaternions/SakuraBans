package net.serlith.sakurabans.paper;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.serlith.sakurabans.core.command.CommandActor;
import org.incendo.cloud.SenderMapper;

final class PaperSenderMapper implements SenderMapper<CommandSourceStack, CommandActor> {

    @Override
    public CommandActor map(final CommandSourceStack source) {
        return new PaperCommandActor(source);
    }

    @Override
    public CommandSourceStack reverse(final CommandActor actor) {
        return ((PaperCommandActor) actor).source();
    }
}
