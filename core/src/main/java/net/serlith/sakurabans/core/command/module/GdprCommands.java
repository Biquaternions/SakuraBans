package net.serlith.sakurabans.core.command.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
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
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jspecify.annotations.Nullable;

public final class GdprCommands implements CommandModule {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls()
            .disableHtmlEscaping().create();
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
                        Placeholder.unparsed("names", Long.toString(erased.namesRemoved())),
                        Placeholder.unparsed("addresses",
                                Long.toString(erased.addressesRemoved())),
                        Placeholder.unparsed("allowed",
                                Long.toString(erased.allowedAddressesRemoved())),
                        Placeholder.unparsed("cleared",
                                Long.toString(erased.victimAddressesCleared())),
                        Placeholder.unparsed("evidence", Long.toString(erased.evidenceRemoved())),
                        Placeholder.unparsed("redacted", Long.toString(erased.namesRedacted())),
                        Placeholder.unparsed("kept", Long.toString(erased.punishmentsKept())));
            });
        });
    }

    private static String name(final ResolvedTarget.Player player) {
        return player.name() == null ? player.uuid().toString() : player.name();
    }

    private Path write(final PlayerDataExport export) {
        final JsonObject json = new JsonObject();
        json.addProperty("uuid", export.uuid().toString());
        json.addProperty("exportedAt", export.exportedAt().toString());
        json.add("profile", export.profile().<JsonElement>map(profile -> {
            final JsonObject object = new JsonObject();
            object.addProperty("name", profile.name());
            object.addProperty("firstSeen", profile.firstSeen().toString());
            object.addProperty("lastSeen", profile.lastSeen().toString());
            return object;
        }).orElse(JsonNull.INSTANCE));
        json.add("names", array(export.names(), name -> {
            final JsonObject object = new JsonObject();
            object.addProperty("name", name.name());
            object.addProperty("firstSeen", name.firstSeen().toString());
            object.addProperty("lastSeen", name.lastSeen().toString());
            return object;
        }));
        json.add("addresses", array(export.addresses(), address -> {
            final JsonObject object = new JsonObject();
            object.addProperty("address", address.address().getHostAddress());
            object.addProperty("firstSeen", address.firstSeen().toString());
            object.addProperty("lastSeen", address.lastSeen().toString());
            object.addProperty("lastServer", address.lastServer().orElse(null));
            return object;
        }));
        json.add("received", array(export.received(), GdprCommands::punishment));
        json.add("issued", array(export.issued(), GdprCommands::punishment));
        json.add("allowList", array(export.allowEntries(), entry -> {
            final JsonObject object = new JsonObject();
            object.addProperty("addedAt", entry.addedAt().toString());
            object.addProperty("note", entry.note().orElse(null));
            return object;
        }));
        json.add("audit", array(export.auditEntries(), entry -> {
            final JsonObject object = new JsonObject();
            object.addProperty("at", entry.at().toString());
            object.addProperty("action", entry.action().name());
            object.addProperty("subject", entry.subjectType() + " " + entry.subjectId());
            object.addProperty("old", entry.oldValue().orElse(null));
            object.addProperty("new", entry.newValue().orElse(null));
            return object;
        }));
        final Path file = this.dataDirectory.resolve("exports").resolve("player-"
                + export.uuid() + "-" + FILE_TIME.format(this.clock.instant()) + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static <T> JsonArray array(final List<T> values,
            final Function<T, JsonObject> element) {
        final JsonArray array = new JsonArray(values.size());
        values.forEach(value -> array.add(element.apply(value)));
        return array;
    }

    private static JsonObject punishment(final Punishment punishment) {
        final JsonObject object = new JsonObject();
        object.addProperty("id", punishment.id());
        object.addProperty("type", punishment.type().name());
        object.addProperty("kind", punishment.kind());
        object.addProperty("victim", AuditValues.victim(punishment.victim()));
        object.addProperty("scope", AuditValues.scope(punishment.scope()));
        object.addProperty("reason", punishment.reason());
        object.addProperty("createdAt", punishment.createdAt().toString());
        object.addProperty("expiresAt", punishment.expiresAt().map(Instant::toString).orElse(null));
        object.addProperty("ended", punishment.end().map(end -> end.kind().name()).orElse(null));
        object.addProperty("voided", punishment.voiding().isPresent());
        return object;
    }

}
