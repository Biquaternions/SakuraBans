package net.serlith.sakurabans.core.bootstrap;

import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.admin.SakuraStatsService;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.appeal.SakuraAppealService;
import net.serlith.sakurabans.core.audit.SakuraAuditService;
import net.serlith.sakurabans.core.auth.SakuraAuthorizationService;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.ConfirmationTracker;
import net.serlith.sakurabans.core.command.Pager;
import net.serlith.sakurabans.core.command.PunishFlags;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.SakuraCommands;
import net.serlith.sakurabans.core.command.ScopeAccess;
import net.serlith.sakurabans.core.command.module.AdminCommands;
import net.serlith.sakurabans.core.command.module.AllowCommands;
import net.serlith.sakurabans.core.command.module.AltCommands;
import net.serlith.sakurabans.core.command.module.AppealCommands;
import net.serlith.sakurabans.core.command.module.BanWaveCommands;
import net.serlith.sakurabans.core.command.module.CheckCommands;
import net.serlith.sakurabans.core.command.module.EditCommands;
import net.serlith.sakurabans.core.command.module.GdprCommands;
import net.serlith.sakurabans.core.command.module.HistoryCommands;
import net.serlith.sakurabans.core.command.module.KindCommands;
import net.serlith.sakurabans.core.command.module.PunishCommands;
import net.serlith.sakurabans.core.command.module.RevokeCommands;
import net.serlith.sakurabans.core.command.module.TemplateCommands;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.configuration.settings.SyncMode;
import net.serlith.sakurabans.core.data.SakuraPlayerDataService;
import net.serlith.sakurabans.core.enforce.ChannelReceiver;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.CommandBlacklist;
import net.serlith.sakurabans.core.enforce.EnforcementMessages;
import net.serlith.sakurabans.core.enforce.LocalEnforcer;
import net.serlith.sakurabans.core.enforce.LockdownService;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.enforce.SyncReceiver;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.kind.SakuraKindService;
import net.serlith.sakurabans.core.limit.CooldownTracker;
import net.serlith.sakurabans.core.limit.LimitService;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.notify.Throttle;
import net.serlith.sakurabans.core.notify.WebhookService;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.permission.LuckPermsBridge;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.platform.LatePlatform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.player.UuidResolver;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.punish.WarningActions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.scope.ServerNameDetector;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.SchemaManager;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.AppealRepository;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.BanWaveRepository;
import net.serlith.sakurabans.core.storage.repository.KindRepository;
import net.serlith.sakurabans.core.storage.repository.LockdownRepository;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerDataRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.ScopeRepository;
import net.serlith.sakurabans.core.storage.repository.StatsRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.storage.repository.VictimRepository;
import net.serlith.sakurabans.core.sync.PacketCodec;
import net.serlith.sakurabans.core.sync.SqlPollingTransport;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.sync.SyncTransport;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.template.TemplateActions;
import net.serlith.sakurabans.core.wave.BanWaveScheduler;
import net.serlith.sakurabans.core.wave.SakuraBanWaveService;

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
    private final DisplayIdCodec displayIds;
    private final PlayerRepository players;
    private final ScopeRepository scopes;
    private final VictimRepository victims;
    private final PunishmentRepository punishments;
    private final AuditRepository audit;
    private final SakuraAuditService auditService;
    private final TemplateRepository templateRepository;
    private final TemplateFiles templateFiles;
    private final Formats formats;
    private final Placeholders placeholders;

    // Layer 2
    private final SakuraIntegrationRegistry integrations;
    private final SakuraEventBus events;
    private final SyncService sync;
    private final SakuraAllowListService allowList;
    private final AltService altService;
    private final ActiveRangeCache ranges;
    private final SyncReceiver syncReceiver;
    private final NameCache names;
    private final UuidResolver uuids;
    private final TargetResolver targets;
    private final ServerNameDetector serverNames;
    private final SakuraScopeService scopeService;

    // Layer 3
    private final MuteCache mutes;
    private final SakuraKindService kindService;
    private final PermissionService permissions;
    private final ExemptionService exemption;
    private final SakuraAuthorizationService authorization;
    private final LimitService limits;
    private final SakuraTemplateService templates;
    private final TemplateActions templateActions;
    private final WarningActions warningActions;
    private final NotificationService notifications;
    private final LockdownService lockdowns;
    private final WebhookService webhooks;
    private final SakuraSettingsService settingsService;
    private final SakuraMessageTemplateService messageTemplates;
    private final SakuraBanWaveService banWaves;
    private final BanWaveScheduler banWaveScheduler;
    private final SakuraAppealService appeals;
    private final SakuraPlayerDataService playerData;

    // Layer 4, and the layer 3 services that take a layer 4 implementation of their interface
    private final EnforcementMessages enforcementMessages;
    private final ProxyChannel channel;
    private final LocalEnforcer enforcer;
    private final ChannelReceiver channelReceiver;
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
        this.displayIds = new DisplayIdCodec(this.meta, this.config);
        this.scopes = new ScopeRepository(this.storage);
        this.victims = new VictimRepository(this.storage);
        this.sync = new SyncService(SyncTransport.enabledWhen(
                () -> this.config.storage().sync().mode() == SyncMode.SQL,
                new SqlPollingTransport(this.storage,
                        new PacketCodec(this.instanceId, environment.logger()), this.executors,
                        this.config, clock, environment.logger())),
                environment.logger());
        this.events = new SakuraEventBus(environment.logger());
        this.kindService = new SakuraKindService(new KindRepository(this.storage),
                this::authorize, this.sync, this.events, clock);
        this.allowList = new SakuraAllowListService(new AllowListRepository(this.storage),
                this.sync, this.config, clock);
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(this.displayIds::display), this.allowList,
                this.kindService);
        this.audit = new AuditRepository(this.storage);
        this.auditService = new SakuraAuditService(this.audit);
        this.templateRepository = new TemplateRepository(this.storage, this.allowList);
        this.templateFiles = new TemplateFiles(environment.dataDirectory());
        this.formats = new Formats(this.messages, this.config);
        this.placeholders = new Placeholders(this.messages, this.formats);

        this.integrations = new SakuraIntegrationRegistry();
        this.names = new NameCache();
        this.uuids = new UuidResolver(this.integrations, this.platform, this.config,
                environment.logger());
        this.targets = new TargetResolver(this.platform, this.names, this.players, this.uuids);
        this.serverNames = new ServerNameDetector(environment.dataDirectory(), this.config,
                this.executors.worker(), environment.logger());
        this.scopeService = new SakuraScopeService(this.config, this.platform,
                environment.proxy(), this.serverNames, environment.logger());

        this.altService = new AltService(this.players, this.punishments, this.allowList,
                this.integrations, clock, environment.logger());
        this.ranges = new ActiveRangeCache(this.punishments, this.players, this.allowList,
                clock);
        this.mutes = new MuteCache(this.punishments, this.scopeService, this.config, clock,
                this.ranges);
        this.permissions = new PermissionService(this.platform,
                LuckPermsBridge.detect(environment.logger()), environment.logger());
        this.exemption = new ExemptionService(this.config, this.permissions,
                environment.logger());
        this.authorization = new SakuraAuthorizationService(this.permissions, this.exemption,
                this.kindService, this.punishments, this.integrations, this.config,
                environment.logger());
        this.limits = new LimitService(this.config, this.permissions, new CooldownTracker(),
                clock);
        this.templates = new SakuraTemplateService(this.templateRepository, this.meta,
                this.templateFiles, this.kindService, this.integrations, this::authorize,
                this.sync, this.events, clock, environment.logger());
        this.templateActions = new TemplateActions(this.events, this.templates, this.platform,
                this.names, environment.logger());
        this.warningActions = new WarningActions(this.events, this.punishments, this.config,
                this.platform, this.names, clock, environment.logger());
        this.webhooks = new WebhookService(this.events, this.config, this.messages,
                this.placeholders, this.formats, this.scopeService, this.names, this.executors,
                clock, environment.logger());
        final OverrideRepository overrides = new OverrideRepository(this.storage);
        this.settingsService = new SakuraSettingsService(this.config, overrides,
                this::authorize, this.sync, this.events, clock, environment.logger());
        this.messageTemplates = new SakuraMessageTemplateService(this.messages, overrides,
                this::authorize, this.sync, this.events, clock);
        this.lockdowns = new LockdownService(new LockdownRepository(this.storage),
                this.permissions, this.allowList, this.sync, this.config, clock,
                environment.logger());
        this.notifications = new NotificationService(this.platform, this.messages,
                this.placeholders, this.names, this.config, this.events, new Throttle(clock),
                this.executors.worker(), clock);

        this.enforcementMessages =
                new EnforcementMessages(this.messages, this.placeholders, this.names, clock);
        this.channel = new ProxyChannel(this.meta, environment.logger());
        this.enforcer = new LocalEnforcer(this.platform, this.scopeService, this.punishments,
                this.mutes, this.enforcementMessages, this.config, this.channel, clock,
                this.ranges, environment.logger());
        this.channelReceiver = new ChannelReceiver(this.channel, this.scopeService,
                this.serverNames, this.punishments, this.enforcer, clock, environment.logger());
        this.punishmentService = new SakuraPunishmentService(this.lifecycle,
                this.executors.worker(), clock, this.config, this.scopeService, this.punishments,
                new PunishmentFactory(this.scopeService, this.config, this.players, this.platform,
                        clock, this.templates, this.kindService),
                this.mutes, this.enforcer, this::authorize, this.limits, this.exemption,
                this.events, this.sync, this.displayIds);
        final BanWaveRepository waveRepository = new BanWaveRepository(this.storage);
        this.banWaves = new SakuraBanWaveService(waveRepository, this.punishmentService,
                this::authorize, this.sync, this.events, clock, environment.logger());
        this.banWaveScheduler = new BanWaveScheduler(this.lease, waveRepository, this.banWaves,
                this.executors, clock, environment.logger());
        this.appeals = new SakuraAppealService(new AppealRepository(this.storage),
                this.punishmentService, this::authorize, this.notifications, this.names,
                this.sync, this.events, clock);
        this.playerData = new SakuraPlayerDataService(new PlayerDataRepository(this.storage,
                new PunishmentMapper(this.displayIds::display)), this.players, this.audit,
                this.allowList, this.mutes, this.names, this::authorize, this.sync, this.events,
                clock);
        this.syncReceiver = new SyncReceiver(this.punishments, this.enforcer, this.mutes,
                this.scopeService, this.platform, this.events, this.config, clock,
                this.allowList, this.templates, this.kindService, this.lockdowns,
                this.notifications, this.settingsService, this.messageTemplates,
                this.banWaves, this.appeals, this.playerData, environment.logger());
        this.sweeper = new ExpirySweeper(this.lease, this.punishments, this.mutes, this.enforcer,
                this.sync, this.events, this.executors, this.platform, clock,
                environment.logger());
        this.loginGate = new LoginGate(this.lifecycle, this.config, this.scopeService,
                this.punishments, this.players, this.names, this.mutes, this.enforcementMessages,
                this.notifications, this.events, clock, this.allowList, this.altService,
                this.ranges, this.enforcer, this.lockdowns, environment.logger());
        this.chatGate = new ChatGate(this.mutes, new CommandBlacklist(this.config),
                this.enforcementMessages, this.notifications, this.events, environment.logger());

        final CommandRegistrar registrar = new CommandRegistrar(this.config, this.messages);
        final CommandRunner runner =
                new CommandRunner(this.lifecycle, this.messages, environment.logger());
        final Replies replies =
                new Replies(runner, this.placeholders, this.names, this.config, clock);
        final TargetParser targetParser =
                new TargetParser(this.platform, this.names, this.lifecycle, this.displayIds);
        final ScopeParser scopeParser = new ScopeParser(this.scopeService);
        final PunishFlags flags = new PunishFlags(scopeParser);
        final ConfirmationTracker confirmations = new ConfirmationTracker(clock);
        final ScopeAccess scopeAccess = new ScopeAccess(this.config);
        final Pager pager = new Pager(runner, replies, clock);
        this.commands = new SakuraCommands(List.of(
                new PunishCommands(registrar, runner, replies, flags, targetParser,
                        this.targets, this.punishmentService, this.names, this.config,
                        this.scopeService, scopeAccess, this.templates, this.kindService),
                new RevokeCommands(registrar, runner, replies, flags, targetParser,
                        this.targets, this.punishmentService, this.scopeService, scopeAccess),
                new CheckCommands(registrar, runner, replies, pager, targetParser,
                        this.targets, this.punishmentService, this.config),
                new HistoryCommands(registrar, runner, pager, targetParser, this.targets,
                        this.punishmentService),
                new AltCommands(registrar, runner, replies, this.formats, targetParser,
                        this.targets, this.players, clock, this.altService, this.platform,
                        this.config),
                new AllowCommands(registrar, runner, replies, this.formats, targetParser,
                        this.targets, this.allowList, this.names, this.placeholders),
                new TemplateCommands(registrar, runner, this.templates, this.templateFiles,
                        confirmations, this.executors.worker(), clock),
                new EditCommands(registrar, runner, replies, this.placeholders, this.formats,
                        this.punishmentService, this.auditService, this.displayIds, scopeParser,
                        confirmations, clock),
                new GdprCommands(registrar, runner, targetParser, this.targets, this.playerData,
                        confirmations, environment.dataDirectory(), this.executors.worker(),
                        clock),
                new AppealCommands(registrar, runner, this.formats, this.appeals,
                        this.punishmentService, this.displayIds, this.names),
                new BanWaveCommands(registrar, runner, replies, this.formats, flags,
                        targetParser, this.targets, this.banWaves, confirmations, clock),
                new AdminCommands(registrar, runner, replies, this.placeholders, this.formats,
                        flags, targetParser, this.targets, this.punishmentService,
                        this.punishments, this.lockdowns, this.scopeService, this.notifications,
                        this.sync, this.config, this.messages, this.platform, confirmations,
                        this.executors.worker(), clock),
                new KindCommands(registrar, runner, this.kindService, this.scopeService)),
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

    DisplayIdCodec displayIds() {
        return this.displayIds;
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

    SakuraAuditService auditService() {
        return this.auditService;
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

    ServerNameDetector serverNames() {
        return this.serverNames;
    }

    ActiveRangeCache ranges() {
        return this.ranges;
    }

    AltService altService() {
        return this.altService;
    }

    SakuraAllowListService allowList() {
        return this.allowList;
    }

    SyncService sync() {
        return this.sync;
    }

    SyncReceiver syncReceiver() {
        return this.syncReceiver;
    }

    ProxyChannel channel() {
        return this.channel;
    }

    ChannelReceiver channelReceiver() {
        return this.channelReceiver;
    }

    SakuraScopeService scopeService() {
        return this.scopeService;
    }

    MuteCache mutes() {
        return this.mutes;
    }

    private CompletableFuture<Decision> authorize(final Actor actor, final Action action) {
        return this.authorization.check(actor, action);
    }

    SakuraAuthorizationService authorization() {
        return this.authorization;
    }

    SakuraKindService kindService() {
        return this.kindService;
    }

    SakuraTemplateService templates() {
        return this.templates;
    }

    TemplateActions templateActions() {
        return this.templateActions;
    }

    TemplateFiles templateFiles() {
        return this.templateFiles;
    }

    WarningActions warningActions() {
        return this.warningActions;
    }

    SakuraSettingsService settingsService() {
        return this.settingsService;
    }

    SakuraMessageTemplateService messageTemplates() {
        return this.messageTemplates;
    }

    SakuraBanWaveService banWaves() {
        return this.banWaves;
    }

    SakuraPlayerDataService playerData() {
        return this.playerData;
    }

    SakuraAppealService appeals() {
        return this.appeals;
    }

    BanWaveScheduler banWaveScheduler() {
        return this.banWaveScheduler;
    }

    SakuraStatsService stats() {
        return new SakuraStatsService(new StatsRepository(this.storage, this.allowList),
                this.punishments, this.clock);
    }

    SakuraMessageFormatter formatter() {
        return new SakuraMessageFormatter(this.enforcementMessages, this.formats);
    }

    WebhookService webhooks() {
        return this.webhooks;
    }

    LockdownService lockdowns() {
        return this.lockdowns;
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
