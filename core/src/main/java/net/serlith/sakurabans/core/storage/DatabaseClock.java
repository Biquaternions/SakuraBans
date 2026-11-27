package net.serlith.sakurabans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public final class DatabaseClock {

    static final Duration SKEW_WARNING = Duration.ofSeconds(5);

    private static final String EPOCH_MILLIS =
            "CAST(FLOOR(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000) AS BIGINT)";
    private static final String MYSQL_EPOCH_MILLIS =
            "(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', UTC_TIMESTAMP(3)) DIV 1000)";

    private final StorageService storage;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public DatabaseClock(final StorageService storage, final InstantSource clock,
            final ComponentLogger logger) {
        this.storage = storage;
        this.clock = clock;
        this.logger = logger;
    }

    public static Field<Instant> now(final SQLDialect dialect) {
        return DSL.field(switch (dialect.family()) {
            case H2, POSTGRES -> EPOCH_MILLIS;
            case MARIADB, MYSQL -> MYSQL_EPOCH_MILLIS;
            default -> throw new IllegalArgumentException("no database clock for " + dialect);
        }, SbTypes.INSTANT);
    }

    public static Instant now(final DSLContext dsl) {
        return dsl.select(now(dsl.dialect())).fetchSingle().value1();
    }

    public CompletableFuture<Instant> now() {
        return this.storage.read(DatabaseClock::now);
    }

    public CompletableFuture<Duration> skew() {
        final Instant before = this.clock.instant();
        return this.now().thenApply(database -> {
            final Instant after = this.clock.instant();
            final Instant middle = before.plus(Duration.between(before, after).dividedBy(2));
            return Duration.between(middle, database);
        });
    }

    public CompletableFuture<Duration> checkHostClock() {
        return this.skew().thenApply(skew -> {
            if (skew.abs().compareTo(SKEW_WARNING) > 0) {
                this.logger.warn("This server's clock is {} ms {} the database's. Punishments"
                                + " expire, and durations are counted, by each server's own"
                                + " clock, so servers whose clocks differ end the same punishment"
                                + " at different times: keep every server's clock synchronised"
                                + " (NTP).", skew.abs().toMillis(),
                        skew.isNegative() ? "ahead of" : "behind");
            }
            return skew;
        });
    }
}
