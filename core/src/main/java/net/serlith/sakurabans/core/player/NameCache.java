package net.serlith.sakurabans.core.player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.core.util.Strings;

public final class NameCache {

    private static final int MAXIMUM_SIZE = 20_000;

    private final Cache<UUID, String> names = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_SIZE)
            .build();
    private final Cache<String, UUID> uuids = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_SIZE)
            .build();

    public void put(final UUID uuid, final String name) {
        Preconditions.checkNotNull(uuid, "uuid");
        Preconditions.checkNotNull(name, "name");
        final String previous = this.names.asMap().put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            this.uuids.asMap().remove(Strings.lower(previous), uuid);
        }
        this.uuids.put(Strings.lower(name), uuid);
    }

    public void forget(final UUID uuid) {
        final String previous = this.names.asMap().remove(uuid);
        if (previous != null) {
            this.uuids.asMap().remove(Strings.lower(previous), uuid);
        }
    }

    public Optional<String> name(final UUID uuid) {
        return Optional.ofNullable(this.names.getIfPresent(uuid));
    }

    public Optional<UUID> uuid(final String name) {
        return Optional.ofNullable(this.uuids.getIfPresent(Strings.lower(name)));
    }

    public List<String> suggestions(final String prefix, final int limit) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        return this.uuids.asMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(lower))
                .map(entry -> this.names.getIfPresent(entry.getValue()))
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(limit)
                .toList();
    }

}
