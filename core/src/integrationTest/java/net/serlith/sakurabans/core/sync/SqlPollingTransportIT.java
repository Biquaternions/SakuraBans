package net.serlith.sakurabans.core.sync;

import net.serlith.sakurabans.core.storage.ContainerDatabase;
import net.serlith.sakurabans.core.storage.TestDatabase;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/** {@link SqlPollingTransportTest} on MariaDB, MySQL and PostgreSQL. */
@ParameterizedClass
@EnumSource(ContainerDatabase.class)
class SqlPollingTransportIT extends SqlPollingTransportTest {

    @Parameter
    private ContainerDatabase container;

    @Override
    protected TestDatabase database() {
        return this.container;
    }
}
