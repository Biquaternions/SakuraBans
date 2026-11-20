package net.serlith.sakurabans.core.scope;

import java.nio.file.Path;
import java.util.Set;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.platform.PlatformType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SakuraScopeServiceTest {

    @TempDir
    Path dir;

    private SakuraScopeService service(final String yaml, final PlatformType type,
            final FakePlatform platform) {
        return new SakuraScopeService(TestConfig.load(this.dir, yaml, new RecordingLogger()),
                platform, type);
    }

    @Test
    void parsesAndFormatsEveryScope() {
        final SakuraScopeService scopes =
                this.service("server:\n  name: lobby\n", PlatformType.PAPER, new FakePlatform());

        Assertions.assertThat(scopes.parse("GLOBAL")).contains(GlobalScope.INSTANCE);
        Assertions.assertThat(scopes.parse("server:Survival"))
                .contains(ServerScope.of("survival"));
        Assertions.assertThat(scopes.parse("category:minigames"))
                .contains(CategoryScope.of("minigames"));
        Assertions.assertThat(scopes.parse("local")).contains(ServerScope.of("lobby"));
        Assertions.assertThat(scopes.parse("server:")).isEmpty();
        Assertions.assertThat(scopes.parse("world:x")).isEmpty();
        Assertions.assertThat(scopes.parse("server:a.b")).isEmpty();
        Assertions.assertThat(scopes.format(CategoryScope.of("pvp"))).isEqualTo("category:pvp");
        Assertions.assertThat(scopes.parse(scopes.format(ServerScope.of("x"))))
                .contains(ServerScope.of("x"));
    }

    @Test
    void theConfiguredNameWinsOverTheDetectedOne() {
        Assertions.assertThat(this.service("server:\n  name: lobby\n", PlatformType.PAPER,
                new FakePlatform("detected", Set.of())).thisServer())
                .contains(ServerScope.of("lobby"));
        Assertions.assertThat(this.service("", PlatformType.PAPER,
                new FakePlatform("Detected", Set.of())).thisServer())
                .contains(ServerScope.of("detected"));
        Assertions.assertThat(this.service("", PlatformType.PAPER, new FakePlatform())
                .thisServer()).isEmpty();
    }

    @Test
    void proxiesHaveNoServerOfTheirOwn() {
        final SakuraScopeService proxy = this.service("server:\n  name: proxy\n",
                PlatformType.VELOCITY, new FakePlatform());

        Assertions.assertThat(proxy.thisServer()).isEmpty();
        Assertions.assertThat(proxy.parse("local")).isEmpty();
        Assertions.assertThat(proxy.localScopes()).containsExactly(GlobalScope.INSTANCE);
    }

    @Test
    void localScopesAreGlobalThisServerAndItsCategories() {
        final SakuraScopeService scopes = this.service(
                "server:\n  name: bedwars-1\n  categories: [minigames, pvp]\n",
                PlatformType.PAPER, new FakePlatform());

        Assertions.assertThat(scopes.localScopes()).containsExactlyInAnyOrder(
                GlobalScope.INSTANCE, ServerScope.of("bedwars-1"),
                CategoryScope.of("minigames"), CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.categories(ServerScope.of("other"))).isEmpty();
        Assertions.assertThat(scopes.covering(CategoryScope.of("pvp")))
                .containsExactlyInAnyOrder(GlobalScope.INSTANCE, CategoryScope.of("pvp"));
        Assertions.assertThat(scopes.appliesHere(ServerScope.of("other"))).isFalse();
    }

    @Test
    void theDefaultScopeFollowsTheConfiguration() {
        Assertions.assertThat(this.service("", PlatformType.PAPER, new FakePlatform())
                .defaultScope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(this.service("server:\n  name: lobby\nscopes:\n  default: local\n",
                        PlatformType.PAPER, new FakePlatform()).defaultScope())
                .isEqualTo(ServerScope.of("lobby"));
        Assertions.assertThat(this.service("scopes:\n  default: local\n",
                PlatformType.VELOCITY, new FakePlatform()).defaultScope())
                .isEqualTo(GlobalScope.INSTANCE);
    }
}
