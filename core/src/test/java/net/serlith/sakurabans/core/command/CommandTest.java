package net.serlith.sakurabans.core.command;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.admin.EditorClient;
import net.serlith.sakurabans.core.admin.EditorLines;
import net.serlith.sakurabans.core.admin.EditorSeal;
import net.serlith.sakurabans.core.admin.EditorService;
import net.serlith.sakurabans.core.admin.EditorSocket;
import net.serlith.sakurabans.core.admin.EditorWatcher;
import net.serlith.sakurabans.core.admin.FakeEditorSockets;
import net.serlith.sakurabans.core.admin.InstanceRegistry;
import net.serlith.sakurabans.core.command.module.AdminCommands;
import net.serlith.sakurabans.core.command.module.AllowCommands;
import net.serlith.sakurabans.core.command.module.AltCommands;
import net.serlith.sakurabans.core.command.module.CheckCommands;
import net.serlith.sakurabans.core.command.module.EditCommands;
import net.serlith.sakurabans.core.command.module.EditorCommands;
import net.serlith.sakurabans.core.command.module.GdprCommands;
import net.serlith.sakurabans.core.command.module.HistoryCommands;
import net.serlith.sakurabans.core.command.module.ImportCommands;
import net.serlith.sakurabans.core.command.module.LadderCommands;
import net.serlith.sakurabans.core.command.module.OverrideCommands;
import net.serlith.sakurabans.core.command.module.PunishCommands;
import net.serlith.sakurabans.core.command.module.RevokeCommands;
import net.serlith.sakurabans.core.command.module.TemplateCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.importer.ImportSources;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.player.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.player.UuidResolver;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.EditorSessionRepository;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** The command layer of {@code CoreComponents}, installed on a {@link TestCommandManager}. */
public abstract class CommandTest extends ServiceTest {

    private static final InetAddress SENDER_ADDRESS =
            InetAddresses.parseLiteral("203.0.113.253").orElseThrow();

    protected final TestCommandManager manager = new TestCommandManager();
    protected final FakeCommandActor console = FakeCommandActor.theConsole();
    protected InstanceRegistry registry;
    protected EditorClient editorClient;
    protected EditorService editor;
    protected final FakeEditorSockets editorSockets = new FakeEditorSockets();
    protected EditorLines editorLines;
    protected EditorWatcher editorWatcher;

    @AfterEach
    final void stopEditorClient() {
        this.editorWatcher.stop();
        this.registry.stop();
        this.editorClient.stop();
    }

    @BeforeEach
    final void installCommands() {
        this.registry = new InstanceRegistry(this.dir, this.config, this.platform,
                new InstanceRepository(this.storage), this.executors, this.clock,
                this.log.logger());
        this.editorClient = new EditorClient(this.config, this.executors.worker(),
                this.log.logger());
        this.editor = new EditorService(this.config, this.settingsService, this.messageTemplates,
                this.templates, new EditorSessionRepository(this.storage), this.editorClient,
                new EditorSeal(new SecureRandom()), this.platform, this.registry, this.clock,
                this.log.logger());
        this.editorLines = new EditorLines(this.messages, this.platform);
        this.editorWatcher = new EditorWatcher(this.config, this.editor, this.editorConnector(),
                this.editorLines, this.platform, this.executors, this.editorTiming(), this.clock,
                this.log.logger());
        final CommandRegistrar registrar = new CommandRegistrar(this.config, this.messages);
        final CommandRunner runner =
                new CommandRunner(this.lifecycle, this.messages, this.log.logger());
        final Replies replies =
                new Replies(runner, this.placeholders, this.names, this.config, this.clock);
        final TargetParser targetParser =
                new TargetParser(this.platform, this.names, this.lifecycle, this.displayIds);
        final PunishFlags flags = new PunishFlags(new ScopeParser(this.scopes));
        final Pager pager = new Pager(runner, replies, this.clock);
        final TargetResolver targets = new TargetResolver(this.platform, this.names,
                this.players, new UuidResolver(new SakuraIntegrationRegistry(), this.platform,
                        this.config, this.log.logger()));
        new SakuraCommands(List.of(
                new PunishCommands(registrar, runner, replies, flags, targetParser, targets,
                        this.service, this.names, this.config, this.scopes,
                        new ScopeAccess(this.config), this.templates),
                new LadderCommands(registrar, runner, replies, flags, targetParser, targets,
                        this.service, this.names, this.scopes, new ScopeAccess(this.config),
                        this.templates),
                new RevokeCommands(registrar, runner, replies, flags, targetParser, targets,
                        this.service, this.scopes, new ScopeAccess(this.config)),
                new CheckCommands(registrar, runner, replies, pager, targetParser, targets,
                        this.service, this.config),
                new HistoryCommands(registrar, runner, pager, targetParser, targets,
                        this.service),
                new AltCommands(registrar, runner, replies, new Formats(this.messages,
                        this.config), targetParser, targets, this.players, this.clock,
                        this.altService, this.platform, this.config),
                new AllowCommands(registrar, runner, replies, new Formats(this.messages,
                        this.config), targetParser, targets, this.allowList, this.names,
                        this.placeholders),
                new TemplateCommands(registrar, runner, this.templates,
                        new TemplateFiles(this.dir), new ConfirmationTracker(this.clock),
                        this.executors.worker(), this.clock, targetParser, targets,
                        new ScopeParser(this.scopes)),
                new EditCommands(registrar, runner, replies, this.placeholders,
                        new Formats(this.messages, this.config), this.service,
                        new AuditRepository(this.storage),
                        this.displayIds, new ScopeParser(this.scopes),
                        new ConfirmationTracker(this.clock), this.clock),
                new GdprCommands(registrar, runner, targetParser, targets, this.playerData,
                        new ConfirmationTracker(this.clock), this.dir, Runnable::run,
                        this.clock),
                new AdminCommands(registrar, runner, replies, this.placeholders,
                        new Formats(this.messages, this.config), flags, targetParser, targets,
                        this.service, this.punishments, this.lockdowns, this.scopes,
                        this.config, this.messages, this.platform,
                        new ConfirmationTracker(this.clock), this.settingsService,
                        this.messageTemplates, this.registry, Runnable::run, this.clock,
                        this.log.logger()),
                new OverrideCommands(registrar, runner, this.settingsService,
                        this.messageTemplates, new ConfirmationTracker(this.clock),
                        new ScopeParser(this.scopes), this.registry),
                new EditorCommands(registrar, runner, this.editor, this.editorWatcher,
                        this.editorLines, new Formats(this.messages, this.config)),
                new ImportCommands(registrar, runner, this.importJobs(this.importSources()),
                        new ConfirmationTracker(this.clock), this.platform)),
                registrar, runner, this.messages).install(this.manager);
    }

    /** The watcher's network: the in-memory sockets unless a test talks to a real site. */
    protected EditorSocket.Connector editorConnector() {
        return this.editorSockets;
    }

    /** Quick retries; pings and checks only when a test asks. */
    protected EditorWatcher.Timing editorTiming() {
        return new EditorWatcher.Timing(Duration.ofHours(1), Duration.ofHours(1),
                Duration.ofMinutes(2), List.of(Duration.ofMillis(10), Duration.ofMillis(20)));
    }

    protected ImportSources importSources() {
        return ImportSources.standard();
    }

    protected final void known(final UUID uuid, final String name) {
        this.players.recordConnection(uuid, name, this.now.get(), this.home, null).join();
    }

    /**
     * Runs a command line. A player sender is online, as in game, with its permissions, so the
     * services it calls authorize it the same way.
     */
    protected final void run(final CommandActor actor, final String line) {
        if (actor instanceof FakeCommandActor fake && !fake.console()
                && fake.uuid().isPresent()) {
            final UUID uuid = fake.uuid().get();
            final String[] nodes = fake.permissions().toArray(String[]::new);
            this.platform.online(uuid).ifPresentOrElse(online -> online.grant(nodes),
                    () -> this.platform.join(uuid, fake.name(), SENDER_ADDRESS).grant(nodes));
        }
        this.manager.run(actor, line);
    }
}
