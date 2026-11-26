package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbInstancesRecord extends UpdatableRecordImpl<SbInstancesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbInstancesRecord() {
        super(SakuraTables.INSTANCES);
    }

    public String id() {
        return this.get(SakuraTables.INSTANCES.ID);
    }

    public String serverName() {
        return this.get(SakuraTables.INSTANCES.SERVER_NAME);
    }

    public String categories() {
        return this.get(SakuraTables.INSTANCES.CATEGORIES);
    }

    public String platform() {
        return this.get(SakuraTables.INSTANCES.PLATFORM);
    }

    public String pluginVersion() {
        return this.get(SakuraTables.INSTANCES.PLUGIN_VERSION);
    }

    public Instant startedAt() {
        return this.get(SakuraTables.INSTANCES.STARTED_AT);
    }

    public Instant lastSeenAt() {
        return this.get(SakuraTables.INSTANCES.LAST_SEEN_AT);
    }

    public String fileValues() {
        return this.get(SakuraTables.INSTANCES.FILE_VALUES);
    }
}
