package net.serlith.sakurabans.api.audit;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Page;

/**
 * The audit log. Implemented by SakuraBans; don't implement it.
 */
public interface AuditService {

    /**
     * Selects audit entries.
     *
     * @param query the filters and the cursor
     * @return one page of entries, newest first, completed on a worker thread
     */
    CompletableFuture<Page<AuditEntry>> query(AuditQuery query);
}
