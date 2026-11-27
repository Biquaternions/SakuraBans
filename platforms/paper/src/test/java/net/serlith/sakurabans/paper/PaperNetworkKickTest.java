package net.serlith.sakurabans.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.assertj.core.api.Assertions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** S68: the BungeeCord kick request, and the local kick when no proxy takes the player. */
class PaperNetworkKickTest {

    private static final Component SCREEN = Component.text("Banned", NamedTextColor.RED);

    private final List<byte[]> sent = new ArrayList<>();
    private final List<String> channels = new ArrayList<>();
    private final List<Consumer<ScheduledTask>> delayed = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private int kicks;
    private boolean online = true;

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, arguments) -> answer.answer(method.getName(), arguments));
    }

    private interface Answer {
        Object answer(String method, Object[] arguments);
    }

    @SuppressWarnings("unchecked")
    private Player player(final Set<String> listening) {
        final ScheduledTask task = proxy(ScheduledTask.class, (name, ignored) -> {
            throw new UnsupportedOperationException(name);
        });
        final EntityScheduler scheduler = proxy(EntityScheduler.class, (method, arguments) -> {
            switch (method) {
                case "run" -> ((Consumer<ScheduledTask>) arguments[1]).accept(task);
                case "runDelayed" -> {
                    this.delayed.add((Consumer<ScheduledTask>) arguments[1]);
                    this.delays.add((Long) arguments[3]);
                }
                default -> throw new UnsupportedOperationException(method);
            }
            return task;
        });
        return proxy(Player.class, (method, arguments) -> switch (method) {
            case "isOnline" -> this.online;
            case "getScheduler" -> scheduler;
            case "getName" -> "Steve";
            case "getListeningPluginChannels" -> listening;
            case "sendPluginMessage" -> {
                this.channels.add((String) arguments[1]);
                this.sent.add((byte[]) arguments[2]);
                yield null;
            }
            case "kick" -> {
                this.kicks++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private void kick(final Player player) {
        final Plugin plugin = proxy(Plugin.class, (method, arguments) -> {
            throw new UnsupportedOperationException(method);
        });
        new PaperOnlinePlayer(player, new PaperTaskScheduler(plugin))
                .disconnectFromNetwork(SCREEN);
    }

    private static List<String> read(final byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            final List<String> fields = List.of(in.readUTF(), in.readUTF(), in.readUTF());
            Assertions.assertThat(in.available()).isZero();
            return fields;
        }
    }

    @Test
    void asksTheProxyWithBothRequestsAndKicksLocallyIfStillHere() throws IOException {
        this.kick(this.player(Set.of("BungeeCord")));

        Assertions.assertThat(this.channels).containsExactly("BungeeCord", "BungeeCord");
        final List<String> raw = read(this.sent.get(0));
        Assertions.assertThat(raw.subList(0, 2)).containsExactly("KickPlayerRaw", "Steve");
        Assertions.assertThat(GsonComponentSerializer.gson().deserialize(raw.get(2)))
                .isEqualTo(SCREEN);
        Assertions.assertThat(read(this.sent.get(1)))
                .containsExactly("KickPlayer", "Steve", "§cBanned");
        Assertions.assertThat(this.kicks).isZero();
        Assertions.assertThat(this.delays).containsExactly(PaperNetworkKick.FALLBACK_TICKS);

        this.delayed.getFirst().accept(null);
        Assertions.assertThat(this.kicks).as("no proxy answered").isOne();
    }

    @Test
    void noLocalKickOnceTheProxyDisconnectedThem() {
        this.kick(this.player(Set.of("BungeeCord")));
        this.online = false;

        this.delayed.getFirst().accept(null);
        Assertions.assertThat(this.kicks).isZero();
    }

    @Test
    void withoutTheChannelItKicksFromThisServer() {
        this.kick(this.player(Set.of()));

        Assertions.assertThat(this.sent).isEmpty();
        Assertions.assertThat(this.kicks).isOne();
    }
}
