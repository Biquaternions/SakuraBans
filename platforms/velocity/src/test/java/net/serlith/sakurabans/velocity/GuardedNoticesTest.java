package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Whatever the packetevents bridge throws, the core gets {@code false} and uses chat. */
class GuardedNoticesTest {

    private static final Component TEXT = Component.text("Muted");

    private final List<Throwable> reported = new ArrayList<>();

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private boolean show(final VelocityNotices notices) {
        return notices.show(player(), TEXT, TEXT, TEXT);
    }

    @Test
    void bridgeThatCantBeLoadedMeansChatAndIsReportedOnce() {
        final AtomicInteger created = new AtomicInteger();
        final GuardedNotices notices = new GuardedNotices(() -> {
            created.incrementAndGet();
            throw new NoClassDefFoundError(
                    "com/github/retrooper/packetevents/protocol/dialog/NoticeDialog");
        }, this.reported::add);

        Assertions.assertThat(this.show(notices)).isFalse();
        Assertions.assertThat(this.show(notices)).isFalse();
        Assertions.assertThat(this.show(notices)).isFalse();

        Assertions.assertThat(this.reported).singleElement()
                .isInstanceOf(NoClassDefFoundError.class);
        Assertions.assertThat(created).as("not tried again after a linkage error").hasValue(1);
    }

    @Test
    void linkageErrorWhileShowingTurnsDialogsOff() {
        final AtomicInteger shown = new AtomicInteger();
        final GuardedNotices notices = new GuardedNotices(() -> (player, title, body, button) -> {
            shown.incrementAndGet();
            throw new NoSuchMethodError("WrapperPlayServerShowDialog.<init>");
        }, this.reported::add);

        Assertions.assertThat(this.show(notices)).isFalse();
        Assertions.assertThat(this.show(notices)).isFalse();

        Assertions.assertThat(shown).hasValue(1);
        Assertions.assertThat(this.reported).hasSize(1);
    }

    @Test
    void runtimeFailureMeansChatForThatNoticeAndIsReportedOnce() {
        final AtomicInteger calls = new AtomicInteger();
        final GuardedNotices notices = new GuardedNotices(() -> (player, title, body, button) -> {
            if (calls.incrementAndGet() <= 2) {
                throw new IllegalStateException("not in the play phase");
            }
            return true;
        }, this.reported::add);

        Assertions.assertThat(this.show(notices)).isFalse();
        Assertions.assertThat(this.show(notices)).isFalse();
        Assertions.assertThat(this.show(notices)).as("tried again: it worked").isTrue();

        Assertions.assertThat(this.reported).singleElement()
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void workingBridgeIsCreatedOnceAndAnswers() {
        final AtomicInteger created = new AtomicInteger();
        final GuardedNotices notices = new GuardedNotices(() -> {
            created.incrementAndGet();
            return (player, title, body, button) -> true;
        }, this.reported::add);

        Assertions.assertThat(this.show(notices)).isTrue();
        Assertions.assertThat(this.show(notices)).isTrue();

        Assertions.assertThat(created).hasValue(1);
        Assertions.assertThat(this.reported).isEmpty();
    }

    @Test
    void withoutPacketEventsNothingIsShown() {
        Assertions.assertThat(this.show(VelocityNotices.NONE)).isFalse();
    }
}
