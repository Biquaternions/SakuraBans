package net.serlith.sakurabans.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScopeAndNamesTest {

    @Test
    void namesAreLowerCased() {
        assertThat(ServerScope.of("Lobby-1").server()).isEqualTo("lobby-1");
        assertThat(CategoryScope.of("SURVIVAL_EU").category()).isEqualTo("survival_eu");
        assertThat(ServerScope.of("LOBBY")).isEqualTo(ServerScope.of("lobby"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "lobby 1", "lobby.1", "server:lobby", "*", "lobby/1", "lóbby"})
    void namesRejectCharactersThatBreakPermissionsOrSyntax(final String name) {
        assertThatThrownBy(() -> ServerScope.of(name)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CategoryScope.of(name))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesAreLimitedInLength() {
        assertThat(ServerScope.of("a".repeat(Lengths.MAX_IDENTIFIER)).server()).hasSize(64);
        assertThatThrownBy(() -> ServerScope.of("a".repeat(Lengths.MAX_IDENTIFIER + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorNamesAreCheckedForLength() {
        final UUID uuid = UUID.randomUUID();
        assertThat(PlayerOperator.of(uuid, "a".repeat(Lengths.MAX_PLAYER_NAME)).name()).hasSize(32);
        assertThatThrownBy(() -> PlayerOperator.of(uuid, "a".repeat(Lengths.MAX_PLAYER_NAME + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayerOperator.of(uuid, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CustomOperator.of("Anti-Cheat", null).uuid()).isEmpty();
        assertThatThrownBy(() -> CustomOperator.of("a".repeat(Lengths.MAX_OPERATOR_NAME + 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typesKnowWhetherTheyAreInstantOrStack() {
        assertThat(PunishmentType.KICK.instant()).isTrue();
        assertThat(PunishmentType.BAN.instant()).isFalse();
        assertThat(PunishmentType.WARN.stacks()).isTrue();
        assertThat(PunishmentType.MUTE.stacks()).isFalse();
    }
}
