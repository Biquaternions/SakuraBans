package net.serlith.sakurabans.core.storage.repository;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EditorSessionRepositoryTest extends DatabaseTest {

    private static final String FIRST = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String SECOND = "BBBBBBBBBBBBBBBBBBBBBB";

    @Test
    void sessionsAreStoredAndExpiredOnesGoWithEachNewOne() {
        final EditorSessionRepository sessions = new EditorSessionRepository(this.storage);
        final byte[] secret = new byte[32];
        secret[31] = 7;
        final PlayerOperator alex = PlayerOperator.of(UUID.randomUUID(), "Alex");
        sessions.create(FIRST, secret, "token-of-the-first", Set.of("settings", "templates"),
                alex, this.now.get(), this.now.get().minus(Duration.ofDays(1))).join();

        Assertions.assertThat(sessions.find(FIRST).join()).hasValueSatisfying(session -> {
            Assertions.assertThat(session.secret()).containsExactly(secret);
            Assertions.assertThat(session.creator()).isEqualTo(alex);
            Assertions.assertThat(session.createdAt()).isEqualTo(this.now.get());
            Assertions.assertThat(session.token()).isEqualTo("token-of-the-first");
            Assertions.assertThat(session.lastSequence()).isZero();
            Assertions.assertThat(session.sections()).containsExactlyInAnyOrder("settings",
                    "templates");
            Assertions.assertThat(session.toString()).doesNotContain("token-of-the-first");
        });
        Assertions.assertThat(sessions.find(SECOND).join()).isEmpty();

        // Each sequence is claimed once, and only upwards.
        Assertions.assertThat(sessions.claim(FIRST, 5).join()).isTrue();
        Assertions.assertThat(sessions.claim(FIRST, 5).join()).isFalse();
        Assertions.assertThat(sessions.claim(FIRST, 4).join()).isFalse();
        Assertions.assertThat(sessions.claim(SECOND, 9).join()).as("unknown").isFalse();
        Assertions.assertThat(sessions.find(FIRST).join().orElseThrow().lastSequence())
                .isEqualTo(5);
        final var racing = IntStream.range(0, 8)
                .mapToObj(ignored -> sessions.claim(FIRST, 6)).toList();
        Assertions.assertThat(racing).filteredOn(claim -> claim.join()).hasSize(1);

        final var later = this.now.get().plus(Duration.ofHours(25));
        sessions.create(SECOND, new byte[32], "token-two", Set.of(),
                ConsoleOperator.INSTANCE, later, later.minus(Duration.ofDays(1))).join();
        Assertions.assertThat(sessions.find(FIRST).join()).isEmpty();
        Assertions.assertThat(sessions.find(SECOND).join()).hasValueSatisfying(session ->
                Assertions.assertThat(session.creator()).isEqualTo(ConsoleOperator.INSTANCE));
        Assertions.assertThat(sessions.deleteBefore(later.plusSeconds(1)).join()).isOne();
        Assertions.assertThat(this.count(SakuraTables.EDITOR_SESSIONS)).isZero();
    }
}
