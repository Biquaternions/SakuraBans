package net.serlith.sakurabans.core.placeholder;

import io.github.miniplaceholders.api.MiniPlaceholders;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The placeholder values and the MiniPlaceholders bridge, against the real API jar. */
class PlaceholderValuesTest extends ServiceTest {

    private PlaceholderValues values;

    @BeforeEach
    void values() {
        this.values = new PlaceholderValues(this.mutes, this.counts,
                new Formats(this.messages, this.config), this.messages, this.platform,
                this.clock);
    }

    private void login() {
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.counts.cached(STEVE).isPresent() && this.mutes.loaded(STEVE));
        this.platform.join(STEVE, "Steve", this.home);
    }

    private Punishment mute(final boolean shadow, final Duration duration) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                        PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR).shadow(shadow)
                .reason("spam <red>").duration(duration).build(),
                PunishOptions.builder().build()).join()).punishment();
    }

    private String value(final String name) {
        return this.values.audience(STEVE, name).orElseThrow();
    }

    private static Audience audience(final UUID uuid) {
        final Pointers pointers = Pointers.builder().withStatic(Identity.UUID, uuid).build();
        return new Audience() {
            @Override
            public Pointers pointers() {
                return pointers;
            }
        };
    }

    @Test
    void unknownNamesAreNotPlaceholdersAndUnknownValuesAreEmpty() {
        Assertions.assertThat(this.values.audience(STEVE, "nonsense")).isEmpty();
        Assertions.assertThat(this.values.global("muted")).isEmpty();
        for (final String name : PlaceholderValues.AUDIENCE) {
            Assertions.assertThat(this.value(name)).as(name).isEmpty();
        }
        for (final String name : PlaceholderValues.GLOBAL) {
            Assertions.assertThat(this.values.global(name)).as(name).contains("");
        }
    }

    @Test
    void playersOwnStateAndCounts() {
        this.login();
        Assertions.assertThat(this.value("muted")).isEqualTo("false");
        Assertions.assertThat(this.value("mute_reason")).isEmpty();
        Assertions.assertThat(this.value("player_mutes")).isEqualTo("0");

        this.mute(false, Duration.ofHours(1));

        Assertions.assertThat(this.value("muted")).isEqualTo("true");
        Assertions.assertThat(this.value("mute_reason")).isEqualTo("spam <red>");
        Assertions.assertThat(this.value("mute_remaining")).isNotEmpty();
        Assertions.assertThat(this.value("mute_expires")).isNotEmpty();
        Assertions.assertThat(this.value("player_mutes")).isEqualTo("1");
        Assertions.assertThat(this.value("player_bans")).isEqualTo("0");
        Assertions.assertThat(this.value("player_active_warns")).isEqualTo("0");
    }

    @Test
    void shadowMuteIsNeverRevealed() {
        this.login();
        this.mute(true, Duration.ofHours(1));
        Assertions.assertThat(this.service.cachedMute(STEVE)).as("enforced").isPresent();

        Assertions.assertThat(this.value("muted")).isEqualTo("false");
        Assertions.assertThat(this.value("mute_reason")).isEmpty();
        Assertions.assertThat(this.value("mute_remaining")).isEmpty();
        Assertions.assertThat(this.value("player_mutes")).isEqualTo("0");
    }

    @Test
    void noReadTouchesTheDatabase() {
        this.login();
        this.mute(false, Duration.ofHours(1));
        this.counts.refreshTotals().join();
        // Every query fails from here on.
        this.storage.stop();

        for (final String name : PlaceholderValues.AUDIENCE) {
            Assertions.assertThat(this.values.audience(STEVE, name)).as(name).isPresent();
        }
        Assertions.assertThat(this.value("muted")).isEqualTo("true");
        Assertions.assertThat(this.values.global("active_mutes")).contains("1");
        Assertions.assertThat(this.values.audience(ALEX, "player_bans")).contains("");
        Assertions.assertThat(this.storage.running()).isFalse();
    }

    @Test
    void miniPlaceholdersGetsAudienceAndGlobalTags() {
        this.login();
        this.mute(false, Duration.ofHours(1));
        this.counts.refreshTotals().join();
        final PlaceholderBridges bridges = new PlaceholderBridges(this.log.logger());
        Assertions.assertThat(bridges.start("MiniPlaceholders", true,
                () -> MiniPlaceholdersBridge.register(this.values))).isTrue();
        try {
            Assertions.assertThat(MiniPlaceholders.expansionByName("sakurabans")).isNotNull();
            final MiniMessage mini = MiniMessage.miniMessage();
            final TagResolver tags = TagResolver.resolver(
                    MiniPlaceholders.audiencePlaceholders(),
                    MiniPlaceholders.globalPlaceholders());
            Assertions.assertThat(plain(mini.deserialize("<sakurabans_mute_reason>",
                    audience(STEVE), tags))).as("the reason is text, not tags")
                    .isEqualTo("spam <red>");
            Assertions.assertThat(plain(mini.deserialize("<sakurabans_muted>"
                    + " <sakurabans_player_mutes> <sakurabans_active_mutes>", audience(STEVE),
                    tags))).isEqualTo("true 1 1");
            Assertions.assertThat(plain(mini.deserialize("<sakurabans_total_mutes>", tags)))
                    .as("global, without an audience").isEqualTo("1");
        } finally {
            bridges.stop();
        }
        Assertions.assertThat(MiniPlaceholders.expansionByName("sakurabans"))
                .as("unregistered").isNull();
    }

    @Test
    void bridgeThatDoesntFitIsReportedAndLeftOff() {
        final PlaceholderBridges bridges = new PlaceholderBridges(this.log.logger());

        Assertions.assertThat(bridges.start("MiniPlaceholders", true, () -> {
            throw new NoClassDefFoundError("io/github/miniplaceholders/api/Expansion");
        })).isFalse();
        Assertions.assertThat(bridges.start("PlaceholderAPI", false, () -> {
            throw new AssertionError("not created without the plugin");
        })).isFalse();

        Assertions.assertThat(bridges.count()).isZero();
        Assertions.assertThat(this.log.entries("warn")).anySatisfy(entry ->
                Assertions.assertThat(entry.format())
                        .contains("MiniPlaceholders placeholders are off",
                                "NoClassDefFoundError"));
    }
}
