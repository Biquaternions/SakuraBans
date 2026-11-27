package net.serlith.sakurabans.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.SakuraEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.data.SakuraPlayerDataService;
import net.serlith.sakurabans.core.enforce.AltAutoBans;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.CommandBlacklist;
import net.serlith.sakurabans.core.enforce.EnforcementMessages;
import net.serlith.sakurabans.core.enforce.LocalEnforcer;
import net.serlith.sakurabans.core.enforce.LockdownService;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.enforce.SyncReceiver;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.geoip.Country;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.importer.ImportJobs;
import net.serlith.sakurabans.core.importer.ImportRunner;
import net.serlith.sakurabans.core.importer.ImportSources;
import net.serlith.sakurabans.core.importer.SelfImport;
import net.serlith.sakurabans.core.limit.CooldownTracker;
import net.serlith.sakurabans.core.limit.LimitService;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.NetworkTotals;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.notify.Throttle;
import net.serlith.sakurabans.core.permission.AuthorizationService;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.permission.OfflinePermissions;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.permission.TemplateGuard;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.player.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.AuditRetention;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentCounts;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.punish.TemplateActions;
import net.serlith.sakurabans.core.punish.WarningActions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.storage.repository.LockdownRepository;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerDataRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import org.junit.jupiter.api.BeforeEach;

/**
 * The core services wired like {@code CoreComponents} does, on a fresh database, with a
 * {@link FakePlatform} named {@code lobby} and a running lifecycle.
 */
public abstract class ServiceTest extends DatabaseTest {

    protected static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    protected static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    protected static final UUID MOD = UUID.fromString("0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b");
    protected static final Actor MODERATOR = Actor.of(PlayerOperator.of(MOD, "Mod"), "in-game");
    protected static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    protected final FakePlatform platform;
    protected final Lifecycle lifecycle = new Lifecycle();
    protected final List<SakuraEvent> events = new CopyOnWriteArrayList<>();
    protected final List<SyncPacket> published = new CopyOnWriteArrayList<>();

    protected SakuraEventBus bus;
    protected MessageService messages;
    protected Placeholders placeholders;
    protected NameCache names;
    protected SakuraScopeService scopes;
    protected PlayerRepository players;
    protected DisplayIdCodec displayIds;
    protected PunishmentRepository punishments;
    protected Lease lease;
    protected MuteCache mutes;
    protected NetworkTotals totals;
    protected PunishmentCounts counts;
    protected EnforcementMessages enforcementMessages;
    protected LocalEnforcer enforcer;
    protected SakuraPunishmentService service;
    protected NotificationService notifications;
    protected LoginGate loginGate;
    protected AltAutoBans autoBans;
    protected ChatGate chatGate;
    protected ExpirySweeper sweeper;
    protected SyncService syncService;
    protected SakuraAllowListService allowList;
    protected AltService altService;
    protected ActiveRangeCache ranges;
    protected PermissionService permissions;
    protected ExemptionService exemption;
    protected AuthorizationService authorization;
    /** What LuckPerms would say about offline players; none by default. */
    protected OfflinePermissions offline = OfflinePermissions.NONE;
    /** What the services ask for countries; off unless a test sets one. */
    protected CountryLookup countries = CountryLookup.disabled();
    protected LimitService limits;
    protected LockdownService lockdowns;
    protected SakuraSettingsService settingsService;
    protected SakuraMessageTemplateService messageTemplates;
    protected SakuraPlayerDataService playerData;
    protected TemplateRepository templateRepository;
    protected SakuraTemplateService templates;
    protected TemplateActions templateActions;
    protected WarningActions warningActions;
    protected final SakuraIntegrationRegistry integrations = new SakuraIntegrationRegistry();
    protected InetAddress home;

    protected boolean proxy() {
        return false;
    }

    @Override
    protected String baseConfigYaml() {
        return this.proxy() ? "" : "server:\n  name: lobby\n";
    }

    protected ServiceTest() {
        this(new FakePlatform());
    }

    protected ServiceTest(final FakePlatform platform) {
        this.platform = platform;
    }

    /**
     * What the moderator may do: every action authorization checks. {@link #MODERATOR} is online
     * with these, as staff acting in game are.
     */
    protected static String[] staffPermissions() {
        final List<String> nodes = new ArrayList<>(List.of(Permissions.PUNISH,
                Permissions.ADMIN_TEMPLATES,
                Permissions.ADMIN_SETTINGS, Permissions.ADMIN_MESSAGES, Permissions.ADMIN_GDPR,
                Permissions.ADMIN_STAFFROLLBACK));
        for (final PunishmentType type : PunishmentType.values()) {
            nodes.addAll(List.of(Permissions.punish(type), Permissions.revoke(type),
                    Permissions.edit(type), Permissions.voidPunishment(type),
                    Permissions.delete(type)));
        }
        return nodes.toArray(String[]::new);
    }

    @BeforeEach
    final void wireServices() throws UnknownHostException {
        this.home = InetAddress.getByName("203.0.113.7");
        this.bus = new SakuraEventBus(this.log.logger());
        this.bus.subscribe(SakuraEvent.class, EventPriority.MONITOR, this.events::add);
        this.totals = new NetworkTotals();
        this.messages = new MessageService(this.dir, this.config, this.log.logger(),
                this.totals);
        this.messages.load();
        this.placeholders =
                new Placeholders(this.messages, new Formats(this.messages, this.config));
        this.names = new NameCache();
        this.scopes = new SakuraScopeService(this.config, this.platform, this.proxy(),
                this.log.logger());
        this.players = new PlayerRepository(this.storage, this.hasher);
        this.displayIds = new DisplayIdCodec(new MetaRepository(this.storage), this.config);
        this.displayIds.load().join();
        this.syncService = new SyncService(this.published::add, this.log.logger());
        this.allowList = new SakuraAllowListService(new AllowListRepository(this.storage),
                this.syncService, this.config, this.clock);
        this.allowList.reload().join();
        this.punishments = new PunishmentRepository(this.storage, this.hasher,
                new PunishmentMapper(this.displayIds::display), this.allowList);
        this.counts = new PunishmentCounts(this.punishments, this.totals, this.bus, this.config,
                this.executors, this.clock, this.log.logger());
        this.altService = new AltService(this.players, this.punishments, this.allowList,
                this.clock);
        this.lease = new Lease(this.storage, UUID.randomUUID());
        this.ranges = new ActiveRangeCache(this.punishments, this.players, this.allowList,
                this.clock);
        this.mutes = new MuteCache(this.punishments, this.scopes, this.config, this.clock,
                this.ranges);
        this.enforcementMessages =
                new EnforcementMessages(this.messages, this.placeholders, this.names,
                        ref -> this.templates.definition(ref).map(StoredTemplate::template),
                        this.clock, this.counts, new DelegatingCountries());
        this.notifications = new NotificationService(this.platform, this.messages,
                this.placeholders, this.names, this.config, this.bus, new Throttle(this.clock),
                Runnable::run, this.clock, this.counts, this.log.logger());
        this.notifications.start();
        this.permissions = new PermissionService(this.platform, new DelegatingPermissions(),
                this.executors.worker(), this.log.logger());
        this.exemption = new ExemptionService(this.config, this.permissions, this.log.logger(),
                this.names::name);
        this.authorization = new AuthorizationService(this.permissions, this.exemption,
                this.punishments, this.config, TemplateGuard.lazy(() -> this.templates));
        final OverrideRepository overrides = new OverrideRepository(this.storage);
        this.settingsService = new SakuraSettingsService(this.config, overrides,
                this.authorization, this.syncService,
                this.clock, this.log.logger());
        this.messageTemplates = new SakuraMessageTemplateService(this.messages, overrides,
                this.authorization, this.syncService,
                this.clock);
        this.lockdowns = new LockdownService(new LockdownRepository(this.storage),
                this.permissions, this.allowList, this.syncService, this.config, this.clock,
                this.log.logger());
        this.enforcer = new LocalEnforcer(this.platform, this.scopes, this.punishments, this.mutes,
                this.enforcementMessages, this.config, this.clock, this.ranges, this.counts,
                this.log.logger());
        this.limits = new LimitService(this.config, this.permissions, new CooldownTracker(),
                this.clock);
        this.templateRepository = new TemplateRepository(this.storage, this.hasher,
                this.allowList);
        this.templates = new SakuraTemplateService(this.templateRepository,
                new MetaRepository(this.storage), new TemplateFiles(this.dir),
                this.authorization, this.syncService,
                this.clock, this.config, this.scopes, this.platform, this.log.logger());
        this.templates.reload().join();
        this.templateActions = new TemplateActions(this.platform, this.names,
                this.log.logger());
        this.warningActions = new WarningActions(this.bus, this.punishments, this.config,
                this.platform, this.names, this.clock, this.log.logger());
        this.warningActions.start();
        this.service = new SakuraPunishmentService(this.lifecycle, this.executors.worker(),
                this.clock, this.config, this.scopes, this.punishments,
                new PunishmentFactory(this.scopes, this.config, this.players, this.platform,
                        this.clock, this.templates),
                this.mutes, this.enforcer, this.authorization,
                this.limits, this.exemption, this.bus, this.syncService, this.displayIds,
                this.templates, this.templateRepository, this.templateActions,
                this.log.logger());
        this.playerData = new SakuraPlayerDataService(new PlayerDataRepository(this.storage,
                this.hasher,
                new PunishmentMapper(this.displayIds::display)), this.players,
                new AuditRepository(this.storage), this.allowList, this.mutes, this.counts,
                this.names,
                this.authorization, this.syncService,
                this.bus, this.clock);
        this.autoBans = new AltAutoBans(this.config, this.altService, this.allowList,
                this.service, this.exemption, new MetaRepository(this.storage), this.bus,
                this.notifications, this.syncService, this.clock, this.log.logger());
        this.autoBans.start();
        this.loginGate = new LoginGate(this.lifecycle, this.config, this.scopes,
                this.punishments, this.players, this.names, this.mutes,
                this.enforcementMessages, this.notifications, this.bus, this.clock,
                this.altService, this.ranges, this.enforcer, this.lockdowns,
                this.autoBans, this.counts, new DelegatingCountries(), this.allowList,
                this.permissions, this.log.logger());
        this.chatGate = new ChatGate(this.mutes, new CommandBlacklist(this.config),
                this.enforcementMessages, this.notifications, this.bus, this.clock,
                this.log.logger());
        this.sweeper = new ExpirySweeper(this.lease,
                new AuditRetention(new AuditRepository(this.storage), this.config, this.clock,
                        this.log.logger()),
                this.punishments, this.mutes, this.enforcer,
                this.syncService, this.bus, this.executors, this.platform, this.clock,
                this.log.logger());
        this.platform.join(MOD, "Mod", InetAddress.getByName("203.0.113.254"))
                .grant(staffPermissions());
        this.lifecycle.advance(LifecycleState.NEW, LifecycleState.CREATED);
        this.lifecycle.advance(LifecycleState.CREATED, LifecycleState.STARTING);
        this.lifecycle.advance(LifecycleState.STARTING, LifecycleState.RUNNING);
    }

    /** What receives other instances' packets, wired like {@code CoreComponents} does. */
    protected final SyncReceiver syncReceiver() {
        return new SyncReceiver(this.punishments, this.enforcer, this.mutes, this.counts,
                this.scopes,
                this.platform, this.notifications, this.config, this.clock, this.allowList,
                this.templates, this.lockdowns, this.settingsService,
                this.messageTemplates, this.playerData, this.ranges, this.displayIds,
                this.log.logger());
    }

    /** The import framework wired like {@code CoreComponents} does, with these sources. */
    protected final ImportJobs importJobs(final ImportSources sources) {
        final ImportRepository repository = new ImportRepository(this.storage, this.hasher);
        return new ImportJobs(sources,
                new ImportRunner(repository, this.clock, this.log.logger()),
                new SelfImport(repository, this.log.logger()), repository, this.lease,
                this.config, this.syncService, this.syncReceiver(), this.executors.worker(),
                this.clock, this.log.logger());
    }

    protected static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    protected final <E extends SakuraEvent> List<E> events(final Class<E> type) {
        return this.events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The lookup the services were built with, which asks {@link #countries} at each call. */
    protected final CountryLookup delegatingCountries() {
        return new DelegatingCountries();
    }

    private final class DelegatingCountries implements CountryLookup {

        @Override
        public Optional<Country> country(final InetAddress address) {
            return ServiceTest.this.countries.country(address);
        }

        @Override
        public State state() {
            return ServiceTest.this.countries.state();
        }
    }

    private final class DelegatingPermissions implements OfflinePermissions {

        @Override
        public boolean available() {
            return ServiceTest.this.offline.available();
        }

        @Override
        public CompletableFuture<Optional<Boolean>> has(final UUID uuid, final String node) {
            return ServiceTest.this.offline.has(uuid, node);
        }

        @Override
        public CompletableFuture<OptionalInt> weight(final UUID uuid) {
            return ServiceTest.this.offline.weight(uuid);
        }
    }
}
