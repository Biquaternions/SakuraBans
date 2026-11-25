package net.serlith.sakurabans.core.command;

import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.command.module.AllowCommands;
import net.serlith.sakurabans.core.command.module.AltCommands;
import net.serlith.sakurabans.core.command.module.CheckCommands;
import net.serlith.sakurabans.core.command.module.HistoryCommands;
import net.serlith.sakurabans.core.command.module.KindCommands;
import net.serlith.sakurabans.core.command.module.PunishCommands;
import net.serlith.sakurabans.core.command.module.RevokeCommands;
import net.serlith.sakurabans.core.command.module.TemplateCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.player.UuidResolver;
import org.junit.jupiter.api.BeforeEach;

/** The command layer of {@code CoreComponents}, installed on a {@link TestCommandManager}. */
public abstract class CommandTest extends ServiceTest {

    protected final TestCommandManager manager = new TestCommandManager();
    protected final FakeCommandActor console = FakeCommandActor.theConsole();

    @BeforeEach
    final void installCommands() {
        final CommandRegistrar registrar = new CommandRegistrar(this.config, this.messages);
        final CommandRunner runner =
                new CommandRunner(this.lifecycle, this.messages, this.log.logger());
        final Replies replies =
                new Replies(runner, this.placeholders, this.names, this.config, this.clock);
        final TargetParser targetParser =
                new TargetParser(this.platform, this.names, this.lifecycle);
        final PunishFlags flags = new PunishFlags(new ScopeParser(this.scopes));
        final Pager pager = new Pager(runner, replies, this.clock);
        final TargetResolver targets = new TargetResolver(this.platform, this.names,
                this.players, new UuidResolver(new SakuraIntegrationRegistry(), this.platform,
                        this.config, this.log.logger()));
        new SakuraCommands(List.of(
                new PunishCommands(registrar, runner, replies, flags, targetParser, targets,
                        this.service, this.names, this.config, this.scopes,
                        new ScopeAccess(this.config), this.templates, this.kinds),
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
                        this.executors.worker(), this.clock),
                new KindCommands(registrar, runner, this.kinds, this.scopes)),
                registrar, runner, this.messages).install(this.manager);
    }

    /** Registers a known player, so names resolve from the database. */
    protected final void known(final UUID uuid, final String name) {
        this.players.recordConnection(uuid, name, this.now.get(), this.home, null).join();
    }

    protected final void run(final CommandActor actor, final String line) {
        this.manager.run(actor, line);
    }
}
