package net.serlith.sakurabans.core.bootstrap;

import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Pager;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.module.AltCommands;
import net.serlith.sakurabans.core.command.module.CheckCommands;
import net.serlith.sakurabans.core.command.module.HistoryCommands;
import net.serlith.sakurabans.core.command.module.PunishCommands;
import net.serlith.sakurabans.core.command.module.RevokeCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.CommandBlacklist;
import net.serlith.sakurabans.core.enforce.EnforcementMessages;
import net.serlith.sakurabans.core.enforce.LocalEnforcer;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.notify.Throttle;
import net.serlith.sakurabans.core.platform.LatePlatform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.player.UuidResolver;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.SchemaManager;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.ScopeRepository;
import net.serlith.sakurabans.core.storage.repository.VictimRepository;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.sync.SyncTransport;

final class CoreComponents {

    // Layer 0
    private final CoreEnvironment environment;
    private final InstantSource clock;
    private final UUID instanceId;
    private final Lifecycle lifecycle;
    private final LatePlatform platform;
    private final SakuraExecutors executors;

    // Layer 1
    private final ConfigService config;
    private final MessageService messages;
    private final StorageService storage;
    private final Lease lease;
    private final MetaRepository meta;
    private final PlayerRepository players;
    private final ScopeRepository scopes;
    private final VictimRepository victims;
    private final PunishmentRepository punishments;
    private final AuditRepository audit;
    private final Formats formats;
    private final Placeholders placeholders;

    // Layer 2
    private final SakuraIntegrationRegistry integrations;
    private final SakuraEventBus events;
    private final SyncService sync;
    private final NameCache names;
    private final UuidResolver uuids;
    private final TargetResolver targets;
    private final SakuraScopeService scopeService;

    // Layer 3
    private final MuteCache mutes;
    private final NotificationService notifications;

    // Layer 4, and the layer 3 services that take a layer 4 implementation of their interface
    private final EnforcementMessages enforcementMessages;
    private final LocalEnforcer enforcer;
    private final SakuraPunishmentService punishmentService;
    private final ExpirySweeper sweeper;
    private final LoginGate loginGate;
    private final ChatGate chatGate;

    // Layer 5
    private final SakuraCommands commands;

    CoreComponents(final CoreEnvironment environment, final InstantSource clock) {
        this.environment = environment;
        this.clock = clock;
        this.instanceId = UUID.randomUUID();
        this.lifecycle = new Lifecycle();
        this.platform = new LatePlatform();
        this.executors = new SakuraExecutors(
                failure -> environment.logger().error("A background task failed", failure));

        this.config = new ConfigService(environment.dataDirectory(), environment.logger());
        this.messages =
                new MessageService(environment.dataDirectory(), this.config, environment.logger());
        this.storage = new StorageService(environment.dataDirectory(), this.config,
                new SchemaManager(environment.logger()), this.executors, environment.logger());
        this.lease = new Lease(this.storage, this.instanceId, clock);
        this.meta = new MetaRepository(this.storage);
        this.players = new PlayerRepository(this.storage);
        this.scopes = new ScopeRepository(this.storage);
        this.victims = new VictimRepository(this.storage);
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(Long::toString));
        this.audit = new AuditRepository(this.storage);
        this.formats = new Formats(this.messages, this.config);
        this.placeholders = new Placeholders(this.messages, this.formats);

        this.integrations = new SakuraIntegrationRegistry();
        this.events = new SakuraEventBus(environment.logger());
        this.sync = new SyncService(SyncTransport.NONE, environment.logger());
        this.names = new NameCache();
        this.uuids = new UuidResolver(this.integrations, this.platform, this.config,
                environment.logger());
        this.targets = new TargetResolver(this.platform, this.names, this.players, this.uuids);
        this.scopeService =
                new SakuraScopeService(this.config, this.platform, environment.platformType());

        this.mutes = new MuteCache(this.punishments, this.scopeService, this.config, clock);
        this.notifications = new NotificationService(this.platform, this.messages,
                this.placeholders, this.names, this.config, this.events, new Throttle(clock),
                this.executors.worker(), clock);

        this.enforcementMessages =
                new EnforcementMessages(this.messages, this.placeholders, this.names, clock);
        this.enforcer = new LocalEnforcer(this.platform, this.scopeService, this.players,
                this.mutes, this.enforcementMessages, this.config, environment.logger());
        this.punishmentService = new SakuraPunishmentService(this.lifecycle,
                this.executors.worker(), clock, this.config, this.scopeService, this.punishments,
                new PunishmentFactory(this.scopeService, this.config, this.players, this.platform,
                        clock),
                this.mutes, this.enforcer, ActionCheck.ALLOW_ALL, this.events, this.sync);
        this.sweeper = new ExpirySweeper(this.lease, this.punishments, this.mutes, this.enforcer,
                this.events, this.executors, this.platform, clock, environment.logger());
        this.loginGate = new LoginGate(this.lifecycle, this.config, this.scopeService,
                this.punishments, this.players, this.names, this.mutes, this.enforcementMessages,
                this.notifications, this.events, clock, environment.logger());
        this.chatGate = new ChatGate(this.mutes, new CommandBlacklist(this.config),
                this.enforcementMessages, this.notifications, this.events, environment.logger());

        final CommandRegistrar registrar = new CommandRegistrar(this.config, this.messages);
        final CommandRunner runner =
                new CommandRunner(this.lifecycle, this.messages, environment.logger());
        final Replies replies =
                new Replies(runner, this.placeholders, this.names, this.config, clock);
        final TargetParser targetParser =
                new TargetParser(this.platform, this.names, this.lifecycle);
        final PunishFlags flags = new PunishFlags(new ScopeParser(this.scopeService));
        final Pager pager = new Pager(runner, replies, clock);
        this.commands = new SakuraCommands(List.of(
                new PunishCommands(registrar, runner, replies, flags, targetParser,
                        this.targets, this.punishmentService, this.names, this.config,
                        this.scopeService),
                new RevokeCommands(registrar, runner, replies, flags, targetParser,
                        this.targets, this.punishmentService),
                new CheckCommands(registrar, runner, replies, pager, targetParser,
                        this.targets, this.punishmentService, this.config),
                new HistoryCommands(registrar, runner, pager, targetParser, this.targets,
                        this.punishmentService),
                new AltCommands(registrar, runner, replies, this.formats, targetParser,
                        this.targets, this.players)),
                registrar, runner, this.messages);
    }

    CoreEnvironment environment() {
        return this.environment;
    }

    InstantSource clock() {
        return this.clock;
    }

    Lifecycle lifecycle() {
        return this.lifecycle;
    }

    LatePlatform platform() {
        return this.platform;
    }

    SakuraExecutors executors() {
        return this.executors;
    }

    ConfigService config() {
        return this.config;
    }

    MessageService messages() {
        return this.messages;
    }

    UUID instanceId() {
        return this.instanceId;
    }

    StorageService storage() {
        return this.storage;
    }

    Lease lease() {
        return this.lease;
    }

    MetaRepository meta() {
        return this.meta;
    }

    PlayerRepository players() {
        return this.players;
    }

    ScopeRepository scopes() {
        return this.scopes;
    }

    VictimRepository victims() {
        return this.victims;
    }

    PunishmentRepository punishments() {
        return this.punishments;
    }

    AuditRepository audit() {
        return this.audit;
    }

    SakuraIntegrationRegistry integrations() {
        return this.integrations;
    }

    SakuraEventBus events() {
        return this.events;
    }

    NameCache names() {
        return this.names;
    }

    TargetResolver targets() {
        return this.targets;
    }

    SakuraScopeService scopeService() {
        return this.scopeService;
    }

    MuteCache mutes() {
        return this.mutes;
    }

    NotificationService notifications() {
        return this.notifications;
    }

    SakuraPunishmentService punishmentService() {
        return this.punishmentService;
    }

    ExpirySweeper sweeper() {
        return this.sweeper;
    }

    LoginGate loginGate() {
        return this.loginGate;
    }

    ChatGate chatGate() {
        return this.chatGate;
    }

    SakuraCommands commands() {
        return this.commands;
    }
}
