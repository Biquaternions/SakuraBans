package net.serlith.sakurabans.core.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.platform.PlatformType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreBootstrapTest {

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();

    private CoreComponents components() {
        return new CoreComponents(
                new CoreEnvironment(this.dir, this.log.logger(), PlatformType.PAPER),
                InstantSource.system());
    }

    @Test
    void constructingTheComponentsTouchesNothing() {
        final long threadsBefore = sakuraThreads();

        final CoreComponents components = this.components();

        assertThat(this.dir).isEmptyDirectory();
        assertThat(sakuraThreads()).isEqualTo(threadsBefore);
        assertThat(components.lifecycle().state()).isEqualTo(LifecycleState.NEW);
        assertThatThrownBy(() -> components.platform().onlinePlayers())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void phaseOneReadsTheFilesAndPhaseTwoBindsThePlatform() {
        final CoreComponents components = this.components();

        final SakuraBansCore core = CoreBootstrap.create(components);

        assertThat(core.state()).isEqualTo(LifecycleState.CREATED);
        assertThat(this.dir.resolve("config.yml")).isRegularFile();
        assertThat(this.dir.resolve("lang/en_US.yml")).isRegularFile();
        assertThat(components.platform().bound()).isFalse();

        final FakePlatform platform = new FakePlatform();
        core.start(platform);

        assertThat(core.state()).isEqualTo(LifecycleState.RUNNING);
        final UUID uuid = UUID.randomUUID();
        platform.join(uuid, "Sakura", InetAddress.getLoopbackAddress());
        assertThat(components.platform().player(uuid)).isPresent();
        assertThatThrownBy(() -> core.start(platform)).isInstanceOf(IllegalStateException.class);

        core.stop();
    }

    @Test
    void anInvalidConfigurationRefusesPhaseOne() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                "enforcement:\n  address-strictness: NOPE\n");

        assertThatThrownBy(() -> CoreBootstrap.create(this.components()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(this.log.entries("error")).isNotEmpty();
    }

    @Test
    void reloadOnlyWhileRunning() {
        final SakuraBansCore core = CoreBootstrap.create(this.components());

        assertThatThrownBy(core::reload).isInstanceOf(IllegalStateException.class);
        core.start(new FakePlatform());
        assertThat(core.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        core.stop();
        assertThatThrownBy(core::reload).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stopLeavesNoThreadsBehind() throws Exception {
        final long threadsBefore = sakuraThreads();
        final CoreComponents components = this.components();
        final SakuraBansCore core = CoreBootstrap.create(components);
        core.start(new FakePlatform());
        final CountDownLatch ran = new CountDownLatch(2);
        components.executors().repeat(Duration.ZERO, Duration.ofMillis(10), ran::countDown);
        components.executors().worker().execute(ran::countDown);
        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sakuraThreads()).as("the timer thread").isGreaterThan(threadsBefore);

        core.stop();

        assertThat(core.state()).isEqualTo(LifecycleState.STOPPED);
        assertThat(components.executors().terminated()).isTrue();
        assertThat(sakuraThreads()).isEqualTo(threadsBefore);
        core.stop();
        assertThat(this.log.entries("info").stream().map(RecordingLogger.Entry::format)
                .filter(line -> line.contains("disabled"))).hasSize(1);
    }

    @Test
    void stopBeforeStartIsClean() {
        final CoreComponents components = this.components();
        final SakuraBansCore core = CoreBootstrap.create(components);

        core.stop();

        assertThat(core.state()).isEqualTo(LifecycleState.STOPPED);
        assertThat(components.executors().terminated()).isTrue();
        assertThatThrownBy(() -> core.start(new FakePlatform()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void playerHopsAreDroppedOnceThePlayerLeft() {
        final FakePlatform platform = new FakePlatform();
        final UUID uuid = UUID.randomUUID();
        final FakeOnlinePlayer player =
                platform.join(uuid, "Sakura", InetAddress.getLoopbackAddress());

        player.disconnect(Component.text("bye"));
        player.disconnect(Component.text("again"));

        assertThat(player.disconnectReason()).contains(Component.text("bye"));
        assertThat(platform.player(uuid)).isEmpty();
    }

    /** Platform threads only; virtual worker threads are checked through {@code terminated()}. */
    private static long sakuraThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("sakurabans-") && thread.isAlive())
                .count();
    }
}
