package net.serlith.sakurabans.paper;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

/** S73: the PlaceholderAPI expansion passes its requests to the core's cached values. */
class PaperPlaceholderExpansionTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final PaperPlaceholderExpansion expansion = new PaperPlaceholderExpansion(
            (uuid, name) -> uuid.equals(STEVE)
                    ? Optional.ofNullable(Map.of("muted", "true", "player_bans", "2",
                            "total_bans", "7").get(name))
                    : Optional.of(""),
            name -> name.equals("total_bans") ? Optional.of("7") : Optional.empty());

    private static OfflinePlayer player(final UUID uuid) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return uuid;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void describesItself() {
        Assertions.assertThat(this.expansion.getIdentifier()).isEqualTo("sakurabans");
        Assertions.assertThat(this.expansion.persist()).isTrue();
        Assertions.assertThat(this.expansion.canRegister()).isTrue();
        Assertions.assertThat(this.expansion.getPlaceholders())
                .contains("%sakurabans_muted%", "%sakurabans_player_bans%",
                        "%sakurabans_total_bans%", "%sakurabans_active_warns%");
    }

    @Test
    void answersForPlayersAndWithoutOne() {
        Assertions.assertThat(this.expansion.onRequest(player(STEVE), "muted")).isEqualTo("true");
        Assertions.assertThat(this.expansion.onRequest(player(STEVE), "player_bans"))
                .isEqualTo("2");
        Assertions.assertThat(this.expansion.onRequest(player(UUID.randomUUID()), "muted"))
                .as("not known yet").isEmpty();
        Assertions.assertThat(this.expansion.onRequest(null, "total_bans")).isEqualTo("7");
        Assertions.assertThat(this.expansion.onRequest(null, "muted"))
                .as("needs a player").isNull();
        Assertions.assertThat(this.expansion.onRequest(player(STEVE), "nonsense"))
                .as("unknown to PlaceholderAPI").isNull();
    }
}
