package net.serlith.sakurabans.core.command.module;

import java.util.List;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Scope rules of the commands, with {@code scopes.require-permissions} on. */
class ScopeCommandsTest extends CommandTest {

    private static final String BAN = Permissions.punish(PunishmentType.BAN);
    private static final String UNBAN = Permissions.revoke(PunishmentType.BAN);

    @Override
    protected String configYaml() {
        return "scopes:\n  require-permissions: true\n  categories:\n    pvp: [kitpvp]\n";
    }

    @BeforeEach
    void steve() {
        this.known(STEVE, "Steve");
    }

    private List<Punishment> active() {
        return this.service.query(PunishmentQuery.builder().types(PunishmentType.BAN)
                .activeOnly(true).build()).join().items();
    }

    @Test
    void punishingWithoutScopeNeedsTheDefaultPermission() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", BAN);
        this.run(mod, "ban Steve -r griefing");
        Assertions.assertThat(mod.last()).contains("Name a scope");
        Assertions.assertThat(this.active()).isEmpty();

        final FakeCommandActor trusted =
                FakeCommandActor.player(MOD, "Mod", BAN, Permissions.SCOPE_DEFAULT);
        this.run(trusted, "ban Steve -r griefing");
        Assertions.assertThat(this.active()).singleElement()
                .extracting(Punishment::scope).isEqualTo(GlobalScope.INSTANCE);
    }

    @Test
    void punishingInScopeNeedsItsPermission() {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", BAN,
                Permissions.scopeServer("survival"));

        this.run(mod, "ban Steve --server creative -r griefing");
        Assertions.assertThat(mod.last()).contains("can't punish in server:creative");
        this.run(mod, "ban Steve -g -r griefing");
        Assertions.assertThat(mod.last()).contains("can't punish in global");

        this.run(mod, "ban Steve --server survival -r griefing");
        Assertions.assertThat(this.active()).singleElement()
                .extracting(Punishment::scope).isEqualTo(ServerScope.of("survival"));
    }

    @Test
    void revocationsLeaveScopesTheSenderCantUse() {
        this.run(this.console, "ban Steve -g -r one");
        this.run(this.console, "ban Steve --server survival -r two");
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", UNBAN,
                Permissions.scopeServer("survival"));

        this.run(mod, "unban Steve");

        Assertions.assertThat(mod.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("Unbanned Steve (1)"));
        Assertions.assertThat(mod.last()).contains("Not lifted in global");
        Assertions.assertThat(this.active()).singleElement()
                .extracting(Punishment::scope).isEqualTo(GlobalScope.INSTANCE);

        this.run(mod, "unban Steve");
        Assertions.assertThat(mod.last()).contains("can't lift punishments in global");
        this.run(mod, "unban Steve --scope global");
        Assertions.assertThat(mod.last()).contains("can't lift punishments in global");
        Assertions.assertThat(this.active()).hasSize(1);
    }

    @Test
    void scopeAllIsForRevocationsOnly() {
        this.run(this.console, "ban Steve -g -r one");
        this.run(this.console, "ban Steve --server survival -r two");

        this.run(this.console, "ban Steve --scope all -r three");
        Assertions.assertThat(this.console.last()).contains("only when lifting");

        this.run(this.console, "unban Steve --scope server:survival");
        Assertions.assertThat(this.active()).singleElement()
                .extracting(Punishment::scope).isEqualTo(GlobalScope.INSTANCE);
        this.run(this.console, "ban Steve --server survival -r two");

        this.run(this.console, "unban Steve --scope all");
        Assertions.assertThat(this.active()).isEmpty();
        Assertions.assertThat(this.console.last()).contains("Unbanned Steve (2)");
    }

    @Test
    void scopeFlagsSuggestKnownServersAndCategories() {
        Assertions.assertThat(this.suggest("ban Steve -d 7d --server "))
                .contains("kitpvp");
        Assertions.assertThat(this.suggest("ban Steve --category "))
                .contains("pvp");
        Assertions.assertThat(this.suggest("ban Steve -s --scope "))
                .contains("global", "local", "server:lobby", "server:kitpvp", "category:pvp")
                .doesNotContain("all");
        Assertions.assertThat(this.suggest("unban Steve --scope "))
                .contains("all", "server:kitpvp");
    }

    private List<String> suggest(final String input) {
        return this.manager.suggestionFactory().suggestImmediately(this.console, input).list()
                .stream().map(Suggestion::suggestion).toList();
    }
}
