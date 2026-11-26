package net.serlith.sakurabans.core.bootstrap;

import java.security.SecureRandom;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.admin.EditorClient;
import net.serlith.sakurabans.core.admin.EditorLines;
import net.serlith.sakurabans.core.admin.EditorSeal;
import net.serlith.sakurabans.core.admin.EditorService;
import net.serlith.sakurabans.core.admin.EditorWatcher;
import net.serlith.sakurabans.core.admin.InstanceRegistry;
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
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
import net.serlith.sakurabans.core.command.module.CheckCommands;
import net.serlith.sakurabans.core.command.module.EditCommands;
import net.serlith.sakurabans.core.command.module.EditorCommands;
import net.serlith.sakurabans.core.command.module.GdprCommands;
import net.serlith.sakurabans.core.command.module.HistoryCommands;
import net.serlith.sakurabans.core.command.module.ImportCommands;
import net.serlith.sakurabans.core.command.module.OverrideCommands;
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
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.CommandBlacklist;
import net.serlith.sakurabans.core.enforce.EnforcementMessages;
import net.serlith.sakurabans.core.enforce.LocalEnforcer;
import net.serlith.sakurabans.core.enforce.LockdownService;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.enforce.SyncReceiver;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.importer.ImportJobs;
import net.serlith.sakurabans.core.importer.ImportRunner;
import net.serlith.sakurabans.core.importer.ImportSources;
import net.serlith.sakurabans.core.importer.SelfImport;
import net.serlith.sakurabans.core.limit.CooldownTracker;
import net.serlith.sakurabans.core.limit.LimitService;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.notify.Throttle;
import net.serlith.sakurabans.core.notify.WebhookService;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.permission.LuckPermsBridge;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.platform.LatePlatform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.player.UuidResolver;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.punish.TemplateActions;
import net.serlith.sakurabans.core.punish.WarningActions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.SchemaManager;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.EditorSessionRepository;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.storage.repository.LockdownRepository;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerDataRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.ScopeRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.sync.PacketCodec;
import net.serlith.sakurabans.core.sync.SqlPollingTransport;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.sync.SyncTransport;
import net.serlith.sakurabans.core.template.SakuraTemplateService;

final class CoreComponents {

    // Layer 0
    private final CoreEnvironment environment;
    private final InstantSource clock;
    private final Lifecycle lifecycle;
    private final LatePlatform platform;
    private final SakuraExecutors executors;

    // Layer 1
    private final ConfigService config;
    private final MessageService messages;
    private final StorageService storage;
    private final MetaRepository meta;
    private final DisplayIdCodec displayIds;
    private final PlayerRepository players;
    private final ScopeRepository scopes;
    private final PunishmentRepository punishments;
    private final TemplateRepository templateRepository;
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
    private final SakuraScopeService scopeService;

    // Layer 3
    private final PermissionService permissions;
    private final ExemptionService exemption;
    private final LimitService limits;
    private final SakuraTemplateService templates;
    private final TemplateActions templateActions;
    private final WarningActions warningActions;
    private final NotificationService notifications;
    private final LockdownService lockdowns;
    private final WebhookService webhooks;
    private final SakuraSettingsService settingsService;
    private final SakuraMessageTemplateService messageTemplates;
    private final InstanceRegistry registry;
    private final EditorClient editorClient;
    private final EditorService editor;
    private final EditorWatcher editorWatcher;
    private final EditorLines editorLines;
    private final SakuraPlayerDataService playerData;

    // Layer 4, and the layer 3 services that take a layer 4 implementation of their interface
    private final EnforcementMessages enforcementMessages;
    private final LocalEnforcer enforcer;
    private final SakuraPunishmentService punishmentService;
    private final ExpirySweeper sweeper;
    private final LoginGate loginGate;
    private final ChatGate chatGate;
    private final ImportJobs imports;

    // Layer 5
    private final SakuraCommands commands;

    CoreComponents(final CoreEnvironment environment, final InstantSource clock) {
        this.environment = environment;
        this.clock = clock;
        final UUID instanceId = UUID.randomUUID();
        this.lifecycle = new Lifecycle();
        this.platform = new LatePlatform();
        this.executors = new SakuraExecutors(
                failure -> environment.logger().error("A background task failed", failure));

        this.config = new ConfigService(environment.dataDirectory(), environment.logger());
        this.messages =
                new MessageService(environment.dataDirectory(), this.config, environment.logger());
        this.storage = new StorageService(environment.dataDirectory(), this.config,
                new SchemaManager(environment.logger()), this.executors, environment.logger());
        final Lease lease = new Lease(this.storage, instanceId, clock);
        this.meta = new MetaRepository(this.storage);
        this.players = new PlayerRepository(this.storage);
        this.displayIds = new DisplayIdCodec(this.meta, this.config);
        this.scopes = new ScopeRepository(this.storage);
        this.sync = new SyncService(SyncTransport.enabledWhen(
                () -> this.config.storage().sync().mode() == SyncMode.SQL,
                new SqlPollingTransport(this.storage,
                        new PacketCodec(instanceId, environment.logger()), this.executors,
                        this.config, clock, environment.logger())),
                environment.logger());
        this.events = new SakuraEventBus(environment.logger());
        this.allowList = new SakuraAllowListService(new AllowListRepository(this.storage),
                this.sync, this.config, clock);
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(this.displayIds::display), this.allowList);
        final AuditRepository audit = new AuditRepository(this.storage);
        this.templateRepository = new TemplateRepository(this.storage, this.allowList);
        final TemplateFiles templateFiles = new TemplateFiles(environment.dataDirectory());
        this.formats = new Formats(this.messages, this.config);
        this.placeholders = new Placeholders(this.messages, this.formats);

        this.integrations = new SakuraIntegrationRegistry();
        this.names = new NameCache();
        this.uuids = new UuidResolver(this.integrations, this.platform, this.config,
                environment.logger());
        final TargetResolver targets =
                new TargetResolver(this.platform, this.names, this.players, this.uuids);
        this.scopeService = new SakuraScopeService(this.config, this.platform,
                environment.proxy(), environment.logger());

        this.altService = new AltService(this.players, this.punishments, this.allowList,
                clock);
        this.ranges = new ActiveRangeCache(this.punishments, this.players, this.allowList,
                clock);
        final MuteCache mutes = new MuteCache(this.punishments, this.scopeService, this.config,
                clock, this.ranges);
        this.permissions = new PermissionService(this.platform,
                LuckPermsBridge.detect(environment.logger()), environment.logger());
        this.exemption = new ExemptionService(this.config, this.permissions,
                environment.logger());
        final AuthorizationService authorization = new AuthorizationService(
                this.permissions, this.exemption, this.punishments, this.config);
        this.limits = new LimitService(this.config, this.permissions, new CooldownTracker(),
                clock);
        this.templates = new SakuraTemplateService(this.templateRepository, this.meta,
                templateFiles, authorization, this.sync, clock, environment.logger());
        this.templateActions = new TemplateActions(this.events, this.templates, this.platform,
                this.names, environment.logger());
        this.warningActions = new WarningActions(this.events, this.punishments, this.config,
                this.platform, this.names, clock, environment.logger());
        this.webhooks = new WebhookService(this.events, this.config, this.messages,
                this.placeholders, this.formats, this.scopeService, this.names, this.executors,
                clock, environment.logger());
        final OverrideRepository overrides = new OverrideRepository(this.storage);
        this.settingsService = new SakuraSettingsService(this.config, overrides,
                authorization, this.sync, clock, environment.logger());
        this.messageTemplates = new SakuraMessageTemplateService(this.messages, overrides,
                authorization, this.sync, clock);
        this.registry = new InstanceRegistry(environment.dataDirectory(), this.config,
                this.platform, new InstanceRepository(this.storage), this.executors, clock,
                environment.logger());
        this.editorClient = new EditorClient(this.config, this.executors.worker(),
                environment.logger());
        this.editor = new EditorService(this.config, this.settingsService, this.messageTemplates,
                this.templates, new EditorSessionRepository(this.storage), this.editorClient,
                new EditorSeal(new SecureRandom()), this.platform, this.registry, clock,
                environment.logger());
        this.editorLines = new EditorLines(this.messages, this.platform);
        this.editorWatcher = new EditorWatcher(this.config, this.editor,
                this.editorClient::connect, this.editorLines, this.platform, this.executors,
                EditorWatcher.Timing.DEFAULT, clock, environment.logger());
        this.lockdowns = new LockdownService(new LockdownRepository(this.storage),
                this.permissions, this.allowList, this.sync, this.config, clock,
                environment.logger());
        this.notifications = new NotificationService(this.platform, this.messages,
                this.placeholders, this.names, this.config, this.events, new Throttle(clock),
                this.executors.worker(), clock);

        this.enforcementMessages =
                new EnforcementMessages(this.messages, this.placeholders, this.names,
                        this.templates::template, clock);
        this.enforcer = new LocalEnforcer(this.platform, this.scopeService, this.punishments,
                mutes, this.enforcementMessages, this.config, clock, this.ranges,
                environment.logger());
        this.punishmentService = new SakuraPunishmentService(this.lifecycle,
                this.executors.worker(), clock, this.config, this.scopeService, this.punishments,
                new PunishmentFactory(this.scopeService, this.config, this.players, this.platform,
                        clock, this.templates),
                mutes, this.enforcer, authorization, this.limits, this.exemption,
                this.events, this.sync, this.displayIds);
        this.playerData = new SakuraPlayerDataService(new PlayerDataRepository(this.storage,
                new PunishmentMapper(this.displayIds::display)), this.players, audit,
                this.allowList, mutes, this.names, authorization, this.sync,
                this.events, clock);
        this.syncReceiver = new SyncReceiver(this.punishments, this.enforcer, mutes,
                this.scopeService, this.platform, this.notifications, this.config, clock,
                this.allowList, this.templates, this.lockdowns,
                this.settingsService, this.messageTemplates,
                this.playerData, this.ranges, this.displayIds, environment.logger());
        this.sweeper = new ExpirySweeper(lease, this.punishments, mutes, this.enforcer,
                this.sync, this.events, this.executors, this.platform, clock,
                environment.logger());
        this.loginGate = new LoginGate(this.lifecycle, this.config, this.scopeService,
                this.punishments, this.players, this.names, mutes, this.enforcementMessages,
                this.notifications, this.events, clock, this.altService, this.ranges,
                this.enforcer, this.lockdowns, environment.logger());
        this.chatGate = new ChatGate(mutes, new CommandBlacklist(this.config),
                this.enforcementMessages, this.notifications, this.events, clock,
                environment.logger());

        final ImportRepository importRepository = new ImportRepository(this.storage);
        this.imports = new ImportJobs(ImportSources.standard(),
                new ImportRunner(importRepository, clock, environment.logger()),
                new SelfImport(importRepository, environment.logger()), importRepository,
                lease, this.config, this.sync, this.syncReceiver, this.executors.worker(),
                clock, environment.logger());

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
                        targets, this.punishmentService, this.names, this.config,
                        this.scopeService, scopeAccess, this.templates),
                new RevokeCommands(registrar, runner, replies, flags, targetParser,
                        targets, this.punishmentService, this.scopeService, scopeAccess),
                new CheckCommands(registrar, runner, replies, pager, targetParser,
                        targets, this.punishmentService, this.config),
                new HistoryCommands(registrar, runner, pager, targetParser, targets,
                        this.punishmentService),
                new AltCommands(registrar, runner, replies, this.formats, targetParser,
                        targets, this.players, clock, this.altService, this.platform,
                        this.config),
                new AllowCommands(registrar, runner, replies, this.formats, targetParser,
                        targets, this.allowList, this.names, this.placeholders),
                new TemplateCommands(registrar, runner, this.templates, templateFiles,
                        confirmations, this.executors.worker(), clock),
                new EditCommands(registrar, runner, replies, this.placeholders, this.formats,
                        this.punishmentService, audit, this.displayIds, scopeParser,
                        confirmations, clock),
                new GdprCommands(registrar, runner, targetParser, targets, this.playerData,
                        confirmations, environment.dataDirectory(), this.executors.worker(),
                        clock),
                new AdminCommands(registrar, runner, replies, this.placeholders, this.formats,
                        flags, targetParser, targets, this.punishmentService,
                        this.punishments, this.lockdowns, this.scopeService, this.config,
                        this.messages, this.platform, confirmations, this.settingsService,
                        this.messageTemplates, this.registry, this.executors.worker(), clock,
                        environment.logger()),
                new OverrideCommands(registrar, runner, this.settingsService,
                        this.messageTemplates, confirmations, scopeParser, this.registry),
                new EditorCommands(registrar, runner, this.editor, this.editorWatcher,
                        this.editorLines, this.formats),
                new ImportCommands(registrar, runner, this.imports, confirmations,
                        this.platform)),
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

    StorageService storage() {
        return this.storage;
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

    PunishmentRepository punishments() {
        return this.punishments;
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

    SakuraScopeService scopeService() {
        return this.scopeService;
    }

    SakuraTemplateService templates() {
        return this.templates;
    }

    TemplateActions templateActions() {
        return this.templateActions;
    }

    WarningActions warningActions() {
        return this.warningActions;
    }

    SakuraSettingsService settingsService() {
        return this.settingsService;
    }

    InstanceRegistry registry() {
        return this.registry;
    }

    EditorClient editorClient() {
        return this.editorClient;
    }

    EditorService editor() {
        return this.editor;
    }

    EditorWatcher editorWatcher() {
        return this.editorWatcher;
    }

    SakuraMessageTemplateService messageTemplates() {
        return this.messageTemplates;
    }

    SakuraPlayerDataService playerData() {
        return this.playerData;
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
