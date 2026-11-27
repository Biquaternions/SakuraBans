package net.serlith.sakurabans.core.storage;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.StorageSettings;
import net.serlith.sakurabans.core.configuration.settings.StorageType;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.util.Futures;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.TransactionalCallable;
import org.jooq.conf.Settings;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class StorageService {

    static final int CONFLICT_RETRIES = 2;
    public static final String ADDRESS_HASH_KEY_CHECK = "address_hash_key_check";
    static final String WRONG_KEY = "storage.yml: address-hash-key is not the key this database"
            + " was set up with, so the addresses it stores would never match. Every server that"
            + " uses one database needs the same key: copy the address-hash-key line from the"
            + " storage.yml of a server that already uses this database into this server's"
            + " storage.yml (a new server's file has a generated key of its own: replace it),"
            + " then start again.";

    private final Path dataDirectory;
    private final ConfigService config;
    private final SchemaManager schema;
    private final AddressHasher hasher;
    private final SakuraExecutors executors;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable Open> open = new AtomicReference<>();

    private record Open(HikariDataSource dataSource, DSLContext dsl) {
    }

    public StorageService(final Path dataDirectory, final ConfigService config,
            final SchemaManager schema, final AddressHasher hasher,
            final SakuraExecutors executors, final ComponentLogger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.schema = schema;
        this.hasher = hasher;
        this.executors = executors;
        this.logger = logger;
    }

    public void start() {
        final StorageSettings settings = this.config.storage();
        final String schemaName = schemaName(settings).orElse(null);
        SakuraTables.initialize(settings.tablePrefix(), schemaName);
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        final HikariDataSource dataSource;
        try {
            dataSource = new HikariDataSource(HikariConfigs.create(settings, this.dataDirectory));
        } catch (final RuntimeException e) {
            throw new StorageException("Can't connect to the " + settings.type()
                    + " database: " + e.getMessage(), e);
        }
        try {
            final DSLContext dsl = DSL.using(dataSource, HikariConfigs.dialect(settings),
                    jooqSettings(schemaName));
            this.schema.prepare(dsl, schemaName);
            final AddressHasher candidate = AddressHasher.keyed(settings.addressHashKey());
            checkAddressHashKey(dsl, candidate.fingerprint());
            this.hasher.key(settings.addressHashKey());
            this.open.set(new Open(dataSource, dsl));
        } catch (final RuntimeException e) {
            dataSource.close();
            throw e;
        }
        this.logger.info("Connected to {}", describe(settings));
    }

    /** The first start against a database records the key's fingerprint; later ones match it. */
    static void checkAddressHashKey(final DSLContext dsl, final String fingerprint) {
        try {
            dsl.insertInto(SakuraTables.META)
                    .set(SakuraTables.META.K, ADDRESS_HASH_KEY_CHECK)
                    .set(SakuraTables.META.V, fingerprint)
                    .onConflictDoNothing()
                    .execute();
        } catch (final IntegrityConstraintViolationException e) {
            // H2's emulated "do nothing" can still lose the race; the stored value decides.
        }
        final String stored = dsl.select(SakuraTables.META.V).from(SakuraTables.META)
                .where(SakuraTables.META.K.eq(ADDRESS_HASH_KEY_CHECK))
                .fetchSingle(SakuraTables.META.V);
        if (!stored.strip().equals(fingerprint)) {
            throw new StorageException(WRONG_KEY);
        }
    }

    private static String describe(final StorageSettings settings) {
        return settings.type() == StorageType.H2
                ? "the H2 database " + settings.h2File()
                : settings.type() + " at " + settings.host() + ":" + settings.effectivePort()
                        + "/" + settings.database();
    }

    public void stop() {
        final Open current = this.open.getAndSet(null);
        if (current != null) {
            current.dataSource().close();
        }
    }

    public boolean running() {
        return this.open.get() != null;
    }

    public SQLDialect dialect() {
        return this.dsl().dialect();
    }

    public <T> CompletableFuture<T> read(final Function<DSLContext, T> query) {
        return this.run(query);
    }

    public <T> CompletableFuture<T> write(final TransactionalCallable<T> work) {
        return this.run(dsl -> dsl.transactionResult(work));
    }

    /**
     * Like {@link #write}, but runs the transaction again (up to {@value #CONFLICT_RETRIES}
     * times) when it failed on a unique or foreign key because a concurrent writer won the race.
     * The work must be safe to repeat: it reads what the winner wrote and decides again.
     */
    public <T> CompletableFuture<T> writeRetryingConflicts(final TransactionalCallable<T> work) {
        return this.writeRetryingConflicts(work, CONFLICT_RETRIES);
    }

    private <T> CompletableFuture<T> writeRetryingConflicts(final TransactionalCallable<T> work,
            final int retries) {
        return this.write(work).exceptionallyCompose(failure -> {
            if (retries > 0 && Futures.unwrap(failure)
                    instanceof IntegrityConstraintViolationException) {
                return this.writeRetryingConflicts(work, retries - 1);
            }
            return CompletableFuture.failedFuture(failure);
        });
    }

    private <T> CompletableFuture<T> run(final Function<DSLContext, T> work) {
        final DSLContext dsl;
        try {
            dsl = this.dsl();
        } catch (final IllegalStateException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> work.apply(dsl), this.executors.worker());
    }

    private DSLContext dsl() {
        final Open current = this.open.get();
        if (current == null) {
            throw new IllegalStateException("the storage isn't running");
        }
        return current.dsl();
    }

    static Settings jooqSettings(final @Nullable String schemaName) {
        return new Settings()
                .withRenderCatalog(false)
                .withRenderSchema(schemaName != null)
                .withExecuteLogging(false);
    }

    static Optional<String> schemaName(final StorageSettings settings) {
        return settings.type() == StorageType.POSTGRESQL
                ? Optional.ofNullable(settings.schema())
                : Optional.empty();
    }
}
