package net.serlith.sakurabans.core.audit;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.audit.AuditService;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;

public final class SakuraAuditService implements AuditService {

    private final AuditRepository audit;

    public SakuraAuditService(final AuditRepository audit) {
        this.audit = audit;
    }

    @Override
    public CompletableFuture<Page<AuditEntry>> query(final AuditQuery query) {
        final AuditQuery checked;
        try {
            // Rebuilt, so a caller's own implementation is validated like ours.
            checked = AuditQuery.builder()
                    .subject(query.subjectType().orElse(null), query.subjectId().orElse(null))
                    .actor(query.actor().map(ApiValues::operator).orElse(null))
                    .source(query.source().orElse(null))
                    .actions(query.actions())
                    .between(query.from().orElse(null), query.to().orElse(null))
                    .after(query.after().orElse(null))
                    .pageSize(query.pageSize())
                    .build();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.audit.query(checked);
    }

}
