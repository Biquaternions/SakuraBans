package net.serlith.sakurabans.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starts a {@link StorageService} on a fresh database for every test: H2 here, and MariaDB,
 * MySQL or PostgreSQL in the integration tests, which subclass the same tests and override
 * {@link #database()} (S16).
 */
public abstract class DatabaseTest {

    @TempDir
    protected Path dir;

    protected final RecordingLogger log = new RecordingLogger();
    protected final SakuraExecutors executors = new SakuraExecutors(failure -> { });
    protected final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-11-19T12:00:00Z"));
    protected final InstantSource clock = () -> this.now.get();
    protected ConfigService config;
    protected StorageService storage;

    protected TestDatabase database() {
        return TestDatabase.H2;
    }

    /** The {@code config.yml} for the test; keys it leaves out keep their defaults. */
    protected String configYaml() {
        return "";
    }

    @BeforeEach
    final void openDatabase() throws Exception {
        final Optional<String> yaml = this.database().freshStorageYaml();
        if (yaml.isPresent()) {
            Files.writeString(this.dir.resolve("storage.yml"), yaml.get());
        }
        this.config = TestConfig.load(this.dir, this.configYaml(), this.log);
        this.storage = new StorageService(this.dir, this.config,
                new SchemaManager(this.log.logger()), this.executors, this.log.logger());
        this.storage.start();
    }

    @AfterEach
    final void closeDatabase() {
        this.storage.stop();
        this.executors.close();
    }

    /** For work the code under test starts without waiting (recording a connection, ...). */
    protected static void eventually(final BooleanSupplier condition) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the condition didn't become true within 5 s");
            }
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    protected final boolean onH2() {
        return this.database() == TestDatabase.H2;
    }

    /** The configured schema, as {@code SchemaManager} gets it. */
    protected final Optional<String> schema() {
        return StorageService.schemaName(this.config.storage());
    }

    protected final <T> T read(final Function<DSLContext, T> query) {
        return this.storage.read(query).join();
    }

    protected final int count(final Table<?> table) {
        return this.read(dsl -> dsl.fetchCount(table));
    }

    protected final int count(final Table<?> table, final Condition condition) {
        return this.read(dsl -> dsl.fetchCount(table, condition));
    }
}
