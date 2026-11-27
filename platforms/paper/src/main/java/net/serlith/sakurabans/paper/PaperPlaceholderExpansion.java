package net.serlith.sakurabans.paper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serlith.sakurabans.core.placeholder.PlaceholderBridges;
import net.serlith.sakurabans.core.placeholder.PlaceholderValues;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

public final class PaperPlaceholderExpansion extends PlaceholderExpansion {

    private final BiFunction<UUID, String, Optional<String>> audience;
    private final Function<String, Optional<String>> global;

    PaperPlaceholderExpansion(final BiFunction<UUID, String, Optional<String>> audience,
            final Function<String, Optional<String>> global) {
        this.audience = audience;
        this.global = global;
    }

    static PlaceholderBridges.Registration register(final PlaceholderValues values) {
        final PaperPlaceholderExpansion expansion =
                new PaperPlaceholderExpansion(values::audience, values::global);
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI refused the expansion");
        }
        return expansion::unregister;
    }

    @Override
    public String getIdentifier() {
        return PlaceholderValues.PREFIX;
    }

    @Override
    public String getAuthor() {
        return "Biquaternions";
    }

    @Override
    public String getVersion() {
        return BuildConstants.VERSION;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return Stream.concat(PlaceholderValues.AUDIENCE.stream(),
                        PlaceholderValues.GLOBAL.stream())
                .map(name -> "%" + PlaceholderValues.PREFIX + "_" + name + "%")
                .toList();
    }

    @Override
    public @Nullable String onRequest(final @Nullable OfflinePlayer player, final String params) {
        if (player == null) {
            return this.global.apply(params).orElse(null);
        }
        return this.audience.apply(player.getUniqueId(), params).orElse(null);
    }
}
