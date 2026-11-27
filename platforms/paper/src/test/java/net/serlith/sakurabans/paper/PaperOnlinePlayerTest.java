package net.serlith.sakurabans.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.assertj.core.api.Assertions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * S64: {@code showNotice} says whether the dialog was handed to the player's thread. The dialog
 * itself needs a running server (its registry), so the scheduled task is never run here.
 */
class PaperOnlinePlayerTest {

    private static final Component TEXT = Component.text("Muted");

    private final List<Object> scheduled = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, arguments) -> answer.answer(method.getName(), arguments));
    }

    private interface Answer {
        Object answer(String method, Object[] arguments);
    }

    /** {@code retired}: the entity scheduler refuses the task, as for a player who left. */
    private Player player(final boolean online, final boolean retired) {
        final EntityScheduler scheduler = proxy(EntityScheduler.class, (method, arguments) -> {
            if (!method.equals("run")) {
                throw new UnsupportedOperationException(method);
            }
            this.scheduled.add(arguments[1]);
            return retired ? null : proxy(ScheduledTask.class, (name, ignored) -> {
                throw new UnsupportedOperationException(name);
            });
        });
        return proxy(Player.class, (method, arguments) -> switch (method) {
            case "isOnline" -> online;
            case "getScheduler" -> scheduler;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private boolean show(final Player player) {
        final Plugin plugin = proxy(Plugin.class, (method, arguments) -> {
            throw new UnsupportedOperationException(method);
        });
        return new PaperOnlinePlayer(player, new PaperTaskScheduler(plugin))
                .showNotice(TEXT, TEXT, TEXT);
    }

    @Test
    void scheduledDialogIsShown() {
        Assertions.assertThat(this.show(this.player(true, false))).isTrue();
        Assertions.assertThat(this.scheduled).hasSize(1);
    }

    @Test
    void retiredSchedulerMeansChat() {
        Assertions.assertThat(this.show(this.player(true, true))).isFalse();
    }

    @Test
    void anOfflinePlayerMeansChatAndNothingIsScheduled() {
        Assertions.assertThat(this.show(this.player(false, false))).isFalse();
        Assertions.assertThat(this.scheduled).isEmpty();
    }
}
