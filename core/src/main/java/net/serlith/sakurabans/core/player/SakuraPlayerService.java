package net.serlith.sakurabans.core.player;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.api.player.PlayerService;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;

public final class SakuraPlayerService implements PlayerService {

    private final PlayerRepository players;

    public SakuraPlayerService(final PlayerRepository players) {
        this.players = players;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfileView>> profile(final UUID uuid) {
        return this.players.profile(uuid);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfileView>> profile(final String name) {
        return this.players.profile(name);
    }

    @Override
    public CompletableFuture<List<NameRecord>> names(final UUID uuid) {
        return this.players.names(uuid);
    }

    @Override
    public CompletableFuture<List<AddressRecord>> addresses(final UUID uuid) {
        return this.players.addresses(uuid);
    }

    @Override
    public CompletableFuture<List<PlayerProfileView>> accountsAt(final InetAddress address) {
        return this.players.accountsAt(address);
    }

    @Override
    public CompletableFuture<List<AltEntry>> alts(final UUID uuid,
            final AddressStrictness strictness) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("alts aren't supported yet (S28)"));
    }

    /** The audit entry for it comes with S37e. */
    @Override
    public CompletableFuture<Boolean> forgetAddress(final UUID uuid, final InetAddress address,
            final Actor actor) {
        return this.players.forgetAddress(uuid, address);
    }
}
