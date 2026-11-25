package net.serlith.sakurabans.core.command.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.data.SakuraPlayerDataService;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.storage.repository.AuditValues;
import net.serlith.sakurabans.core.util.JsonWriter;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jspecify.annotations.Nullable;

public final class GdprCommands implements CommandModule {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPlayerDataService data;
    private final ConfirmationTracker confirmations;
    private final Path dataDirectory;
    private final Executor worker;
    private final InstantSource clock;

    public GdprCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final TargetParser targetParser, final TargetResolver targets,
            final SakuraPlayerDataService data, final ConfirmationTracker confirmations,
            final Path dataDirectory, final Executor worker, final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.targetParser = targetParser;
        this.targets = targets;
        this.data = data;
        this.confirmations = confirmations;
        this.dataDirectory = dataDirectory;
        this.worker = worker;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_GDPR)
                .ifPresent(root -> {
                    final var gdpr = root.literal("gdpr").permission(Permissions.ADMIN_GDPR);
                    manager.command(gdpr.literal("export")
                            .required("target", this.targetParser.descriptor())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.export(context))));
                    manager.command(gdpr.literal("erase")
                            .required("target", this.targetParser.descriptor())
                            .flag(CommandFlag.builder("confirm").build())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.erase(context))));
                });
    }

    private CompletableFuture<ResolvedTarget.@Nullable Player> player(
            final CommandContext<CommandActor> context) {
        final TargetInput input = context.get("target");
        return this.targets.resolve(input).thenApply(target -> {
            if (target instanceof ResolvedTarget.Player player) {
                return player;
            }
            this.runner.send(context.sender(), MessageKey.COMMAND_PLAYER_REQUIRED,
                    Placeholder.unparsed("input", input.toString()));
            return null;
        });
    }

    private CompletableFuture<?> export(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.player(context).thenCompose(player -> player == null
                ? CompletableFuture.completedFuture(null)
                : this.data.export(player.uuid(), actor.asActor()).thenComposeAsync(result -> {
                    if (!(result instanceof AdminResult.Ok<PlayerDataExport> ok)) {
                        this.runner.send(actor, MessageKey.GDPR_FAILURE_FORBIDDEN);
                        return CompletableFuture.completedFuture(null);
                    }
                    final Path file = this.write(ok.value());
                    this.runner.send(actor, MessageKey.GDPR_EXPORTED,
                            Placeholder.unparsed("name", name(player)),
                            Placeholder.unparsed("file", "exports/" + file.getFileName()),
                            Placeholder.unparsed("received",
                                    Integer.toString(ok.value().received().size())),
                            Placeholder.unparsed("issued",
                                    Integer.toString(ok.value().issued().size())));
                    return CompletableFuture.completedFuture(null);
                }, this.worker));
    }

    private CompletableFuture<?> erase(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.player(context).thenCompose(player -> {
            if (player == null) {
                return CompletableFuture.completedFuture(null);
            }
            final String command = "gdpr erase " + player.uuid();
            if (!context.flags().isPresent("confirm")
                    || !this.confirmations.confirm(actor.name(), command)) {
                this.confirmations.request(actor.name(), command);
                this.runner.send(actor, MessageKey.GDPR_ERASE_CONFIRM,
                        Placeholder.unparsed("name", name(player)),
                        Placeholder.unparsed("uuid", player.uuid().toString()));
                return CompletableFuture.completedFuture(null);
            }
            return this.data.erase(player.uuid(), actor.asActor()).thenAccept(result -> {
                if (!(result instanceof AdminResult.Ok<EraseResult> ok)) {
                    this.runner.send(actor, MessageKey.GDPR_FAILURE_FORBIDDEN);
                    return;
                }
                final EraseResult erased = ok.value();
                this.runner.send(actor, MessageKey.GDPR_ERASED,
                        Placeholder.unparsed("name", name(player)),
                        Placeholder.unparsed("names", Long.toString(erased.namesDeleted())),
                        Placeholder.unparsed("addresses",
                                Long.toString(erased.addressesDeleted())),
                        Placeholder.unparsed("expunged",
                                Long.toString(erased.punishmentsExpunged())),
                        Placeholder.unparsed("anonymised",
                                Long.toString(erased.punishmentsAnonymised())),
                        Placeholder.unparsed("issued", Long.toString(erased.issuedAnonymised())),
                        Placeholder.unparsed("other", Long.toString(erased.otherDeleted())));
            });
        });
    }

    private static String name(final ResolvedTarget.Player player) {
        return player.name() == null ? player.uuid().toString() : player.name();
    }

    private Path write(final PlayerDataExport export) {
        final JsonWriter json = new JsonWriter().beginObject()
                .field("uuid", export.uuid())
                .field("exportedAt", export.exportedAt());
        json.name("profile");
        if (export.profile().isPresent()) {
            json.beginObject()
                    .field("name", export.profile().get().name())
                    .field("firstSeen", export.profile().get().firstSeen())
                    .field("lastSeen", export.profile().get().lastSeen())
                    .endObject();
        } else {
            json.value(null);
        }
        json.name("names").beginArray();
        for (final NameRecord name : export.names()) {
            json.beginObject().field("name", name.name()).field("firstSeen", name.firstSeen())
                    .field("lastSeen", name.lastSeen()).endObject();
        }
        json.endArray().name("addresses").beginArray();
        for (final AddressRecord address : export.addresses()) {
            json.beginObject().field("address", address.address().getHostAddress())
                    .field("firstSeen", address.firstSeen()).field("lastSeen", address.lastSeen())
                    .field("lastServer", address.lastServer().orElse(null)).endObject();
        }
        json.endArray();
        punishments(json, "received", export.received());
        punishments(json, "issued", export.issued());
        json.name("allowList").beginArray();
        for (final AllowEntry entry : export.allowEntries()) {
            json.beginObject().field("addedAt", entry.addedAt())
                    .field("note", entry.note().orElse(null)).endObject();
        }
        json.endArray().name("appeals").beginArray();
        for (final Appeal appeal : export.appeals()) {
            json.beginObject().field("id", appeal.id())
                    .field("punishment", appeal.punishmentId())
                    .field("message", appeal.message())
                    .field("createdAt", appeal.createdAt())
                    .field("status", appeal.status()).endObject();
        }
        json.endArray().name("audit").beginArray();
        for (final AuditEntry entry : export.auditEntries()) {
            json.beginObject().field("at", entry.at()).field("action", entry.action())
                    .field("subject", entry.subjectType() + " " + entry.subjectId())
                    .field("old", entry.oldValue().orElse(null))
                    .field("new", entry.newValue().orElse(null)).endObject();
        }
        json.endArray().endObject();
        final Path file = this.dataDirectory.resolve("exports").resolve("player-"
                + export.uuid() + "-" + FILE_TIME.format(this.clock.instant()) + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static void punishments(final JsonWriter json, final String name,
            final List<Punishment> punishments) {
        json.name(name).beginArray();
        for (final Punishment punishment : punishments) {
            json.beginObject()
                    .field("id", punishment.id())
                    .field("type", punishment.type())
                    .field("kind", punishment.kind())
                    .field("victim", AuditValues.victim(punishment.victim()))
                    .field("scope", AuditValues.scope(punishment.scope()))
                    .field("reason", punishment.reason())
                    .field("createdAt", punishment.createdAt())
                    .field("expiresAt", punishment.expiresAt().orElse(null))
                    .field("ended", punishment.end().map(end -> end.kind().name())
                            .orElse(null))
                    .field("voided", punishment.voiding().isPresent())
                    .endObject();
        }
        json.endArray();
    }
}
