package net.serlith.sakurabans.core.admin;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class InstanceRegistryTest extends DatabaseTest {

    @Override
    protected String configYaml() {
        return "server:\n  name: lobby\n  categories: [hub]\nreasons:\n  default: Lobby rules\n";
    }

    private InstanceRegistry registry() {
        return new InstanceRegistry(this.dir, this.config, new FakePlatform(),
                new InstanceRepository(this.storage), this.executors, this.clock,
                this.log.logger());
    }

    @Test
    void eachServerKeepsOneRowWithItsFileValues() throws Exception {
        final InstanceRegistry registry = this.registry();
        registry.start().join();
        final String id = registry.id().orElseThrow();

        Assertions.assertThat(registry.instances()).singleElement().satisfies(row -> {
            Assertions.assertThat(row.id()).isEqualTo(id);
            Assertions.assertThat(row.serverName()).isEqualTo("lobby");
            Assertions.assertThat(row.categories()).containsExactly("hub");
            Assertions.assertThat(row.platform()).isEqualTo("paper");
            Assertions.assertThat(InstanceRegistry.fileValues(row))
                    .containsEntry("reasons.default", "Lobby rules")
                    .doesNotContainKeys("server.name", "web.url", "web.token");
        });
        registry.stop();

        // The id survives a restart; a reload updates the row.
        Files.writeString(this.dir.resolve("config.yml"), "server:\n  name: hub-1\n");
        this.config.reload();
        final InstanceRegistry again = this.registry();
        again.start().join();
        Assertions.assertThat(again.id()).contains(id);
        Assertions.assertThat(again.instances()).singleElement()
                .satisfies(row -> Assertions.assertThat(row.serverName()).isEqualTo("hub-1"));
        again.stop();
    }

    @Test
    void serversNotSeenForSevenDaysAreRemoved() {
        final InstanceRepository repository = new InstanceRepository(this.storage);
        repository.write(new InstanceRepository.Instance("00000000-0000-0000-0000-000000000001",
                "old", List.of(), "velocity", "0.9", this.now.get(), this.now.get(), "{}"),
                this.now.get().minus(Duration.ofDays(30))).join();
        this.now.set(this.now.get().plus(Duration.ofDays(8)));

        final InstanceRegistry registry = this.registry();
        registry.start().join();

        Assertions.assertThat(registry.instances()).extracting(InstanceRepository.Instance::id)
                .containsExactly(registry.id().orElseThrow());
        registry.stop();
    }
}
