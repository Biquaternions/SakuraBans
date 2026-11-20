package net.serlith.sakurabans.core.player;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.serlith.sakurabans.api.integration.ProfileSource;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.util.Uuids;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UuidResolverTest {

    private static final UUID FROM_PLATFORM = UUID.randomUUID();

    @TempDir
    Path dir;

    private final RecordingLogger log = new RecordingLogger();
    private final SakuraIntegrationRegistry integrations = new SakuraIntegrationRegistry();
    private final AtomicInteger platformCalls = new AtomicInteger();
    private final FakePlatform platform = new FakePlatform().profiles(name -> {
        this.platformCalls.incrementAndGet();
        return CompletableFuture.completedFuture(
                name.equals("Known") ? Optional.of(FROM_PLATFORM) : Optional.empty());
    });

    private UuidResolver resolver(final String yaml) {
        return new UuidResolver(this.integrations, this.platform,
                TestConfig.load(this.dir, yaml, this.log), this.log.logger());
    }

    private UuidResolver resolver() {
        return this.resolver("");
    }

    private static ProfileSource source(final String name,
            final CompletableFuture<Optional<UUID>> answer, final List<String> asked) {
        return new ProfileSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public CompletableFuture<Optional<UUID>> uuid(final String player) {
                asked.add(name);
                return answer;
            }

            @Override
            public CompletableFuture<Optional<String>> currentName(final UUID uuid) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<Optional<InetAddress>> lastAddress(final UUID uuid) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    @Test
    void asksSourcesInOrderBeforeThePlatform() {
        final List<String> asked = new CopyOnWriteArrayList<>();
        final UUID fromSource = UUID.randomUUID();
        this.integrations.register(source("empty",
                CompletableFuture.completedFuture(Optional.empty()), asked));
        this.integrations.register(source("hydra",
                CompletableFuture.completedFuture(Optional.of(fromSource)), asked));

        Assertions.assertThat(this.resolver().resolve("Known").join()).contains(fromSource);
        Assertions.assertThat(asked).containsExactly("empty", "hydra");
        Assertions.assertThat(this.platformCalls).hasValue(0);
    }

    @Test
    void failingSourcesAreSkipped() {
        final List<String> asked = new CopyOnWriteArrayList<>();
        this.integrations.register(source("broken",
                CompletableFuture.failedFuture(new IllegalStateException("down")), asked));

        Assertions.assertThat(this.resolver().resolve("Known").join()).contains(FROM_PLATFORM);
        Assertions.assertThat(this.log.entries("warn")).hasSize(1);
    }

    @Test
    void answersAreCachedIncludingMisses() {
        final UuidResolver resolver = this.resolver();
        Assertions.assertThat(resolver.resolve("Known").join()).contains(FROM_PLATFORM);
        Assertions.assertThat(resolver.resolve("known").join()).contains(FROM_PLATFORM);
        Assertions.assertThat(resolver.resolve("Nobody").join()).isEmpty();
        Assertions.assertThat(resolver.resolve("Nobody").join()).isEmpty();

        Assertions.assertThat(this.platformCalls).hasValue(2);
    }

    @Test
    void invalidNamesAreNeverLookedUp() {
        Assertions.assertThat(this.resolver().resolve("no spaces").join()).isEmpty();
        Assertions.assertThat(this.resolver().resolve("x".repeat(17)).join()).isEmpty();
        Assertions.assertThat(this.platformCalls).hasValue(0);
    }

    @Test
    void offlineServersUseOfflineUuids() {
        this.platform.onlineMode(false);

        Assertions.assertThat(this.resolver().resolve("Steve").join())
                .contains(Uuids.offline("Steve"));
        Assertions.assertThat(this.platformCalls).hasValue(0);
    }

    @Test
    void theRemoteLookupCanBeTurnedOff() {
        final List<String> asked = new CopyOnWriteArrayList<>();
        this.integrations.register(source("hydra",
                CompletableFuture.completedFuture(Optional.empty()), asked));
        final UuidResolver resolver = this.resolver("players:\n  remote-lookup: false\n");

        Assertions.assertThat(resolver.resolve("Known").join()).isEmpty();
        Assertions.assertThat(asked).containsExactly("hydra");
        Assertions.assertThat(this.platformCalls).hasValue(0);
    }

    @Test
    void failingLookupsMeanUnknown() {
        this.platform.profiles(name -> CompletableFuture.failedFuture(
                new IllegalStateException("429 Too Many Requests")));

        Assertions.assertThat(this.resolver().resolve("Known").join()).isEmpty();
        Assertions.assertThat(this.log.entries("warn")).hasSize(1);
    }
}
