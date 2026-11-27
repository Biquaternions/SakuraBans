package net.serlith.sakurabans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import net.serlith.sakurabans.core.RecordingLogger;
import org.assertj.core.api.Assertions;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/** On H2 here; {@code DatabaseClockIT} runs it on MariaDB, MySQL and PostgreSQL. */
class DatabaseClockTest extends DatabaseTest {

    private static String render(final SQLDialect dialect) {
        return DSL.using(dialect).render(DSL.select(DatabaseClock.now(dialect)));
    }

    @Test
    void everyDialectGetsEpochMillisecondsInUtc() {
        Assertions.assertThat(render(SQLDialect.H2)).contains(
                "CAST(FLOOR(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000) AS BIGINT)");
        Assertions.assertThat(render(SQLDialect.POSTGRES)).contains(
                "CAST(FLOOR(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000) AS BIGINT)");
        for (final SQLDialect dialect : new SQLDialect[] {SQLDialect.MARIADB, SQLDialect.MYSQL}) {
            Assertions.assertThat(render(dialect)).as(dialect.name()).contains("(TIMESTAMPDIFF("
                    + "MICROSECOND, '1970-01-01 00:00:00', UTC_TIMESTAMP(3)) DIV 1000)")
                    .doesNotContain("UNIX_TIMESTAMP");
        }
        Assertions.assertThatThrownBy(() -> DatabaseClock.now(SQLDialect.SQLITE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDatabasesTimeIsNowInMilliseconds() {
        final Instant before = Instant.now().minusSeconds(2);
        final Instant database = this.read(DatabaseClock::now);
        final Instant after = Instant.now().plusSeconds(2);

        // The containers run on this machine, so their clocks agree with it.
        Assertions.assertThat(database).isBetween(before, after);
        Assertions.assertThat(database.getNano() % 1_000_000).isZero();
    }

    private DatabaseClock clockOff(final Duration offset, final RecordingLogger log) {
        final InstantSource host = () -> Instant.now().plus(offset);
        return new DatabaseClock(this.storage, host, log.logger());
    }

    @Test
    void hostClocksFarFromTheDatabasesAreWarnedAbout() {
        final RecordingLogger ahead = new RecordingLogger();
        final Duration skew = this.clockOff(Duration.ofMinutes(3), ahead).checkHostClock().join();

        Assertions.assertThat(skew).isBetween(Duration.ofMinutes(-3).minusSeconds(2),
                Duration.ofMinutes(-3).plusSeconds(2));
        Assertions.assertThat(ahead.entries("warn")).extracting(RecordingLogger.Entry::format)
                .singleElement().asString().contains("ahead of the database's", "NTP");

        final RecordingLogger behind = new RecordingLogger();
        this.clockOff(Duration.ofMinutes(-3), behind).checkHostClock().join();
        Assertions.assertThat(behind.entries("warn")).extracting(RecordingLogger.Entry::format)
                .singleElement().asString().contains("behind the database's");

        final RecordingLogger close = new RecordingLogger();
        this.clockOff(Duration.ofSeconds(1), close).checkHostClock().join();
        Assertions.assertThat(close.entries("warn")).isEmpty();
    }
}
