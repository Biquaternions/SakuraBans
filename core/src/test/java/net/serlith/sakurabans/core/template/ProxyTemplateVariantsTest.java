package net.serlith.sakurabans.core.template;

import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S62 on a proxy: the operator's server picks the variant; the console gets the global one. */
class ProxyTemplateVariantsTest extends ServiceTest {

    @Override
    protected boolean proxy() {
        return true;
    }

    @Override
    protected String configYaml() {
        return
                """
                scopes:
                  categories:
                    games: [skywars, bedwars]
                """;
    }

    @BeforeEach
    void spam() {
        this.templates.create(TemplateVariantsTest.mute("spam", "Global", 1, null), CONSOLE)
                .join();
        this.templates.create(TemplateVariantsTest.mute("spam", "Games", 2,
                CategoryScope.of("games")), CONSOLE).join();
        this.templates.create(TemplateVariantsTest.mute("spam", "Skywars", 3,
                ServerScope.of("skywars")), CONSOLE).join();
    }

    private String reason(final PunishResult result) {
        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        final Punishment punishment = ((PunishResult.Success) result).punishment();
        return punishment.reason();
    }

    private PunishResult mute(final boolean console, final ServerScope scope) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), console ? CONSOLE : MODERATOR).template("spam")
                .scope(scope).build(), PunishOptions.defaults()).join();
    }

    @Test
    void theOperatorsServerPicksTheVariant() {
        this.platform.join(MOD, "Mod", this.home, null, "skywars");
        Assertions.assertThat(this.templates.resolve("spam", null, MODERATOR).orElseThrow()
                .template().reason()).isEqualTo("Skywars");
        this.platform.leave(MOD);
        this.platform.join(MOD, "Mod", this.home, null, "bedwars");
        Assertions.assertThat(this.templates.resolve("spam", null, MODERATOR).orElseThrow()
                .template().reason()).as("its category").isEqualTo("Games");
        this.platform.leave(MOD);
        this.platform.join(MOD, "Mod", this.home, null, "lobby").grant(staffPermissions());
        Assertions.assertThat(this.templates.resolve("spam", null, MODERATOR).orElseThrow()
                .template().reason()).isEqualTo("Global");
        Assertions.assertThat(this.templates.resolve("spam", null, CONSOLE).orElseThrow()
                .template().reason()).as("the proxy console").isEqualTo("Global");
        this.platform.leave(MOD);
        Assertions.assertThat(this.templates.resolve("spam", null, MODERATOR).orElseThrow()
                .template().reason()).as("an operator who isn't online").isEqualTo("Global");
    }

    @Test
    void anExplicitScopeWinsOverTheOperatorsServer() {
        this.platform.join(MOD, "Mod", this.home, null, "lobby").grant(staffPermissions());
        Assertions.assertThat(this.reason(this.mute(false, ServerScope.of("skywars"))))
                .isEqualTo("Skywars");
        Assertions.assertThat(this.reason(this.mute(true, ServerScope.of("bedwars"))))
                .as("the console with --server").isEqualTo("Games");
        Assertions.assertThat(this.templates.resolve("spam", CategoryScope.of("games"),
                CONSOLE).orElseThrow().template().reason()).isEqualTo("Games");
    }
}
