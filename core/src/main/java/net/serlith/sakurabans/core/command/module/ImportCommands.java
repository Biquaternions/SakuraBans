package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.importer.ImportJobs;
import net.serlith.sakurabans.core.importer.ImportOutcome;
import net.serlith.sakurabans.core.importer.ImportReport;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.Futures;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class ImportCommands implements CommandModule {

    private static final String CONFIRM = "confirm";
    private static final String DRY_RUN = "dry-run";
    private static final int FAILURES_SHOWN = 10;

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final ImportJobs jobs;
    private final ConfirmationTracker confirmations;
    private final Platform platform;

    public ImportCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final ImportJobs jobs, final ConfirmationTracker confirmations,
            final Platform platform) {
        this.registrar = registrar;
        this.runner = runner;
        this.jobs = jobs;
        this.confirmations = confirmations;
        this.platform = platform;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, SakuraCommands.ROOT, MessageKey.COMMAND_DESCRIPTION_IMPORT)
                .ifPresent(root -> manager.command(root.literal("import")
                        .permission(Permissions.ADMIN_IMPORT)
                        .required("source", StringParser.stringParser(),
                                SuggestionProvider.blockingStrings(
                                        (context, input) -> this.jobs.ids()))
                        .flag(CommandFlag.builder(DRY_RUN).build())
                        .flag(CommandFlag.builder(CONFIRM).build())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.importFrom(context)))));
    }

    private CompletableFuture<?> importFrom(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final String source = context.<String>get("source").toLowerCase(Locale.ROOT);
        final TagResolver name = Placeholder.unparsed("source", source);
        if (!this.jobs.ids().contains(source)) {
            this.runner.send(actor, MessageKey.IMPORT_UNKNOWN_SOURCE, name,
                    Placeholder.unparsed("sources", String.join(", ", this.jobs.ids())));
            return CompletableFuture.completedFuture(null);
        }
        final boolean dryRun = context.flags().isPresent(DRY_RUN);
        final String command = "import " + source;
        if (!dryRun && (!context.flags().isPresent(CONFIRM)
                || !this.confirmations.confirm(actor.name(), command))) {
            return this.jobs.survey(source).thenAccept(outcome -> {
                if (outcome instanceof ImportOutcome.Surveyed surveyed) {
                    this.confirmations.request(actor.name(), command);
                    this.runner.send(actor, MessageKey.IMPORT_CONFIRM, name,
                            Placeholder.unparsed("rows", rows(surveyed.rows())));
                } else {
                    this.reply(actor, source, outcome);
                }
            });
        }
        this.runner.send(actor, dryRun ? MessageKey.IMPORT_STARTED_DRY_RUN
                : MessageKey.IMPORT_STARTED, name);
        // The reply comes when the import is done; the command itself returns now.
        this.jobs.run(source, dryRun, actor.asActor()).whenComplete((outcome, failure) -> {
            if (failure != null) {
                this.runner.internalError(actor, Futures.unwrap(failure));
            } else {
                this.reply(actor, source, outcome);
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    private void reply(final CommandActor actor, final String source,
            final ImportOutcome outcome) {
        final TagResolver name = Placeholder.unparsed("source", source);
        switch (outcome) {
            case ImportOutcome.Done done -> this.report(actor, name, done.report());
            case ImportOutcome.Busy busy -> this.runner.send(actor, MessageKey.IMPORT_BUSY);
            case ImportOutcome.Refused refused -> this.runner.send(actor,
                    refusal(refused.problem()), name,
                    Placeholder.unparsed("details", refused.detail()),
                    Placeholder.unparsed("sources", String.join(", ", this.jobs.ids())));
            case ImportOutcome.Surveyed surveyed -> this.runner.send(actor,
                    MessageKey.IMPORT_CONFIRM, name,
                    Placeholder.unparsed("rows", rows(surveyed.rows())));
        }
    }

    private void report(final CommandActor actor, final TagResolver name,
            final ImportReport report) {
        final TagResolver[] all = List.of(name,
                Placeholder.unparsed("imported", Long.toString(report.imported())),
                Placeholder.unparsed("skipped", Long.toString(report.skipped())),
                Placeholder.unparsed("failed", Long.toString(report.failed())),
                Placeholder.unparsed("players", Long.toString(report.players())),
                Placeholder.unparsed("superseded", Long.toString(report.superseded())),
                Placeholder.unparsed("truncated", Long.toString(report.truncated())),
                Placeholder.unparsed("tables", rows(report.tables())))
                .toArray(TagResolver[]::new);
        final MessageKey summary = report.dryRun() ? MessageKey.IMPORT_REPORT_DRY_RUN
                : MessageKey.IMPORT_REPORT;
        this.runner.send(actor, summary, all);
        if (!report.tables().isEmpty()) {
            this.runner.send(actor, MessageKey.IMPORT_REPORT_TABLES, all);
        }
        report.failures().stream().limit(FAILURES_SHOWN).forEach(failure ->
                this.runner.send(actor, MessageKey.IMPORT_REPORT_FAILURE,
                        Placeholder.unparsed("id", failure.externalId() == null
                                ? "#" + failure.index() : failure.externalId()),
                        Placeholder.unparsed("errors", errors(failure))));
        if (!actor.console()) {
            // The console keeps a record of who imported what.
            this.platform.console().sendMessage(this.runner.render(actor, summary, all));
        }
    }

    private static String errors(final ImportReport.Failure failure) {
        return failure.errors().stream()
                .map(error -> error.path() + ": " + error.message())
                .collect(Collectors.joining("; "));
    }

    private static String rows(final Map<String, Long> rows) {
        return rows.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static MessageKey refusal(final ImportOutcome.Problem problem) {
        return switch (problem) {
            case UNKNOWN_SOURCE -> MessageKey.IMPORT_UNKNOWN_SOURCE;
            case NOT_CONFIGURED -> MessageKey.IMPORT_NOT_CONFIGURED;
            case CONFIG_INVALID -> MessageKey.IMPORT_CONFIG_INVALID;
            case UNREACHABLE -> MessageKey.IMPORT_UNREACHABLE;
            case UNREADABLE -> MessageKey.IMPORT_UNREADABLE;
            case NOT_SAKURABANS -> MessageKey.IMPORT_NOT_SAKURABANS;
            case OTHER_VERSION -> MessageKey.IMPORT_OTHER_VERSION;
            case TARGET_NOT_EMPTY -> MessageKey.IMPORT_TARGET_NOT_EMPTY;
            case FAILED -> MessageKey.IMPORT_FAILED;
        };
    }
}
