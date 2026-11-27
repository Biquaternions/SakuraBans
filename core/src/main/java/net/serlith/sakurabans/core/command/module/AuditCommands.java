package net.serlith.sakurabans.core.command.module;

import com.google.gson.stream.JsonWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.command.AuditLines;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Pager;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.AuditFilter;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.util.Durations;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jspecify.annotations.Nullable;

public final class AuditCommands implements CommandModule {

    static final int PAGE_SIZE = 10;
    static final int EXPORT_BATCH = 500;
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String CONSOLE = "console";
    private static final String ACTOR = "actor";
    private static final String PLAYER = "player";
    private static final String ID = "id";
    private static final String TEMPLATE = "template";
    private static final String SETTING = "setting";
    private static final String ACTION = "action";
    private static final String SINCE = "since";

    private static final List<String> FLAGS =
            List.of(ACTOR, PLAYER, ID, TEMPLATE, SETTING, ACTION, SINCE);

    private record Query(AuditFilter filter, String suffix) {
    }

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final AuditLines lines;
    private final Pager pager;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final AuditRepository audit;
    private final DisplayIdCodec displayIds;
    private final SakuraTemplateService templates;
    private final Path dataDirectory;
    private final Executor worker;
    private final InstantSource clock;

    public AuditCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final AuditLines lines, final Pager pager,
            final TargetParser targetParser, final TargetResolver targets,
            final AuditRepository audit, final DisplayIdCodec displayIds,
            final SakuraTemplateService templates, final Path dataDirectory,
            final Executor worker, final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.lines = lines;
        this.pager = pager;
        this.targetParser = targetParser;
        this.targets = targets;
        this.audit = audit;
        this.displayIds = displayIds;
        this.templates = templates;
        this.dataDirectory = dataDirectory;
        this.worker = worker;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_AUDIT)
                .ifPresent(root -> {
                    final var audit = root.literal("audit").permission(Permissions.ADMIN_AUDIT);
                    manager.command(this.filters(audit.literal("export"))
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.export(context))));
                    // A flag-yielding text, not an integer: "audit --actor x" has no page.
                    manager.command(this.filters(audit.optional("page",
                                    StringParser.greedyFlagYieldingStringParser(),
                                    SuggestionProvider.blockingStrings((context, input) ->
                                            FLAGS.stream().map(flag -> "--" + flag).toList())))
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.list(context))));
                });
    }

    private Command.Builder<CommandActor> filters(
            final Command.Builder<CommandActor> builder) {
        return builder
                .flag(this.flag(ACTOR, this.actors()))
                .flag(this.flag(PLAYER, this.targetParser.suggestionProvider()))
                .flag(this.flag(ID, SuggestionProvider.blockingStrings((context, input) ->
                        List.of())))
                .flag(this.flag(TEMPLATE, SuggestionProvider.blockingStrings((context, input) ->
                        this.templates.templates().stream().map(template -> template.name())
                                .distinct().toList())))
                .flag(this.flag(SETTING, SuggestionProvider.blockingStrings((context, input) ->
                        SettingsSchema.ENTRIES.stream().map(SettingsSchema.Entry::key)
                                .toList())))
                .flag(this.flag(ACTION, SuggestionProvider.blockingStrings((context, input) ->
                        Arrays.stream(AuditAction.values())
                                .map(action -> action.name().toLowerCase(Locale.ROOT))
                                .toList())))
                .flag(this.flag(SINCE, DurationParser.descriptor().parser()
                        .suggestionProvider()));
    }

    private CommandFlag<String> flag(final String name,
            final SuggestionProvider<CommandActor> suggestions) {
        return CommandFlag.<CommandActor>builder(name)
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(name)
                        .parser(StringParser.<CommandActor>stringParser())
                        .suggestionProvider(suggestions))
                .build();
    }

    private SuggestionProvider<CommandActor> actors() {
        final SuggestionProvider<CommandActor> players = this.targetParser.suggestionProvider();
        return SuggestionProvider.blockingStrings((context, input) -> {
            final List<String> names = new ArrayList<>(List.of(CONSOLE));
            players.suggestionsFuture(context, input).join()
                    .forEach(suggestion -> names.add(suggestion.suggestion()));
            return names;
        });
    }

    private CompletableFuture<?> list(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String pageText = context.<String>optional("page").filter(text -> !text.isBlank())
                .orElse("1");
        final int page = pageNumber(pageText);
        if (page < 1) {
            this.runner.send(actor, MessageKey.COMMAND_INVALID,
                    Placeholder.unparsed("details", pageText));
            return CompletableFuture.completedFuture(null);
        }
        return this.query(context).thenCompose(query -> {
            if (query.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return this.audit.search(query.get().filter(), page, PAGE_SIZE)
                    .thenAccept(found -> this.pager.send(actor, MessageKey.AUDIT_HEADER,
                            TagResolver.empty(),
                            found, this.registrar.label(SakuraCommands.ROOT)
                                    .orElse(SakuraCommands.ROOT) + " audit",
                            query.get().suffix(), entry -> this.runner.render(actor,
                                    MessageKey.AUDIT_ENTRY, this.lines.values(actor, entry))));
        });
    }

    private static int pageNumber(final String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private CompletableFuture<?> export(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.query(context).thenCompose(query -> {
            if (query.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            final boolean show = this.replies.showAddresses(actor);
            return CompletableFuture.supplyAsync(() -> this.write(query.get().filter(), show),
                    this.worker).thenAccept(done -> this.runner.send(actor,
                    MessageKey.AUDIT_EXPORTED,
                    Placeholder.unparsed("count", Long.toString(done.count())),
                    Placeholder.unparsed("file", "exports/" + done.file().getFileName())));
        });
    }

    private record Written(Path file, long count) {
    }

    /** Batch by batch, so the log is never in memory whole. */
    private Written write(final AuditFilter filter, final boolean show) {
        final Path file = this.dataDirectory.resolve("exports").resolve("audit-"
                + FILE_TIME.format(this.clock.instant()) + ".json");
        long count = 0;
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                    JsonWriter json = new JsonWriter(out)) {
                json.setIndent("  ");
                json.setSerializeNulls(true);
                json.beginObject();
                json.name("exportedAt").value(this.clock.instant().toString());
                json.name("entries").beginArray();
                AuditEntry last = null;
                List<AuditEntry> batch;
                do {
                    batch = this.audit.batch(filter, last, EXPORT_BATCH).join();
                    for (final AuditEntry entry : batch) {
                        this.write(json, entry, show);
                        last = entry;
                    }
                    count += batch.size();
                } while (batch.size() == EXPORT_BATCH);
                json.endArray();
                json.name("count").value(count);
                json.endObject();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Written(file, count);
    }

    private void write(final JsonWriter json, final AuditEntry entry, final boolean show)
            throws IOException {
        json.beginObject();
        json.name("id").value(entry.id());
        json.name("at").value(entry.at().toString());
        final Operator operator = entry.actor().operator();
        json.name("actor").beginObject();
        json.name("kind").value(switch (operator) {
            case ConsoleOperator console -> CONSOLE;
            case PlayerOperator player -> PLAYER;
            case CustomOperator custom -> "custom";
        });
        json.name("name").value(switch (operator) {
            case ConsoleOperator console -> CONSOLE;
            case PlayerOperator player -> player.name();
            case CustomOperator custom -> custom.name();
        });
        final UUID uuid = switch (operator) {
            case ConsoleOperator console -> null;
            case PlayerOperator player -> player.uuid();
            case CustomOperator custom -> custom.uuid().orElse(null);
        };
        json.name("uuid").value(uuid == null ? null : uuid.toString());
        json.name("source").value(entry.actor().source());
        json.endObject();
        json.name("action").value(entry.action().name().toLowerCase(Locale.ROOT));
        json.name("subjectType").value(entry.subjectType().name().toLowerCase(Locale.ROOT));
        json.name("subject").value(this.lines.exportSubject(entry, show));
        json.name("old").value(entry.oldValue() == null ? null
                : this.lines.text(entry.oldValue(), show));
        json.name("new").value(entry.newValue() == null ? null
                : this.lines.text(entry.newValue(), show));
        json.endObject();
    }

    /** The filters as a query, or empty after telling the sender what is wrong with them. */
    private CompletableFuture<Optional<Query>> query(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final StringBuilder suffix = new StringBuilder();
        final String actorText = this.text(context, ACTOR, suffix);
        final String playerText = this.text(context, PLAYER, suffix);
        final String idText = this.text(context, ID, suffix);
        final String template = this.text(context, TEMPLATE, suffix);
        final String setting = this.text(context, SETTING, suffix);
        final String actionText = this.text(context, ACTION, suffix);
        final String sinceText = this.text(context, SINCE, suffix);

        AuditAction action = null;
        if (actionText != null) {
            action = Arrays.stream(AuditAction.values()).filter(found -> found.name()
                    .equalsIgnoreCase(actionText.strip())).findFirst().orElse(null);
            if (action == null) {
                this.runner.send(actor, MessageKey.AUDIT_UNKNOWN_ACTION,
                        Placeholder.unparsed("input", actionText),
                        Placeholder.unparsed("actions", String.join(", ", Arrays
                                .stream(AuditAction.values()).map(found -> found.name()
                                        .toLowerCase(Locale.ROOT)).toList())));
                return CompletableFuture.completedFuture(Optional.empty());
            }
        }
        Instant since = null;
        if (sinceText != null) {
            final Optional<Duration> duration = Durations.parse(sinceText);
            if (duration.isEmpty()) {
                this.runner.send(actor, MessageKey.COMMAND_INVALID,
                        Placeholder.unparsed("details", sinceText));
                return CompletableFuture.completedFuture(Optional.empty());
            }
            since = this.clock.instant().minus(duration.get());
        }
        Long punishment = null;
        if (idText != null) {
            final OptionalLong id = this.displayIds.decode(idText.startsWith("#")
                    ? idText.substring(1) : idText);
            if (id.isEmpty()) {
                this.runner.send(actor, MessageKey.COMMAND_NOT_AN_ID,
                        Placeholder.unparsed("input", idText));
                return CompletableFuture.completedFuture(Optional.empty());
            }
            punishment = id.getAsLong();
        }
        final AuditAction chosen = action;
        final Instant from = since;
        final Long id = punishment;
        final boolean console = actorText != null && actorText.equalsIgnoreCase(CONSOLE);
        return this.resolve(actor, console ? null : actorText)
                .thenCompose(actorUuid -> this.resolve(actor, playerText)
                        .thenApply(playerUuid -> {
                            if (actorUuid == null && !console && actorText != null
                                    || playerUuid == null && playerText != null) {
                                return Optional.<Query>empty();
                            }
                            return Optional.of(new Query(new AuditFilter(actorUuid, console,
                                    playerUuid, id, template, setting, chosen, from),
                                    suffix.toString()));
                        }));
    }

    private @Nullable String text(final CommandContext<CommandActor> context, final String flag,
            final StringBuilder suffix) {
        final String value = context.flags().<String>getValue(flag).orElse(null);
        if (value != null) {
            suffix.append(" --").append(flag).append(' ').append(value);
        }
        return value;
    }

    /** The player's id; null when nothing was asked, or after telling the sender it's unknown. */
    private CompletableFuture<@Nullable UUID> resolve(final CommandActor actor,
            final @Nullable String text) {
        if (text == null) {
            return CompletableFuture.completedFuture(null);
        }
        final Optional<TargetInput> input = TargetParser.parse(text, this.displayIds);
        if (input.isEmpty() || input.get() instanceof TargetInput.IdInput) {
            this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                    Placeholder.unparsed("input", text));
            return CompletableFuture.completedFuture(null);
        }
        return this.targets.resolve(input.get()).thenApply(target -> {
            if (target instanceof ResolvedTarget.Player player) {
                return player.uuid();
            }
            this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                    Placeholder.unparsed("input", text));
            return null;
        });
    }
}
