package net.serlith.sakurabans.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
import net.serlith.sakurabans.core.alts.AltService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.auth.SakuraAuthorizationService;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.enforce.ChannelReceiver;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.CommandBlacklist;
import net.serlith.sakurabans.core.enforce.EnforcementMessages;
import net.serlith.sakurabans.core.enforce.LocalEnforcer;
import net.serlith.sakurabans.core.enforce.LoginGate;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.fake.FakePlatform;
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
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.permission.OfflinePermissions;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.ExpirySweeper;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.punish.PunishmentFactory;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.scope.ServerNameDetector;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.KindRepository;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.template.TemplateActions;
import org.junit.jupiter.api.BeforeEach;

/**
 * The services of S17-S20 wired like {@code CoreComponents} does, on a fresh database, with a
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
    protected ActionCheck actionCheck = ActionCheck.ALLOW_ALL;

    protected SakuraEventBus bus;
    protected MessageService messages;
    protected Placeholders placeholders;
    protected NameCache names;
    protected ServerNameDetector serverNames;
    protected SakuraScopeService scopes;
    protected PlayerRepository players;
    protected PunishmentRepository punishments;
    protected Lease lease;
    protected MuteCache mutes;
    protected EnforcementMessages enforcementMessages;
    protected ProxyChannel channel;
    protected LocalEnforcer enforcer;
    protected ChannelReceiver channelReceiver;
    protected SakuraPunishmentService service;
    protected NotificationService notifications;
    protected LoginGate loginGate;
    protected ChatGate chatGate;
    protected ExpirySweeper sweeper;
    protected SyncService syncService;
    protected SakuraAllowListService allowList;
    protected AltService altService;
    protected ActiveRangeCache ranges;
    protected SakuraKindService kinds;
    protected PermissionService permissions;
    protected ExemptionService exemption;
    protected SakuraAuthorizationService authorization;
    /** What LuckPerms would say about offline players; none by default. */
    protected OfflinePermissions offline = OfflinePermissions.NONE;
    protected LimitService limits;
    protected TemplateRepository templateRepository;
    protected SakuraTemplateService templates;
    protected TemplateActions templateActions;
    protected final SakuraIntegrationRegistry integrations = new SakuraIntegrationRegistry();
    protected InetAddress home;

    protected boolean proxy() {
        return false;
    }

    protected ServiceTest() {
        this(new FakePlatform());
    }

    protected ServiceTest(final FakePlatform platform) {
        this.platform = platform;
    }

    @BeforeEach
    final void wireServices() throws UnknownHostException {
        this.home = InetAddress.getByName("203.0.113.7");
        this.bus = new SakuraEventBus(this.log.logger());
        this.bus.subscribe(SakuraEvent.class, EventPriority.MONITOR, this.events::add);
        this.messages = new MessageService(this.dir, this.config, this.log.logger());
        this.messages.load();
        this.placeholders =
                new Placeholders(this.messages, new Formats(this.messages, this.config));
        this.names = new NameCache();
        // As if the proxy had named this backend "lobby".
        this.serverNames = new ServerNameDetector(this.dir, this.config, Runnable::run,
                this.log.logger());
        this.serverNames.detected("lobby");
        this.scopes = new SakuraScopeService(this.config, this.platform, this.proxy(),
                this.serverNames, this.log.logger());
        this.players = new PlayerRepository(this.storage);
        this.syncService = new SyncService(this.published::add, this.log.logger());
        this.allowList = new SakuraAllowListService(new AllowListRepository(this.storage),
                this.syncService, this.config, this.clock);
        this.allowList.reload().join();
        this.kinds = new SakuraKindService(new KindRepository(this.storage),
                (actor, action) -> this.actionCheck.check(actor, action), this.syncService,
                this.bus, this.clock);
        this.kinds.start().join();
        this.punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(Long::toString), this.allowList, this.kinds);
        this.altService = new AltService(this.players, this.punishments, this.allowList,
                this.integrations, this.clock, this.log.logger());
        this.lease = new Lease(this.storage, UUID.randomUUID(), this.clock);
        this.ranges = new ActiveRangeCache(this.punishments, this.players, this.allowList,
                this.clock);
        this.mutes = new MuteCache(this.punishments, this.scopes, this.config, this.clock,
                this.ranges);
        this.enforcementMessages =
                new EnforcementMessages(this.messages, this.placeholders, this.names, this.clock);
        this.notifications = new NotificationService(this.platform, this.messages,
                this.placeholders, this.names, this.config, this.bus, new Throttle(this.clock),
                Runnable::run, this.clock);
        this.notifications.start();
        this.channel = new ProxyChannel(new MetaRepository(this.storage), this.log.logger());
        this.channel.load();
        this.enforcer = new LocalEnforcer(this.platform, this.scopes, this.punishments, this.mutes,
                this.enforcementMessages, this.config, this.channel, this.clock,
                this.ranges, this.log.logger());
        this.channelReceiver = new ChannelReceiver(this.channel, this.scopes, this.serverNames,
                this.punishments, this.enforcer, this.clock, this.log.logger());
        this.permissions = new PermissionService(this.platform, new DelegatingPermissions(),
                this.log.logger());
        this.exemption = new ExemptionService(this.config, this.permissions, this.log.logger());
        this.authorization = new SakuraAuthorizationService(this.permissions, this.exemption,
                this.kinds, this.punishments, this.integrations, this.config, this.log.logger());
        this.limits = new LimitService(this.config, this.permissions, new CooldownTracker(),
                this.clock);
        this.templateRepository = new TemplateRepository(this.storage, this.allowList);
        this.templates = new SakuraTemplateService(this.templateRepository,
                new MetaRepository(this.storage), new TemplateFiles(this.dir), this.kinds,
                this.integrations, (actor, action) -> this.actionCheck.check(actor, action),
                this.syncService, this.bus, this.clock, this.log.logger());
        this.templates.reload().join();
        this.templateActions = new TemplateActions(this.bus, this.templates, this.platform,
                this.names, this.log.logger());
        this.templateActions.start();
        this.service = new SakuraPunishmentService(this.lifecycle, this.executors.worker(),
                this.clock, this.config, this.scopes, this.punishments,
                new PunishmentFactory(this.scopes, this.config, this.players, this.platform,
                        this.clock, this.templates, this.kinds),
                this.mutes, this.enforcer, (actor, action) -> this.actionCheck.check(actor, action),
                this.limits, this.exemption, this.bus, this.syncService);
        this.loginGate = new LoginGate(this.lifecycle, this.config, this.scopes,
                this.punishments, this.players, this.names, this.mutes,
                this.enforcementMessages, this.notifications, this.bus, this.clock,
                this.allowList, this.altService, this.ranges, this.log.logger());
        this.chatGate = new ChatGate(this.mutes, new CommandBlacklist(this.config),
                this.enforcementMessages, this.notifications, this.bus, this.log.logger());
        this.sweeper = new ExpirySweeper(this.lease, this.punishments, this.mutes, this.enforcer,
                this.syncService, this.bus, this.executors, this.platform, this.clock,
                this.log.logger());
        this.lifecycle.advance(LifecycleState.NEW, LifecycleState.CREATED);
        this.lifecycle.advance(LifecycleState.CREATED, LifecycleState.STARTING);
        this.lifecycle.advance(LifecycleState.STARTING, LifecycleState.RUNNING);
    }

    protected static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    protected final <E extends SakuraEvent> List<E> events(final Class<E> type) {
        return this.events.stream().filter(type::isInstance).map(type::cast).toList();
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
