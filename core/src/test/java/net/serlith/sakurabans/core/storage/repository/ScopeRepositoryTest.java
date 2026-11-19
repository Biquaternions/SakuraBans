package net.serlith.sakurabans.core.storage.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScopeRepositoryTest extends DatabaseTest {

    private ScopeRepository scopes;

    @BeforeEach
    void create() {
        this.scopes = new ScopeRepository(this.storage);
    }

    @Test
    void theGlobalScopeAlreadyExists() {
        Assertions.assertThat(this.scopes.find(GlobalScope.INSTANCE).join()).isPresent();
        Assertions.assertThat(this.scopes.id(GlobalScope.INSTANCE).join())
                .isEqualTo(this.scopes.find(GlobalScope.INSTANCE).join().orElseThrow());
    }

    @Test
    void serverAndCategoryScopesAreCreatedOnce() {
        final Scope lobby = ServerScope.of("lobby");
        final Scope competitive = CategoryScope.of("competitive");

        final int lobbyId = this.scopes.id(lobby).join();
        final int competitiveId = this.scopes.id(competitive).join();

        Assertions.assertThat(this.scopes.id(lobby).join()).isEqualTo(lobbyId);
        Assertions.assertThat(this.scopes.byId(competitiveId).join()).contains(competitive);
        final Map<Integer, Scope> all = this.scopes.all().join();
        Assertions.assertThat(all).containsEntry(lobbyId, lobby).containsEntry(competitiveId,
                competitive)
                .containsValue(GlobalScope.INSTANCE).hasSize(3);
        Assertions.assertThat(this.scopes.find(ServerScope.of("survival")).join()).isEmpty();
    }

    @Test
    void racingCreationsShareOneRow() {
        final Scope scope = ServerScope.of("race");

        final List<CompletableFuture<Integer>> racing = IntStream.range(0, 16)
                .mapToObj(ignored -> this.scopes.id(scope)).toList();

        Assertions.assertThat(Set.copyOf(racing.stream().map(CompletableFuture::join).toList()))
                .hasSize(1);
        Assertions.assertThat(this.count(SakuraTables.SCOPES)).isEqualTo(2);
    }
}
