package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.EditResult;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.util.Durations;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.jspecify.annotations.Nullable;

public final class EditCommands implements CommandModule {

    private static final int AUDIT_LINES = 10;

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Placeholders placeholders;
    private final Formats formats;
    private final SakuraPunishmentService punishments;
    private final AuditRepository audit;
    private final DisplayIdCodec displayIds;
    private final ScopeParser scopeParser;
    private final ConfirmationTracker confirmations;
    private final InstantSource clock;

    public EditCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Placeholders placeholders, final Formats formats,
            final SakuraPunishmentService punishments, final AuditRepository audit,
            final DisplayIdCodec displayIds, final ScopeParser scopeParser,
            final ConfirmationTracker confirmations, final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.placeholders = placeholders;
        this.formats = formats;
        this.punishments = punishments;
        this.audit = audit;
        this.displayIds = displayIds;
        this.scopeParser = scopeParser;
        this.confirmations = confirmations;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_INFO, root -> root.literal("info")
                .permission(Permissions.INFO)
                .required("id", StringParser.stringParser())
                .futureHandler(context -> this.run(context, punishment -> this.info(context,
                        punishment))));
        final Permission edit = anyType(Permissions::editOwn);
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_EDIT, root -> root.literal("edit")
                .permission(edit)
                .required("id", StringParser.stringParser())
                .literal("reason")
                .required("text", StringParser.greedyStringParser())
                .futureHandler(context -> this.run(context, punishment -> this.edit(context,
                        punishment, PunishmentEdit.NewReason.of(context.get("text")),
                        MessageKey.EDIT_WHAT_REASON))));
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_EDIT, root -> root.literal("edit")
                .permission(edit)
                .required("id", StringParser.stringParser())
                .literal("duration")
                .required("duration", StringParser.stringParser())
                .futureHandler(context -> this.run(context, punishment -> this.duration(context,
                        punishment))));
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_EDIT, root -> root.literal("edit")
                .permission(edit)
                .required("id", StringParser.stringParser())
                .literal("extend")
                .required("duration", DurationParser.descriptor())
                .futureHandler(context -> this.run(context, punishment -> this.extend(context,
                        punishment))));
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_EDIT, root -> root.literal("edit")
                .permission(edit)
                .required("id", StringParser.stringParser())
                .literal("scope")
                .required("scope", this.scopeParser.descriptor())
                .futureHandler(context -> this.run(context, punishment -> this.edit(context,
                        punishment, PunishmentEdit.NewScope.of(context.<Scope>get("scope")),
                        MessageKey.EDIT_WHAT_SCOPE))));
        this.root(manager, MessageKey.COMMAND_DESCRIPTION_DELETE, root -> root.literal("delete")
                .permission(anyType(Permissions::delete))
                .required("id", StringParser.stringParser())
                .flag(CommandFlag.builder("confirm").build())
                .futureHandler(context -> this.run(context, punishment -> this.delete(context,
                        punishment))));
        for (final String operation : new String[] {"add", "remove"}) {
            this.root(manager, MessageKey.COMMAND_DESCRIPTION_EVIDENCE, root -> root
                    .literal("evidence")
                    .permission(edit)
                    .required("id", StringParser.stringParser())
                    .literal(operation)
                    .required("uri", StringParser.stringParser())
                    .futureHandler(context -> this.run(context, punishment -> this.evidence(
                            context, punishment, operation.equals("add")))));
        }
    }

    private void root(final CommandManager<CommandActor> manager, final MessageKey description,
            final Function<Command.Builder<CommandActor>, Command.Builder<CommandActor>> build) {
        this.registrar.builder(manager, SakuraCommands.ROOT, description)
                .ifPresent(root -> manager.command(build.apply(root)));
    }

    private static Permission anyType(final Function<PunishmentType, String> node) {
        return Permission.anyOf(Arrays.stream(PunishmentType.values())
                .map(type -> Permission.of(node.apply(type))).toList());
    }

    private CompletableFuture<Void> run(final CommandContext<CommandActor> context,
            final Function<Punishment, CompletableFuture<?>> work) {
        final CommandActor actor = context.sender();
        return this.runner.run(actor, () -> {
            final String input = context.get("id");
            final OptionalLong id = this.displayIds.decode(input);
            if (id.isEmpty()) {
                this.runner.send(actor, MessageKey.COMMAND_NOT_AN_ID,
                        Placeholder.unparsed("input", input));
                return CompletableFuture.completedFuture(null);
            }
            return this.punishments.byId(id.getAsLong()).thenCompose(found -> {
                if (found.isEmpty()) {
                    this.runner.send(actor, MessageKey.EDIT_FAILURE_NOT_FOUND);
                    return CompletableFuture.completedFuture(null);
                }
                return work.apply(found.get());
            });
        });
    }

    private CompletableFuture<?> info(final CommandContext<CommandActor> context,
            final Punishment punishment) {
        final CommandActor actor = context.sender();
        final Locale locale = this.runner.locale(actor);
        final TagResolver values = this.replies.punishment(actor, punishment);
        this.runner.send(actor, MessageKey.CHECK_DETAIL, values);
        for (final Evidence evidence : punishment.evidence()) {
            this.runner.send(actor, MessageKey.INFO_EVIDENCE,
                    Placeholder.unparsed("uri", evidence.ref().uri()),
                    Placeholder.unparsed("added_by",
                            this.placeholders.operator(evidence.addedBy().operator(), locale)),
                    Placeholder.unparsed("added", this.formats.instant(evidence.addedAt(),
                            locale)));
        }
        punishment.end().ifPresent(end -> this.runner.send(actor, MessageKey.INFO_ENDED, values,
                Placeholder.unparsed("end", lower(end.kind())),
                Placeholder.unparsed("at", this.formats.instant(end.at(), locale))));
        punishment.voiding().ifPresent(voiding -> this.runner.send(actor, MessageKey.INFO_VOIDED,
                Placeholder.unparsed("at", this.formats.instant(voiding.at(), locale)),
                Placeholder.unparsed("actor",
                        this.placeholders.operator(voiding.actor().operator(), locale)),
                Placeholder.unparsed("reason", voiding.reason().orElse(""))));
        if (!actor.hasPermission(Permissions.ADMIN_AUDIT)) {
            return CompletableFuture.completedFuture(null);
        }
        return this.audit.about(SubjectType.PUNISHMENT, Long.toString(punishment.id()),
                AUDIT_LINES).thenAccept(entries -> {
                    if (entries.isEmpty()) {
                        return;
                    }
                    this.runner.send(actor, MessageKey.INFO_AUDIT_HEADER);
                    for (final AuditEntry entry : entries.reversed()) {
                        this.runner.send(actor, MessageKey.INFO_AUDIT_ENTRY,
                                Placeholder.unparsed("at", this.formats.instant(entry.at(),
                                        locale)),
                                Placeholder.unparsed("action", lower(entry.action())),
                                Placeholder.unparsed("actor",
                                        this.placeholders.operator(entry.actor().operator(),
                                                locale)),
                                Placeholder.unparsed("source", entry.actor().source()),
                                Placeholder.unparsed("old", entry.oldValue() == null ? ""
                                        : entry.oldValue() + " → "),
                                Placeholder.unparsed("new", entry.newValue() == null ? ""
                                        : entry.newValue()));
                    }
                });
    }

    private CompletableFuture<?> duration(final CommandContext<CommandActor> context,
            final Punishment punishment) {
        final String text = context.get("duration");
        final Instant expiry;
        if (Durations.isPermanentToken(text)) {
            expiry = null;
        } else {
            final Duration duration = Durations.parse(text).orElse(null);
            if (duration == null) {
                this.runner.send(context.sender(), MessageKey.COMMAND_INVALID,
                        Placeholder.unparsed("details", text));
                return CompletableFuture.completedFuture(null);
            }
            expiry = punishment.createdAt().plus(duration);
        }
        return this.edit(context, punishment, PunishmentEdit.NewExpiry.of(expiry),
                MessageKey.EDIT_WHAT_EXPIRY);
    }

    private CompletableFuture<?> extend(final CommandContext<CommandActor> context,
            final Punishment punishment) {
        final Instant expiry = punishment.expiresAt().orElse(null);
        if (expiry == null) {
            this.runner.send(context.sender(), MessageKey.EDIT_PERMANENT,
                    this.replies.punishment(context.sender(), punishment));
            return CompletableFuture.completedFuture(null);
        }
        return this.edit(context, punishment, PunishmentEdit.NewExpiry.of(
                expiry.plus(context.<Duration>get("duration"))), MessageKey.EDIT_WHAT_EXPIRY);
    }

    private CompletableFuture<?> edit(final CommandContext<CommandActor> context,
            final Punishment punishment, final PunishmentEdit edit, final MessageKey what) {
        final CommandActor actor = context.sender();
        return this.punishments.edit(EditRequest.builder(punishment.id(), edit,
                actor.asActor()).build()).thenAccept(result -> {
                    switch (result) {
                        case EditResult.Success success -> {
                            final TagResolver values = this.replies.punishment(actor,
                                    success.punishment());
                            this.runner.send(actor, MessageKey.EDIT_DONE, values,
                                    Placeholder.component("what",
                                            this.runner.render(actor, what, values)));
                        }
                        case EditResult.Failure failure -> this.failure(actor, punishment,
                                failure.reason().name());
                    }
                });
    }

    private CompletableFuture<?> delete(final CommandContext<CommandActor> context,
            final Punishment punishment) {
        final CommandActor actor = context.sender();
        final String command = "delete " + punishment.id();
        if (!context.flags().isPresent("confirm")
                || !this.confirmations.confirm(actor.name(), command)) {
            this.confirmations.request(actor.name(), command);
            this.runner.send(actor, MessageKey.DELETE_CONFIRM,
                    this.replies.punishment(actor, punishment));
            return CompletableFuture.completedFuture(null);
        }
        return this.punishments.expunge(punishment.id(), actor.asActor()).thenAccept(result -> {
            switch (result) {
                case ExpungeResult.Success success -> this.runner.send(actor,
                        MessageKey.DELETE_DONE,
                        this.replies.punishment(actor, success.expunged()));
                case ExpungeResult.Failure failure -> this.failure(actor, punishment,
                        failure.reason().name());
            }
        });
    }

    private CompletableFuture<?> evidence(final CommandContext<CommandActor> context,
            final Punishment punishment, final boolean add) {
        final CommandActor actor = context.sender();
        final EvidenceRef ref;
        try {
            ref = EvidenceRef.of(context.get("uri"));
        } catch (final ValidationException e) {
            this.runner.send(actor, MessageKey.COMMAND_INVALID,
                    Placeholder.unparsed("details", e.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<EditResult> result = add
                ? this.punishments.attachEvidence(punishment.id(), ref, actor.asActor())
                : this.punishments.detachEvidence(punishment.id(), ref, actor.asActor());
        return result.thenAccept(outcome -> {
            switch (outcome) {
                case EditResult.Success success -> {
                    final TagResolver values = this.replies.punishment(actor,
                            success.punishment());
                    this.runner.send(actor, MessageKey.EDIT_DONE, values,
                            Placeholder.component("what", this.runner.render(actor, add
                                    ? MessageKey.EDIT_WHAT_EVIDENCE_ADDED
                                    : MessageKey.EDIT_WHAT_EVIDENCE_REMOVED)));
                }
                case EditResult.Failure failure -> this.failure(actor, punishment,
                        failure.reason().name());
            }
        });
    }

    private void failure(final CommandActor actor, final Punishment punishment,
            final String reason) {
        final MessageKey key = switch (reason) {
            case "NOT_FOUND" -> MessageKey.EDIT_FAILURE_NOT_FOUND;
            case "NOT_ACTIVE" -> MessageKey.EDIT_FAILURE_NOT_ACTIVE;
            case "INVALID_EXPIRY" -> MessageKey.EDIT_FAILURE_INVALID_EXPIRY;
            case "ALREADY_PUNISHED" -> MessageKey.EDIT_FAILURE_ALREADY_PUNISHED;
            case "EXEMPT" -> MessageKey.EDIT_FAILURE_EXEMPT;
            case "CANCELLED" -> MessageKey.EDIT_FAILURE_CANCELLED;
            case "CONFLICT" -> MessageKey.EDIT_FAILURE_CONFLICT;
            case "EVIDENCE_ALREADY_ATTACHED" -> MessageKey.EDIT_FAILURE_EVIDENCE_ALREADY_ATTACHED;
            case "EVIDENCE_NOT_ATTACHED" -> MessageKey.EDIT_FAILURE_EVIDENCE_NOT_ATTACHED;
            default -> MessageKey.EDIT_FAILURE_FORBIDDEN;
        };
        this.runner.send(actor, key, this.replies.punishment(actor, punishment));
    }

    private static @Nullable String lower(final @Nullable Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

}
