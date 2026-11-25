package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.time.InstantSource;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.api.wave.BanWaveEntry;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.wave.SakuraBanWaveService;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.LongParser;
import org.incendo.cloud.parser.standard.StringParser;

public final class BanWaveCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Formats formats;
    private final PunishFlags flags;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraBanWaveService waves;
    private final ConfirmationTracker confirmations;
    private final InstantSource clock;

    public BanWaveCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Formats formats, final PunishFlags flags,
            final TargetParser targetParser, final TargetResolver targets,
            final SakuraBanWaveService waves, final ConfirmationTracker confirmations,
            final InstantSource clock) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.formats = formats;
        this.flags = flags;
        this.targetParser = targetParser;
        this.targets = targets;
        this.waves = waves;
        this.confirmations = confirmations;
        this.clock = clock;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "banwave", MessageKey.COMMAND_DESCRIPTION_BANWAVE)
                .ifPresent(root -> {
                    final Command.Builder<CommandActor> base =
                            root.permission(Permissions.ADMIN_BANWAVES);
                    this.add(manager, base.literal("create")
                            .required("name", StringParser.greedyStringParser()), this::create);
                    this.add(manager, base.literal("list"), this::list);
                    this.add(manager, base.literal("info")
                            .required("id", LongParser.longParser()), this::info);
                    this.add(manager, base.literal("add")
                            .required("id", LongParser.longParser())
                            .required("target", this.targetParser.descriptor())
                            .flag(this.flags.duration()).flag(this.flags.reason()),
                            this::addEntry);
                    this.add(manager, base.literal("remove")
                            .required("id", LongParser.longParser())
                            .required("entry", LongParser.longParser()), this::removeEntry);
                    this.add(manager, base.literal("schedule")
                            .required("id", LongParser.longParser())
                            .required("in", DurationParser.descriptor()), this::schedule);
                    this.add(manager, base.literal("unschedule")
                            .required("id", LongParser.longParser()), this::unschedule);
                    this.add(manager, base.literal("release")
                            .required("id", LongParser.longParser())
                            .flag(CommandFlag.builder("confirm").build()), this::release);
                    this.add(manager, base.literal("cancel")
                            .required("id", LongParser.longParser()), this::cancel);
                });
    }

    private void add(final CommandManager<CommandActor> manager,
            final Command.Builder<CommandActor> builder,
            final Function<CommandContext<CommandActor>, CompletableFuture<?>> work) {
        manager.command(builder.futureHandler(context -> this.runner.run(context.sender(),
                () -> work.apply(context))));
    }

    private CompletableFuture<?> create(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.waves.create(context.get("name"), actor.asActor()).thenAccept(result ->
                this.reply(actor, result, wave -> this.runner.send(actor,
                        MessageKey.BANWAVE_CREATED, id(wave),
                        Placeholder.unparsed("name", wave.name()))));
    }

    private CompletableFuture<?> list(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.waves.waves(Set.of()).thenAccept(all -> {
            if (all.isEmpty()) {
                this.runner.send(actor, MessageKey.BANWAVE_LIST_EMPTY);
                return;
            }
            this.runner.send(actor, MessageKey.BANWAVE_LIST_HEADER,
                    Placeholder.unparsed("count", Integer.toString(all.size())));
            for (final BanWave wave : all) {
                this.runner.send(actor, MessageKey.BANWAVE_LIST_ENTRY, this.waveTags(actor,
                        wave));
            }
        });
    }

    private TagResolver waveTags(final CommandActor actor, final BanWave wave) {
        final Locale locale = this.runner.locale(actor);
        final Component scheduled = wave.status() == BanWave.Status.SCHEDULED
                ? this.runner.render(actor, MessageKey.BANWAVE_LIST_SCHEDULED,
                        Placeholder.unparsed("at", this.formats.instant(
                                wave.scheduledAt().orElseThrow(), locale)))
                : Component.empty();
        return TagResolver.resolver(id(wave), Placeholder.unparsed("name", wave.name()),
                Placeholder.unparsed("status", wave.status().name().toLowerCase(Locale.ROOT)),
                Placeholder.unparsed("count", Integer.toString(wave.entryCount())),
                Placeholder.component("scheduled", scheduled));
    }

    private CompletableFuture<?> info(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final long id = context.get("id");
        return this.waves.wave(id).thenCompose(found -> {
            if (found.isEmpty()) {
                this.runner.send(actor, MessageKey.BANWAVE_FAILURE_NOT_FOUND);
                return CompletableFuture.completedFuture(null);
            }
            this.runner.send(actor, MessageKey.BANWAVE_LIST_ENTRY,
                    this.waveTags(actor, found.get()));
            return this.waves.entries(id).thenAccept(entries -> {
                for (final BanWaveEntry entry : entries) {
                    this.runner.send(actor, MessageKey.BANWAVE_INFO_ENTRY,
                            Placeholder.unparsed("entry", Long.toString(entry.id())),
                            Placeholder.unparsed("target", this.replies.victim(actor,
                                    entry.draft().victim())),
                            Placeholder.unparsed("duration", entry.draft().duration()
                                    .map(duration -> this.formats.duration(duration,
                                            this.runner.locale(actor)))
                                    .orElse("permanent")),
                            Placeholder.unparsed("reason", entry.draft().reason().orElse("")),
                            Placeholder.component("result", this.result(actor, entry)));
                }
            });
        });
    }

    private Component result(final CommandActor actor, final BanWaveEntry entry) {
        return entry.result().map(result -> switch (result) {
            case PunishResult.Success success -> this.runner.render(actor,
                    MessageKey.BANWAVE_RESULT_DONE,
                    Placeholder.unparsed("display_id", success.punishment().displayId()));
            case PunishResult.Failure failure -> this.runner.render(actor,
                    MessageKey.BANWAVE_RESULT_FAILED, Placeholder.unparsed("reason",
                            failure.reason().name().toLowerCase(Locale.ROOT)));
        }).orElseGet(() -> this.runner.render(actor, MessageKey.BANWAVE_RESULT_PENDING));
    }

    private CompletableFuture<?> addEntry(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final long id = context.get("id");
        final TargetInput input = context.get("target");
        return this.targets.resolve(input).thenCompose(target -> {
            final Victim victim = switch (target) {
                case ResolvedTarget.Player player -> PlayerVictim.of(player.uuid());
                case ResolvedTarget.Address address -> AddressVictim.of(address.address());
                case ResolvedTarget.Range range -> range.range();
                default -> null;
            };
            if (victim == null) {
                this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                return CompletableFuture.completedFuture(null);
            }
            final Optional<Duration> duration = context.flags().getValue(PunishFlags.DURATION);
            final DraftPunishment draft = DraftPunishment.builder(PunishmentType.BAN, victim,
                            actor.asActor())
                    .duration(duration.orElse(null))
                    .reason(context.flags().<String>getValue(PunishFlags.REASON)
                            .map(String::strip).filter(text -> !text.isEmpty()).orElse(null))
                    .build();
            return this.waves.addEntry(id, draft, PunishOptions.defaults(), actor.asActor())
                    .thenCompose(result -> this.waves.wave(id).thenAccept(wave -> this.reply(
                            actor, result, entry -> this.runner.send(actor,
                                    MessageKey.BANWAVE_ADDED,
                                    Placeholder.unparsed("id", Long.toString(id)),
                                    Placeholder.unparsed("entry", Long.toString(entry.id())),
                                    Placeholder.unparsed("target",
                                            this.replies.victim(actor, victim)),
                                    Placeholder.unparsed("count", Integer.toString(
                                            wave.map(BanWave::entryCount).orElse(0)))))));
        });
    }

    private CompletableFuture<?> removeEntry(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        final long id = context.get("id");
        final long entry = context.get("entry");
        return this.waves.removeEntry(id, entry, actor.asActor()).thenAccept(result ->
                this.reply(actor, result, removed -> this.runner.send(actor,
                        MessageKey.BANWAVE_REMOVED,
                        Placeholder.unparsed("id", Long.toString(id)),
                        Placeholder.unparsed("entry", Long.toString(entry)))));
    }

    private CompletableFuture<?> schedule(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.current(context, wave -> this.waves.schedule(wave.id(),
                this.clock.instant().plus(context.<Duration>get("in")), wave.version(),
                actor.asActor()).thenAccept(result -> this.reply(actor, result, scheduled ->
                this.runner.send(actor, MessageKey.BANWAVE_SCHEDULED, id(scheduled),
                        Placeholder.unparsed("at", this.formats.instant(
                                scheduled.scheduledAt().orElseThrow(),
                                this.runner.locale(actor)))))));
    }

    private CompletableFuture<?> unschedule(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.current(context, wave -> this.waves.schedule(wave.id(), null,
                wave.version(), actor.asActor()).thenAccept(result -> this.reply(actor, result,
                        draft -> this.runner.send(actor, MessageKey.BANWAVE_UNSCHEDULED,
                                id(draft)))));
    }

    private CompletableFuture<?> release(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.current(context, wave -> {
            final String command = "banwave release " + wave.id();
            if (!context.flags().isPresent("confirm")
                    || !this.confirmations.confirm(actor.name(), command)) {
                this.confirmations.request(actor.name(), command);
                this.runner.send(actor, MessageKey.BANWAVE_CONFIRM, id(wave),
                        Placeholder.unparsed("count", Integer.toString(wave.entryCount())));
                return CompletableFuture.completedFuture(null);
            }
            return this.waves.release(wave.id(), wave.version(), actor.asActor())
                    .thenCompose(result -> this.waves.entries(wave.id()).thenAccept(entries -> {
                        final long done = entries.stream().filter(entry -> entry.result()
                                .filter(PunishResult.Success.class::isInstance).isPresent())
                                .count();
                        this.reply(actor, result, released -> this.runner.send(actor,
                                MessageKey.BANWAVE_RELEASED, id(released),
                                Placeholder.unparsed("done", Long.toString(done)),
                                Placeholder.unparsed("failed",
                                        Long.toString(entries.size() - done))));
                    }));
        });
    }

    private CompletableFuture<?> cancel(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        return this.current(context, wave -> this.waves.cancel(wave.id(), wave.version(),
                actor.asActor()).thenAccept(result -> this.reply(actor, result, cancelled ->
                this.runner.send(actor, MessageKey.BANWAVE_CANCELLED, id(cancelled)))));
    }

    private CompletableFuture<?> current(final CommandContext<CommandActor> context,
            final Function<BanWave, CompletableFuture<?>> work) {
        final long id = context.get("id");
        return this.waves.wave(id).thenCompose(found -> {
            if (found.isEmpty()) {
                this.runner.send(context.sender(), MessageKey.BANWAVE_FAILURE_NOT_FOUND);
                return CompletableFuture.completedFuture(null);
            }
            return work.apply(found.get());
        });
    }

    private <T> void reply(final CommandActor actor, final AdminResult<T> result,
            final Consumer<T> ok) {
        switch (result) {
            case AdminResult.Ok<T> done -> ok.accept(done.value());
            case AdminResult.NotFound<T> notFound -> this.runner.send(actor,
                    MessageKey.BANWAVE_FAILURE_NOT_FOUND);
            case AdminResult.Conflict<T> conflict -> this.runner.send(actor,
                    MessageKey.BANWAVE_FAILURE_CONFLICT);
            case AdminResult.Forbidden<T> forbidden -> this.runner.send(actor,
                    MessageKey.BANWAVE_FAILURE_FORBIDDEN);
            case AdminResult.ValidationFailed<T> invalid -> this.runner.send(actor,
                    MessageKey.BANWAVE_FAILURE_INVALID, Placeholder.unparsed("details",
                            String.join("; ", invalid.errors().stream()
                                    .map(error -> error.path() + " " + error.message())
                                    .toList())));
        }
    }

    private static TagResolver id(final BanWave wave) {
        return Placeholder.unparsed("id", Long.toString(wave.id()));
    }

}
