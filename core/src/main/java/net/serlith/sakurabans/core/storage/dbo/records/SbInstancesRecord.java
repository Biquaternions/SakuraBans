package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.platform.PlatformType;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbInstancesRecord extends UpdatableRecordImpl<SbInstancesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbInstancesRecord() {
        super(SakuraTables.INSTANCES);
    }

    public UUID id() {
        return this.get(SakuraTables.INSTANCES.ID);
    }

    public SbInstancesRecord id(final UUID value) {
        this.set(SakuraTables.INSTANCES.ID, value);
        return this;
    }

    public String serverName() {
        return this.get(SakuraTables.INSTANCES.SERVER_NAME);
    }

    public SbInstancesRecord serverName(final String value) {
        this.set(SakuraTables.INSTANCES.SERVER_NAME, value);
        return this;
    }

    public PlatformType platform() {
        return this.get(SakuraTables.INSTANCES.PLATFORM);
    }

    public SbInstancesRecord platform(final PlatformType value) {
        this.set(SakuraTables.INSTANCES.PLATFORM, value);
        return this;
    }

    public String pluginVersion() {
        return this.get(SakuraTables.INSTANCES.PLUGIN_VERSION);
    }

    public SbInstancesRecord pluginVersion(final String value) {
        this.set(SakuraTables.INSTANCES.PLUGIN_VERSION, value);
        return this;
    }

    public Instant startedAt() {
        return this.get(SakuraTables.INSTANCES.STARTED_AT);
    }

    public SbInstancesRecord startedAt(final Instant value) {
        this.set(SakuraTables.INSTANCES.STARTED_AT, value);
        return this;
    }

    public Instant lastHeartbeat() {
        return this.get(SakuraTables.INSTANCES.LAST_HEARTBEAT);
    }

    public SbInstancesRecord lastHeartbeat(final Instant value) {
        this.set(SakuraTables.INSTANCES.LAST_HEARTBEAT, value);
        return this;
    }
}
