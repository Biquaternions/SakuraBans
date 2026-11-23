package net.serlith.sakurabans.core.storage.repository;

import net.serlith.sakurabans.core.storage.ContainerDatabase;
import net.serlith.sakurabans.core.storage.TestDatabase;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/** {@link StrictnessTest} on MariaDB, MySQL and PostgreSQL. */
@ParameterizedClass
@EnumSource(ContainerDatabase.class)
class StrictnessIT extends StrictnessTest {

    @Parameter
    private ContainerDatabase container;

    @Override
    protected TestDatabase database() {
        return this.container;
    }
}
