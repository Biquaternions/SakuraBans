package net.serlith.sakurabans.core.command;

import java.time.InstantSource;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.message.MessageKey;

public final class Pager {

    private final CommandRunner runner;
    private final Replies replies;
    private final InstantSource clock;

    public Pager(final CommandRunner runner, final Replies replies, final InstantSource clock) {
        this.runner = runner;
        this.replies = replies;
        this.clock = clock;
    }

    public void punishments(final CommandActor actor, final MessageKey header,
            final TagResolver headerTags, final Page<Punishment> page, final String command) {
        this.send(actor, header, headerTags, page, command, punishment -> this.runner.render(
                actor, MessageKey.LIST_ENTRY, this.replies.punishment(actor, punishment),
                Placeholder.component("status", this.status(actor, punishment))));
    }

    public <T> void send(final CommandActor actor, final MessageKey header,
            final TagResolver headerTags, final Page<T> page, final String command,
            final Function<T, Component> line) {
        if (page.total() == 0) {
            this.runner.send(actor, MessageKey.LIST_EMPTY);
            return;
        }
        if (page.items().isEmpty()) {
            this.runner.send(actor, MessageKey.LIST_NO_SUCH_PAGE,
                    Placeholder.unparsed("pages", Integer.toString(page.pageCount())));
            return;
        }
        final TagResolver counts = TagResolver.resolver(
                Placeholder.unparsed("page", Integer.toString(page.page())),
                Placeholder.unparsed("pages", Integer.toString(page.pageCount())),
                Placeholder.unparsed("total", Long.toString(page.total())));
        this.runner.send(actor, header, headerTags, counts);
        page.items().forEach(item -> actor.audience().sendMessage(line.apply(item)));
        if (page.pageCount() > 1) {
            this.runner.send(actor, MessageKey.LIST_FOOTER, counts,
                    Placeholder.component("previous", this.arrow(actor, page.page() > 1,
                            MessageKey.LIST_PREVIOUS, command, page.page() - 1)),
                    Placeholder.component("next", this.arrow(actor, page.hasNext(),
                            MessageKey.LIST_NEXT, command, page.page() + 1)));
        }
    }

    private Component arrow(final CommandActor actor, final boolean enabled,
            final MessageKey key, final String command, final int target) {
        if (!enabled) {
            return this.runner.render(actor, MessageKey.LIST_ARROW_DISABLED);
        }
        return this.runner.render(actor, key)
                .clickEvent(ClickEvent.runCommand("/" + command + " " + target));
    }

    private Component status(final CommandActor actor, final Punishment punishment) {
        if (punishment.type().instant()) {
            return this.runner.render(actor, MessageKey.LIST_STATUS_INSTANT);
        }
        if (punishment.isActiveAt(this.clock.instant())) {
            return this.runner.render(actor, MessageKey.LIST_STATUS_ACTIVE);
        }
        final EndKind end = punishment.end().map(found -> found.kind()).orElse(EndKind.EXPIRED);
        return this.runner.render(actor, end == EndKind.EXPIRED
                ? MessageKey.LIST_STATUS_EXPIRED
                : MessageKey.LIST_STATUS_ENDED);
    }

}
