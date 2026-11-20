package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LoginTimeoutTest extends ServiceTest {

    private static final int POOL_SIZE = 10;

    @Override
    protected String configYaml() {
        return "enforcement:\n  login-check-timeout: 1s\n";
    }

    @Test
    void slowChecksLetThePlayerInWithinTheTimeout() throws Exception {
        // Hold every pooled connection, so the ban query has to wait.
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch held = new CountDownLatch(POOL_SIZE);
        final List<CompletableFuture<Object>> holders = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            holders.add(this.storage.write(configuration -> {
                held.countDown();
                release.await();
                return null;
            }));
        }
        Assertions.assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

        final long start = System.nanoTime();
        final LoginDecision decision = this.loginGate.check(new LoginAttempt(STEVE, "Steve",
                InetAddress.getByName("203.0.113.7"), null)).get(5, TimeUnit.SECONDS);
        final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();
        holders.forEach(CompletableFuture::join);

        Assertions.assertThat(decision).isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(millis).isBetween(900L, 4_000L);
        Assertions.assertThat(this.log.entries("warn"))
                .anySatisfy(entry -> Assertions.assertThat(entry.format()).contains("longer"));
    }
}
