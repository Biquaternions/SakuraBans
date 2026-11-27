package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.AuditFilter;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S70: audit.retention, applied by the sweeper's lease holder. */
class AuditRetentionTest extends ServiceTest {

    private AuditRepository audit;

    @Override
    protected String configYaml() {
        return "audit:\n  retention: 30d\n";
    }

    @BeforeEach
    void repository() {
        this.audit = new AuditRepository(this.storage);
    }

    private void entry(final long daysAgo, final String subject) {
        this.audit.append(this.now.get().minus(Duration.ofDays(daysAgo)), CONSOLE,
                AuditAction.UPDATE, SubjectType.TEMPLATE, subject, null, "v").join();
    }

    private List<String> subjects() {
        return this.audit.batch(AuditFilter.none(), null, 100).join().stream()
                .map(AuditEntry::subjectId).toList();
    }

    private List<String> infoLines() {
        return Stream.of("info").flatMap(level -> this.log.entries(level).stream())
                .map(RecordingLogger.Entry::format).toList();
    }

    @Test
    void prunesOnlyOlderEntriesAndSaysHowManyInOneLine() {
        this.entry(40, "ancient");
        this.entry(31, "old");
        this.entry(29, "recent");
        this.entry(0, "today");

        this.sweeper.sweep().join();

        Assertions.assertThat(this.subjects()).containsExactly("today", "recent");
        Assertions.assertThat(this.infoLines()).filteredOn(line -> line.contains("audit entries"))
                .singleElement().asString().contains("Deleted 2 audit entries");
    }

    @Test
    void onlyTheLeaseHolderPrunes() {
        this.entry(40, "ancient");
        final Lease other = new Lease(this.storage, UUID.randomUUID());
        Assertions.assertThat(other.tryAcquire("sweeper", Duration.ofMinutes(5)).join()).isTrue();

        this.sweeper.sweep().join();
        Assertions.assertThat(this.subjects()).containsExactly("ancient");

        other.release("sweeper").join();
        this.sweeper.sweep().join();
        Assertions.assertThat(this.subjects()).isEmpty();
    }

    @Test
    void runsAtMostOncePerHour() {
        this.entry(40, "first");
        this.sweeper.sweep().join();
        this.entry(40, "second");

        this.now.set(this.now.get().plus(Duration.ofMinutes(10)));
        this.sweeper.sweep().join();
        Assertions.assertThat(this.subjects()).containsExactly("second");

        this.now.set(this.now.get().plus(Duration.ofMinutes(51)));
        this.sweeper.sweep().join();
        Assertions.assertThat(this.subjects()).isEmpty();
    }

    @Test
    void goesInBatchesUntilNothingOlderIsLeft() {
        final int count = AuditRetention.BATCH + 25;
        this.storage.write(configuration -> {
            for (int i = 0; i < count; i++) {
                this.audit.append(this.now.get().minus(Duration.ofDays(60)), CONSOLE,
                        AuditAction.UPDATE, SubjectType.TEMPLATE, "t" + i, null, null).join();
            }
            return null;
        }).join();
        this.entry(1, "kept");

        this.sweeper.sweep().join();

        Assertions.assertThat(this.subjects()).containsExactly("kept");
        Assertions.assertThat(this.infoLines()).anyMatch(line -> line.contains("Deleted "
                + count + " audit entries"));
    }
}
