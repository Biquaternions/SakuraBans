package net.serlith.sakurabans.core.player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.player.AltEntry;

@FunctionalInterface
public interface AltLookup {

    CompletableFuture<List<AltEntry>> alts(UUID uuid, AddressStrictness strictness);

}
