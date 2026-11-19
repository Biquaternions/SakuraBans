package net.serlith.sakurabans.core.bootstrap;

import java.time.InstantSource;
import java.util.UUID;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.platform.LatePlatform;
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
}
