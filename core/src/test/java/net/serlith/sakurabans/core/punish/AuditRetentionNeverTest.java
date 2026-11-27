package net.serlith.sakurabans.core.punish;

import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditFilter;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Audit.retention defaults to never. */
class AuditRetentionNeverTest extends ServiceTest {

    @Test
    void nothingIsPrunedByDefault() {
        final AuditRepository audit = new AuditRepository(this.storage);
        audit.append(this.now.get().minusSeconds(4000L * 86_400), CONSOLE, AuditAction.UPDATE,
                SubjectType.TEMPLATE, "ancient", null, null).join();
        Assertions.assertThat(this.config.settings().audit().retention()).isNull();

        this.sweeper.sweep().join();

        Assertions.assertThat(audit.batch(AuditFilter.none(), null, 10).join()).hasSize(1);
    }
}
