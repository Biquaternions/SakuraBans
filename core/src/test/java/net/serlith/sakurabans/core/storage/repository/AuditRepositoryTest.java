package net.serlith.sakurabans.core.storage.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PlayerOperator;
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
        final long second = this.append(CONSOLE, AuditAction.VOID, "2");

        final Page<AuditEntry> page = this.audit.query(AuditQuery.builder().build()).join();

        Assertions.assertThat(page.items()).extracting(AuditEntry::id).containsExactly(second,
                first);
        final AuditEntry entry = page.items().getLast();
        Assertions.assertThat(entry.actor()).isEqualTo(MODERATOR);
        Assertions.assertThat(entry.action()).isEqualTo(AuditAction.EDIT_REASON);
        Assertions.assertThat(entry.subjectId()).isEqualTo("1");
        Assertions.assertThat(entry.oldValue()).contains("old");
        Assertions.assertThat(entry.newValue()).contains("new");
        Assertions.assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void queriesFilter() {
        final long edit = this.append(MODERATOR, AuditAction.EDIT_REASON, "1");
        final long voided = this.append(CONSOLE, AuditAction.VOID, "1");
        final long other = this.append(MODERATOR, AuditAction.VOID, "2");

        Assertions.assertThat(this.ids(AuditQuery.builder().subject(SubjectType.PUNISHMENT, "1")))
                .containsExactly(voided, edit);
        Assertions.assertThat(this.ids(AuditQuery.builder().actor(PlayerOperator.of(MOD,
                "Renamed"))))
                .containsExactly(other, edit);
        Assertions.assertThat(this.ids(AuditQuery.builder().source("console")))
                .containsExactly(voided);
        Assertions.assertThat(this.ids(AuditQuery.builder().actions(Set.of(AuditAction.VOID))))
                .containsExactly(other, voided);
        Assertions.assertThat(this.ids(AuditQuery.builder().subject(SubjectType.TEMPLATE, null)))
                .isEmpty();
    }

    @Test
    void pagesByCursor() {
        final List<Long> appended = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            appended.add(this.append(CONSOLE, AuditAction.UPDATE, String.valueOf(i)));
        }

        final List<Long> walked = new ArrayList<>();
        final List<Integer> pages = new ArrayList<>();
        String cursor = null;
        do {
            final Page<AuditEntry> page = this.audit.query(AuditQuery.builder().pageSize(2)
                    .after(cursor).build()).join();
            page.items().forEach(entry -> walked.add(entry.id()));
            pages.add(page.page());
            cursor = page.nextCursor().orElse(null);
        } while (cursor != null);

        Assertions.assertThat(walked).containsExactlyElementsOf(appended.reversed());
        Assertions.assertThat(pages).containsExactly(1, 2, 3);
    }

    private List<Long> ids(final AuditQuery.Builder query) {
        return this.audit.query(query.build()).join().items().stream().map(AuditEntry::id)
                .toList();
    }
}
