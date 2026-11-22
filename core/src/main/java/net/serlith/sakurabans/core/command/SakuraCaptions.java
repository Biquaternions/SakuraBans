package net.serlith.sakurabans.core.command;

import net.serlith.sakurabans.core.message.MessageService;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionProvider;

public final class SakuraCaptions {

    public static final Caption TARGET = Caption.of("sakurabans.target");
    public static final Caption DURATION = Caption.of("sakurabans.duration");
    public static final Caption SCOPE = Caption.of("sakurabans.scope");

    static final String PREFIX = "command.caption.";

    private SakuraCaptions() {
    }

    public static CaptionProvider<CommandActor> provider(final MessageService messages) {
        return (caption, actor) -> messages.find(PREFIX + caption.key(),
                actor.locale().orElseGet(messages::defaultLocale)).orElse(null);
    }
}
