package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.appeal.AppealQuery;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.appeal.SakuraAppealService;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.util.Futures;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.LongParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jspecify.annotations.Nullable;

public final class AppealCommands implements CommandModule {

    private static final int PAGE_SIZE = 10;

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Formats formats;
    private final SakuraAppealService appeals;
    private final SakuraPunishmentService punishments;
    private final DisplayIdCodec displayIds;
    private final NameCache names;

    public AppealCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Formats formats, final SakuraAppealService appeals,
            final SakuraPunishmentService punishments, final DisplayIdCodec displayIds,
            final NameCache names) {
        this.registrar = registrar;
        this.runner = runner;
        this.formats = formats;
        this.appeals = appeals;
        this.punishments = punishments;
        this.displayIds = displayIds;
        this.names = names;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "appeal", MessageKey.COMMAND_DESCRIPTION_APPEAL)
                .ifPresent(root -> manager.command(root.permission(Permissions.APPEAL)
                        .required("id", StringParser.stringParser())
                        .required("message", StringParser.greedyStringParser())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.submit(context.sender(), context.get("id"),
                                        context.get("message"))))));
        this.registrar.builder(manager, "appeals", MessageKey.COMMAND_DESCRIPTION_APPEALS)
                .ifPresent(root -> {
                    manager.command(root.literal("list").permission(Permissions.APPEALS_VIEW)
                            .optional("page", IntegerParser.integerParser(1))
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.list(context.sender(),
                                            context.<Integer>optional("page").orElse(1)))));
                    for (final AppealOutcome outcome : AppealOutcome.values()) {
                        final String label = switch (outcome) {
                            case REVOKE -> "accept";
                            case VOID -> "void";
                            case REJECT -> "reject";
                        };
                        manager.command(root.literal(label)
                                .permission(Permissions.APPEALS_DECIDE)
                                .required("appeal", LongParser.longParser(1))
                                .optional("note", StringParser.greedyStringParser())
                                .futureHandler(context -> this.runner.run(context.sender(),
                                        () -> this.decide(context.sender(),
                                                context.get("appeal"), outcome,
                                                context.<String>optional("note")
                                                        .orElse(null)))));
                    }
                });
    }

    private CompletableFuture<?> submit(final CommandActor actor, final String id,
            final String message) {
        final UUID uuid = actor.uuid().orElse(null);
        if (uuid == null) {
            this.runner.send(actor, MessageKey.COMMAND_PLAYERS_ONLY);
            return CompletableFuture.completedFuture(null);
        }
        final OptionalLong punishment = this.displayIds.decode(id);
        if (punishment.isEmpty()) {
            this.runner.send(actor, MessageKey.COMMAND_NOT_AN_ID,
                    Placeholder.unparsed("input", id));
            return CompletableFuture.completedFuture(null);
        }
        return this.appeals.submit(punishment.getAsLong(), uuid, message).thenAccept(result -> {
            switch (result) {
                case AdminResult.Ok<Appeal> ok -> this.runner.send(actor,
                        MessageKey.APPEAL_SUBMITTED,
                        Placeholder.unparsed("id", Long.toString(ok.value().id())));
                case AdminResult.ValidationFailed<Appeal> invalid -> {
                    final String field = invalid.errors().getFirst().path();
                    if (field.equals("appellant")) {
                        this.runner.send(actor, MessageKey.APPEAL_FAILURE_NOT_YOURS);
                    } else if (field.equals("punishmentId")) {
                        this.runner.send(actor, MessageKey.APPEAL_FAILURE_OPEN);
                    } else {
                        this.runner.send(actor, MessageKey.APPEAL_FAILURE_INVALID,
                                Placeholder.unparsed("details",
                                        invalid.errors().getFirst().message()));
                    }
                }
                default -> this.runner.send(actor, MessageKey.APPEAL_FAILURE_NOT_FOUND);
            }
        });
    }

    private CompletableFuture<?> list(final CommandActor actor, final int page) {
        return this.firstPages(page).thenCompose(found -> {
            if (found.items().isEmpty()) {
                this.runner.send(actor, MessageKey.APPEALS_LIST_EMPTY);
                return CompletableFuture.completedFuture(null);
            }
            final List<CompletableFuture<Optional<Punishment>>> reads = found.items()
                    .stream().map(appeal -> this.punishments.byId(appeal.punishmentId()))
                    .toList();
            return Futures.allOf(reads).thenAccept(punished -> {
                this.runner.send(actor, MessageKey.APPEALS_LIST_HEADER,
                        Placeholder.unparsed("total", Long.toString(found.total())),
                        Placeholder.unparsed("page", Integer.toString(page)),
                        Placeholder.unparsed("pages", Integer.toString(found.pageCount())));
                final Locale locale = this.runner.locale(actor);
                for (int i = 0; i < found.items().size(); i++) {
                    final Appeal appeal = found.items().get(i);
                    this.runner.send(actor, MessageKey.APPEALS_LIST_ENTRY,
                            Placeholder.unparsed("id", Long.toString(appeal.id())),
                            Placeholder.unparsed("display_id", punished.get(i)
                                    .map(Punishment::displayId)
                                    .orElse(Long.toString(appeal.punishmentId()))),
                            Placeholder.unparsed("name", this.names.name(appeal.appellant())
                                    .orElse(appeal.appellant().toString())),
                            Placeholder.unparsed("created", this.formats.instant(
                                    appeal.createdAt(), locale)),
                            Placeholder.unparsed("message", appeal.message()));
                }
            });
        });
    }

    private CompletableFuture<Page<Appeal>> firstPages(final int page) {
        CompletableFuture<Page<Appeal>> current = this.appeals.appeals(AppealQuery.builder()
                .statuses(Set.of(Appeal.Status.OPEN)).pageSize(PAGE_SIZE).build());
        for (int i = 1; i < page; i++) {
            current = current.thenCompose(previous -> previous.nextCursor()
                    .map(cursor -> this.appeals.appeals(AppealQuery.builder()
                            .statuses(Set.of(Appeal.Status.OPEN)).pageSize(PAGE_SIZE)
                            .after(cursor).build()))
                    .orElseGet(() -> CompletableFuture.completedFuture(previous)));
        }
        return current;
    }

    private CompletableFuture<?> decide(final CommandActor actor, final long id,
            final AppealOutcome outcome, final @Nullable String note) {
        return this.appeals.appeal(id).thenCompose(found -> {
            if (found.isEmpty()) {
                this.runner.send(actor, MessageKey.APPEAL_FAILURE_NOT_FOUND);
                return CompletableFuture.completedFuture(null);
            }
            return this.appeals.decide(id, outcome, note, found.get().version(),
                    actor.asActor()).thenAccept(result -> this.reply(actor, result, decided ->
                    this.runner.send(actor, MessageKey.APPEALS_DECIDED,
                            Placeholder.unparsed("id", Long.toString(id)),
                            Placeholder.unparsed("outcome",
                                    outcome.name().toLowerCase(Locale.ROOT)))));
        });
    }

    private void reply(final CommandActor actor, final AdminResult<Appeal> result,
            final Consumer<Appeal> ok) {
        switch (result) {
            case AdminResult.Ok<Appeal> done -> ok.accept(done.value());
            case AdminResult.Conflict<Appeal> conflict -> this.runner.send(actor,
                    MessageKey.APPEAL_FAILURE_CONFLICT);
            case AdminResult.Forbidden<Appeal> forbidden -> this.runner.send(actor,
                    MessageKey.APPEAL_FAILURE_FORBIDDEN);
            case AdminResult.ValidationFailed<Appeal> invalid -> this.runner.send(actor,
                    MessageKey.APPEAL_FAILURE_INVALID, Placeholder.unparsed("details",
                            invalid.errors().getFirst().message()));
            case AdminResult.NotFound<Appeal> notFound -> this.runner.send(actor,
                    MessageKey.APPEAL_FAILURE_NOT_FOUND);
        }
    }
}
