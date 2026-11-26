package net.serlith.sakurabans.core.scope;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.fake.FakePlatform;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SakuraScopeServiceTest {

    private static final String NETWORK = "scopes:\n  categories:\n"
            + "    minigames: [bedwars-1, Bedwars-2]\n    pvp: [bedwars-1, kitpvp]\n";

    @TempDir
    Path dir;

    private final RecordingLogger log = new RecordingLogger();

    private SakuraScopeService service(final String yaml, final boolean proxy,
            final FakePlatform platform) {
        final ConfigService config = TestConfig.load(this.dir, yaml, this.log);
        return new SakuraScopeService(config, platform, proxy, this.log.logger());
    }

    private SakuraScopeService paper(final String yaml) {
        return this.service(yaml, false, new FakePlatform());
    }

    @Test
    void parsesAndFormatsEveryScope() {
        final SakuraScopeService scopes = this.paper("server:\n  name: lobby\n");

        Assertions.assertThat(scopes.parse("GLOBAL")).contains(GlobalScope.INSTANCE);
        Assertions.assertThat(scopes.parse("server:Survival"))
                .contains(ServerScope.of("survival"));
        Assertions.assertThat(scopes.parse("category:minigames"))
                .contains(CategoryScope.of("minigames"));
        Assertions.assertThat(scopes.parse("local")).contains(ServerScope.of("lobby"));
        Assertions.assertThat(scopes.parse("server:")).isEmpty();
        Assertions.assertThat(scopes.parse("world:x")).isEmpty();
        Assertions.assertThat(scopes.parse("server:a.b")).isEmpty();
        Assertions.assertThat(scopes.parse("all")).isEmpty();
        Assertions.assertThat(scopes.format(CategoryScope.of("pvp"))).isEqualTo("category:pvp");
        Assertions.assertThat(scopes.parse(scopes.format(ServerScope.of("x"))))
                .contains(ServerScope.of("x"));
    }

    @Test
    void anEmptyNameMeansNoName() {
        Assertions.assertThat(this.paper("server:\n  name: Lobby\n").thisServer())
                .contains(ServerScope.of("lobby"));
        Assertions.assertThat(this.paper("").thisServer()).isEmpty();
        Assertions.assertThat(this.paper("server:\n  name: ''\n").thisServer()).isEmpty();
        Assertions.assertThat(this.paper("server:\n  name: '  '\n").thisServer()).isEmpty();
        Assertions.assertThat(this.paper("server:\n  name:\n").thisServer()).isEmpty();
    }

    @Test
    void autoIsRejectedAsAnError() {
        Assertions.assertThatThrownBy(() -> this.paper("server:\n  name: auto\n"))
                .hasMessageContaining("server.name")
                .hasMessageContaining("\"auto\" was removed");
    }

    @Test
    void proxiesHaveNoServerOfTheirOwn() {
        final SakuraScopeService proxy = this.service("server:\n  name: proxy\n",
                true, new FakePlatform());

        Assertions.assertThat(proxy.thisServer()).isEmpty();
        Assertions.assertThat(proxy.parse("local")).isEmpty();
        Assertions.assertThat(proxy.localScopes()).containsExactly(GlobalScope.INSTANCE);
    }

    @Test
    void localScopesAreGlobalThisServerAndItsCategories() {
        final SakuraScopeService scopes = this.paper(
                "server:\n  name: bedwars-1\n  categories: [minigames, pvp]\n");

        Assertions.assertThat(scopes.localScopes()).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, ServerScope.of("bedwars-1"),
                CategoryScope.of("minigames"), CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.categories(ServerScope.of("other"))).isEmpty();
        Assertions.assertThat(scopes.covering(CategoryScope.of("pvp")))
                .containsExactlyInAnyOrder(GlobalScope.INSTANCE, CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.appliesHere(ServerScope.of("other"))).isFalse();
    }

    @Test
    void ownCategoriesApplyWithoutName() {
        final SakuraScopeService scopes = this.paper("server:\n  categories: [minigames]\n");

        Assertions.assertThat(scopes.localScopes()).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, CategoryScope.of("minigames"));
    }

    @Test
    void theCategoryMapNamesEveryServersCategories() {
        final SakuraScopeService scopes = this.paper("server:\n  name: kitpvp\n" + NETWORK);

        Assertions.assertThat(scopes.categories(ServerScope.of("bedwars-1")))
                .containsExactlyInAnyOrder(CategoryScope.of("minigames"), CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.categories(ServerScope.of("bedwars-2")))
                .containsExactly(CategoryScope.of("minigames"));
        Assertions.assertThat(scopes.localScopes()).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, ServerScope.of("kitpvp"), CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.covering(ServerScope.of("bedwars-2")))
                .containsExactlyInAnyOrder(GlobalScope.INSTANCE, ServerScope.of("bedwars-2"),
                        CategoryScope.of("minigames"));
    }

    @Test
    void theCategoryMapAddsToServerCategories() {
        final SakuraScopeService scopes = this.paper(
                "server:\n  name: kitpvp\n  categories: [arena]\n" + NETWORK);

        Assertions.assertThat(scopes.categories(ServerScope.of("kitpvp")))
                .containsExactlyInAnyOrder(CategoryScope.of("arena"), CategoryScope.of("pvp"));
    }

    @Test
    void proxiesKnowTheirBackendsCategories() {
        final SakuraScopeService proxy = this.service(NETWORK, true,
                new FakePlatform(Set.of("bedwars-1", "lobby"), List.of("lobby")));

        Assertions.assertThat(proxy.scopesOn("Bedwars-1")).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, ServerScope.of("bedwars-1"),
                CategoryScope.of("minigames"), CategoryScope.of("pvp"));
        Assertions.assertThat(proxy.scopesOn("lobby")).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, ServerScope.of("lobby"));
        Assertions.assertThat(proxy.scopesOn(null)).containsExactly(GlobalScope.INSTANCE);
    }

    @Test
    void invalidCategoryEntriesAreConfigErrors() {
        Assertions.assertThatThrownBy(() -> this.paper(
                        "scopes:\n  categories:\n    'bad name': [lobby]\n    ok: ['a.b']\n"))
                .hasMessageContaining("scopes.categories.bad name")
                .hasMessageContaining("scopes.categories.ok");
    }

    @Test
    void theDefaultScopeFollowsTheConfiguration() {
        Assertions.assertThat(this.paper("").defaultScope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(this.paper("server:\n  name: lobby\nscopes:\n  default: local\n")
                .defaultScope()).isEqualTo(ServerScope.of("lobby"));
        Assertions.assertThat(this.paper("scopes:\n  default: category:pvp\n").defaultScope())
                .isEqualTo(CategoryScope.of("pvp"));
    }

    @Test
    void localIsGlobalWithoutName() {
        final SakuraScopeService scopes = this.paper("scopes:\n  default: local\n");
        final int before = this.log.entries("warn").size();

        Assertions.assertThat(scopes.defaultScope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(this.log.entries("warn")).hasSize(before);
    }

    @Test
    void missingNameIsReportedOnlyWhenScopesAreInUse() {
        this.assertNameWarnings(this.paper("scopes:\n  default: local\n"), 1);
        this.assertNameWarnings(this.paper(NETWORK), 1);
        this.assertNameWarnings(this.paper(""), 0);
        this.assertNameWarnings(this.paper("scopes:\n  default: category:pvp\n"), 0);
        this.assertNameWarnings(this.paper("server:\n  name: lobby\n"
                + "scopes:\n  default: local\n" + NETWORK), 0);
        this.assertNameWarnings(this.service("scopes:\n  default: local\n" + NETWORK, true,
                new FakePlatform()), 0);
    }

    private void assertNameWarnings(final SakuraScopeService scopes, final int expected) {
        final int before = this.log.entries("warn").size();
        scopes.checkServerName();
        Assertions.assertThat(this.log.entries("warn")).hasSize(before + expected);
    }

    @Test
    void localOnProxiesIsTheIssuersServer() throws Exception {
        final FakePlatform platform = new FakePlatform(Set.of("survival"), List.of());
        final UUID staff = UUID.randomUUID();
        platform.join(staff, "Mod", InetAddress.getLoopbackAddress(), null, "Survival");
        final SakuraScopeService proxy =
                this.service("scopes:\n  default: local\n", true, platform);

        Assertions.assertThat(proxy.defaultScope(staff)).isEqualTo(ServerScope.of("survival"));
        Assertions.assertThat(proxy.defaultScope(UUID.randomUUID()))
                .isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(proxy.defaultScope()).isEqualTo(GlobalScope.INSTANCE);
    }

    @Test
    void theOriginIsThisServerOrTheProxy() {
        Assertions.assertThat(this.paper("server:\n  name: lobby\n").originServer())
                .contains("lobby");
        Assertions.assertThat(this.paper("").originServer()).isEmpty();
        Assertions.assertThat(this.service("", true, new FakePlatform())
                .originServer()).contains("proxy");
        Assertions.assertThat(this.service("server:\n  name: edge-1\n", true,
                new FakePlatform()).originServer()).contains("edge-1");
    }

    @Test
    void knownServersAndCategoriesFeedSuggestions() {
        final SakuraScopeService proxy = this.service(NETWORK, true,
                new FakePlatform(Set.of("Lobby", "kitpvp"), List.of()));

        Assertions.assertThat(proxy.knownServers())
                .containsExactly("bedwars-1", "bedwars-2", "kitpvp", "lobby");
        Assertions.assertThat(proxy.knownCategories()).containsExactly("minigames", "pvp");
    }
}
