package net.serlith.sakurabans.core.player;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class NameCacheTest {

    private final NameCache cache = new NameCache();

    @Test
    void looksUpBothWaysIgnoringCase() {
        final UUID uuid = UUID.randomUUID();
        this.cache.put(uuid, "Notch");

        Assertions.assertThat(this.cache.name(uuid)).contains("Notch");
        Assertions.assertThat(this.cache.uuid("nOTCH")).contains(uuid);
        Assertions.assertThat(this.cache.uuid("jeb_")).isEmpty();
    }

    @Test
    void nameChangesForgetTheOldName() {
        final UUID uuid = UUID.randomUUID();
        this.cache.put(uuid, "Old");
        this.cache.put(uuid, "New");

        Assertions.assertThat(this.cache.uuid("old")).isEmpty();
        Assertions.assertThat(this.cache.uuid("new")).contains(uuid);
    }

    @Test
    void namesTakenOverPointToTheNewAccount() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        this.cache.put(first, "Shared");
        this.cache.put(second, "Shared");
        this.cache.put(first, "Renamed");

        Assertions.assertThat(this.cache.uuid("shared")).contains(second);
    }

    @Test
    void suggestsByPrefix() {
        this.cache.put(UUID.randomUUID(), "Steve");
        this.cache.put(UUID.randomUUID(), "stephanie");
        this.cache.put(UUID.randomUUID(), "Alex");

        Assertions.assertThat(this.cache.suggestions("ste", 10))
                .containsExactly("stephanie", "Steve");
        Assertions.assertThat(this.cache.suggestions("", 1)).hasSize(1);
    }
}
