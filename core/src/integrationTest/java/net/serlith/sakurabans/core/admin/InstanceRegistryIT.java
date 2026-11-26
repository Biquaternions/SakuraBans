package net.serlith.sakurabans.core.admin;

import net.serlith.sakurabans.core.storage.ContainerDatabase;
import net.serlith.sakurabans.core.storage.TestDatabase;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/** {@link InstanceRegistryTest} on MariaDB, MySQL and PostgreSQL. */
@ParameterizedClass
@EnumSource(ContainerDatabase.class)
class InstanceRegistryIT extends InstanceRegistryTest {

    @Parameter
    private ContainerDatabase container;

    @Override
    protected TestDatabase database() {
        return this.container;
    }
}
