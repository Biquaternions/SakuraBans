package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.command.CommandSource;
import net.serlith.sakurabans.core.command.CommandActor;
import org.incendo.cloud.SenderMapper;

final class VelocitySenderMapper implements SenderMapper<CommandSource, CommandActor> {

    @Override
    public CommandActor map(final CommandSource source) {
        return new VelocityCommandActor(source);
    }

    @Override
    public CommandSource reverse(final CommandActor actor) {
        return ((VelocityCommandActor) actor).source();
    }
}
