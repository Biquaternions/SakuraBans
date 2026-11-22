package net.serlith.sakurabans.core.command;

import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.util.Futures;

public final class CommandRunner {

    private final Lifecycle lifecycle;
    private final MessageService messages;
    private final ComponentLogger logger;

    public CommandRunner(final Lifecycle lifecycle, final MessageService messages,
            final ComponentLogger logger) {
        this.lifecycle = lifecycle;
        this.messages = messages;
        this.logger = logger;
    }

    public CompletableFuture<Void> run(final CommandActor actor,
            final Supplier<? extends CompletableFuture<?>> work) {
        if (!this.lifecycle.running()) {
            this.send(actor, MessageKey.ERROR_NOT_READY);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?> future;
        try {
            future = work.get();
        } catch (final RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.handle((ignored, failure) -> {
            if (failure != null) {
                this.internalError(actor, Futures.unwrap(failure));
            }
            return null;
        });
    }

    public void internalError(final CommandActor actor, final Throwable failure) {
        final String incident = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
        this.logger.error("Command by {} failed (incident {})", actor.name(), incident, failure);
        this.send(actor, MessageKey.ERROR_INTERNAL, Placeholder.unparsed("id", incident));
    }

    public void send(final CommandActor actor, final MessageKey key,
            final TagResolver... resolvers) {
        actor.audience().sendMessage(this.render(actor, key, resolvers));
    }

    public Component render(final CommandActor actor, final MessageKey key,
            final TagResolver... resolvers) {
        return this.messages.render(key, this.locale(actor), resolvers);
    }

    public Locale locale(final CommandActor actor) {
        return actor.locale().orElseGet(this.messages::defaultLocale);
    }
}
