package net.serlith.sakurabans.core.storage.repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditRepositoryTest extends DatabaseTest {

    private static final UUID MOD = UUID.fromString("0f6b8d6a-1d34-4b2e-9c1e-5b7a1e1d2c3b");
    private static final Actor MODERATOR = Actor.of(PlayerOperator.of(MOD, "Mod"), "web");
    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");

    private AuditRepository audit;

    @BeforeEach
    void create() {
        this.audit = new AuditRepository(this.storage);
    }

    private long append(final Actor actor, final AuditAction action, final String subject) {
        final long id = this.audit.append(this.now.get(), actor, action, SubjectType.PUNISHMENT,
                subject, "old", "new").join();
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        return id;
    }

    @Test
    void entriesRoundTripNewestFirst() {
        final long first = this.append(MODERATOR, AuditAction.EDIT_REASON, "1");
        final long second = this.append(CONSOLE, AuditAction.VOID, "1");

        final List<AuditEntry> entries = this.audit.about(SubjectType.PUNISHMENT, "1", 10)
                .join();

        Assertions.assertThat(entries).extracting(AuditEntry::id).containsExactly(second, first);
        final AuditEntry entry = entries.getLast();
        Assertions.assertThat(entry.actor()).isEqualTo(MODERATOR);
        Assertions.assertThat(entry.action()).isEqualTo(AuditAction.EDIT_REASON);
        Assertions.assertThat(entry.subjectId()).isEqualTo("1");
        Assertions.assertThat(entry.oldValue()).isEqualTo("old");
        Assertions.assertThat(entry.newValue()).isEqualTo("new");
    }

    @Test
    void onlyTheSubjectsNewestEntriesAreRead() {
        final long older = this.append(MODERATOR, AuditAction.EDIT_REASON, "1");
        final long newer = this.append(CONSOLE, AuditAction.VOID, "1");
        this.append(MODERATOR, AuditAction.VOID, "2");

        Assertions.assertThat(this.audit.about(SubjectType.PUNISHMENT, "1", 10).join())
                .extracting(AuditEntry::id).containsExactly(newer, older);
        Assertions.assertThat(this.audit.about(SubjectType.PUNISHMENT, "1", 1).join())
                .extracting(AuditEntry::id).containsExactly(newer);
        Assertions.assertThat(this.audit.about(SubjectType.TEMPLATE, "1", 10).join()).isEmpty();
    }
}
