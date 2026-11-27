package net.serlith.sakurabans.core.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.SettingsLayers;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Protocol 5.1: settings resolve server, categories, global, file, default. */
class ScopedSettingsTest extends ServiceTest {

    private static final Scope LOBBY = ServerScope.of("lobby");
    private static final Scope HUBS = CategoryScope.of("hubs");
    private static final Scope MINIGAMES = CategoryScope.of("minigames");

    @Override
    protected String baseConfigYaml() {
        return "";
    }

    @Override
    protected String configYaml() {
        return "server:\n  name: lobby\n  categories: [own]\nreasons:\n  default: From the"
                + " file\n";
    }

    private void set(final Scope scope, final String key, final Object value) {
        Assertions.assertThat(this.settingsService.change(scope, Map.of(key, value), Set.of(),
                this.settingsService.version(scope), CONSOLE).join())
                .as("%s %s", scope, key).isInstanceOf(AdminResult.Ok.class);
    }

    private void reset(final Scope scope, final String key) {
        Assertions.assertThat(this.settingsService.change(scope, Map.of(), Set.of(key),
                this.settingsService.version(scope), CONSOLE).join())
                .isInstanceOf(AdminResult.Ok.class);
    }

    private String reason() {
        return this.config.settings().reasons().defaultReason();
    }

    @Test
    void eachLayerWinsOverTheOnesBelow() {
        Assertions.assertThat(this.reason()).isEqualTo("From the file");
        this.set(GlobalScope.INSTANCE, "reasons.default", "Global");
        Assertions.assertThat(this.reason()).isEqualTo("Global");

        // Categories are global-only: membership comes from the global layer.
        this.set(GlobalScope.INSTANCE, "scopes.categories",
                Map.of("minigames", List.of("lobby"), "hubs", List.of("lobby")));
        this.set(MINIGAMES, "reasons.default", "Minigames");
        this.set(HUBS, "reasons.default", "Hubs");
        final List<?> declared = List.copyOf(((Map<?, ?>) this.settingsService
                .plainValue("scopes.categories").orElseThrow()).keySet());
        // The category declared first in scopes.categories wins.
        Assertions.assertThat(this.reason()).isEqualTo(
                declared.getFirst().equals("hubs") ? "Hubs" : "Minigames");
        Assertions.assertThat(this.settingsService.layers()).containsSubsequence(LOBBY,
                CategoryScope.of(String.valueOf(declared.get(0))),
                CategoryScope.of(String.valueOf(declared.get(1))), CategoryScope.of("own"),
                GlobalScope.INSTANCE);

        this.set(LOBBY, "reasons.default", "Lobby");
        Assertions.assertThat(this.reason()).isEqualTo("Lobby");
        Assertions.assertThat(this.settingsService.source("reasons.default")).contains(LOBBY);

        // A reset lets the next layer through; another server's override never applies.
        this.reset(LOBBY, "reasons.default");
        this.set(ServerScope.of("skywars"), "reasons.default", "Skywars");
        Assertions.assertThat(this.reason()).isNotEqualTo("Skywars").isNotEqualTo("Lobby");
        Assertions.assertThat(this.settingsService.version(LOBBY)).isEqualTo(2);
        Assertions.assertThat(this.settingsService.version(ServerScope.of("nobody"))).isZero();
    }

    @Test
    void ownCategoriesComeAfterTheDeclaredOnes() {
        this.set(CategoryScope.of("own"), "reasons.default", "Own");
        Assertions.assertThat(this.reason()).isEqualTo("Own");
        this.set(GlobalScope.INSTANCE, "scopes.categories",
                Map.of("arena", List.of("lobby")));
        this.set(CategoryScope.of("arena"), "reasons.default", "Arena");
        Assertions.assertThat(this.reason()).isEqualTo("Arena");

        Assertions.assertThat(SettingsLayers.scopes("", List.of("own"), Map.of("arena",
                List.of("")))).as("an unnamed server takes part only through its own")
                .containsExactly(CategoryScope.of("own"), GlobalScope.INSTANCE);
        Assertions.assertThat(SettingsLayers.scopes("lobby", List.of("b", "a"),
                Map.of("a", List.of("lobby")))).containsExactly(LOBBY, CategoryScope.of("a"),
                CategoryScope.of("b"), GlobalScope.INSTANCE);
    }

    @Test
    void globalOnlyKeysAreRefusedInOtherScopes() {
        final AdminResult<?> refused = this.settingsService.change(LOBBY,
                Map.of("enforcement.address-strictness", "STRICT"), Set.of(), 0, CONSOLE).join();

        Assertions.assertThat(refused).isInstanceOfSatisfying(AdminResult.ValidationFailed.class,
                failed -> Assertions.assertThat(failed.errors()).extracting(
                        error -> ((FieldError) error).path()).containsExactly(
                                "enforcement.address-strictness"));
        Assertions.assertThat(this.settingsService.change(MINIGAMES,
                Map.of("server.name", "x"), Set.of(), 0, CONSOLE).join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
    }

    @Test
    void anInvalidScopedValueKeepsTheLastValidSettingsAndNamesItsScope() {
        this.set(GlobalScope.INSTANCE, "reasons.default", "Global");
        // Written by another instance that didn't see this server's own checks.
        new OverrideRepository(this.storage).changeSettings(LOBBY,
                Map.of("display.timezone", this.config.encode("Mars/Olympus")), 0, CONSOLE,
                this.now.get()).join();

        this.settingsService.reload().join();

        Assertions.assertThat(this.reason()).isEqualTo("Global");
        Assertions.assertThat(this.log.entries("error")).extracting(entry -> entry.format())
                .anySatisfy(line -> Assertions.assertThat(line).contains("display.timezone",
                        "(from server:lobby)"));
    }

    @Test
    void otherServersResolveAsTheyWouldAndPublishTakesTheirFiles() {
        this.set(GlobalScope.INSTANCE, "scopes.categories",
                Map.of("minigames", List.of("skywars")));
        this.set(MINIGAMES, "reasons.default", "Minigames");
        this.set(GlobalScope.INSTANCE, "alts.join-notification.threshold", 5);
        final InstanceRepository.Instance skywars = new InstanceRepository.Instance("id-1",
                "skywars", List.of(), "paper", "1", Instant.EPOCH, Instant.EPOCH,
                "{\"reasons.default\":\"Skywars file\",\"alts.join-notification.threshold\":2,"
                        + "\"display.timezone\":\"UTC\"}");

        final Map<String, SakuraSettingsService.Effective> effective =
                this.settingsService.effectiveOn(skywars);
        Assertions.assertThat(effective.get("reasons.default"))
                .isEqualTo(new SakuraSettingsService.Effective("Minigames", MINIGAMES));
        Assertions.assertThat(effective.get("display.timezone"))
                .isEqualTo(new SakuraSettingsService.Effective("UTC", null));

        Assertions.assertThat(this.settingsService.publishable(skywars, Set.of()))
                .containsOnlyKeys("reasons.default", "alts.join-notification.threshold",
                        "display.timezone");
        Assertions.assertThat(this.settingsService.publish(skywars,
                Set.of("alts.join-notification.threshold"), this.settingsService.version(),
                CONSOLE).join()).isInstanceOf(AdminResult.Ok.class);
        Assertions.assertThat(this.config.settings().alts().joinNotification().threshold())
                .isEqualTo(2);
    }
}
