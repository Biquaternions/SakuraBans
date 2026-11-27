package net.serlith.sakurabans.core.command.module;

import java.util.UUID;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** With display.censor-ip-addresses off (the default), nobody's audit view is masked. */
class AuditUncensoredTest extends CommandTest {

    @Test
    void addressesShowToAuditorsWithoutViewips() {
        new AuditRepository(this.storage).append(this.now.get(), MODERATOR, AuditAction.CREATE,
                SubjectType.PUNISHMENT, "9", null, "BAN 198.51.100.77 global permanent x").join();
        final FakeCommandActor auditor = FakeCommandActor.player(UUID.randomUUID(), "Auditor",
                Permissions.ADMIN_AUDIT);

        this.run(auditor, "sb audit");

        Assertions.assertThat(String.join("\n", auditor.texts())).contains("198.51.100.77");
    }
}
