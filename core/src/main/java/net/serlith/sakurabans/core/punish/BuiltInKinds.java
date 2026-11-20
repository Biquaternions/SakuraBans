package net.serlith.sakurabans.core.punish;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.model.SakuraKind;
import org.jspecify.annotations.Nullable;

public final class BuiltInKinds {

    private static final Map<String, SakuraKind> KINDS = Stream.of(
                    kind("ban", PunishmentType.BAN, false, null, null, AddressMode.AS_GIVEN, false),
                    kind("ip-ban", PunishmentType.BAN, false, null, null, AddressMode.ADDRESS,
                            false),
                    kind("mute", PunishmentType.MUTE, false, null, null, AddressMode.AS_GIVEN,
                            false),
                    kind("shadow-mute", PunishmentType.MUTE, true, null, null,
                            AddressMode.AS_GIVEN, false),
                    kind("warn", PunishmentType.WARN, false, null, null, AddressMode.AS_GIVEN,
                            false),
                    kind("kick", PunishmentType.KICK, false, null, null, AddressMode.AS_GIVEN,
                            false),
                    kind("blacklist", PunishmentType.BAN, false, AddressStrictness.STRICT,
                            GlobalScope.INSTANCE, AddressMode.AS_GIVEN, true),
                    kind("competitive-ban", PunishmentType.BAN, false, null,
                            CategoryScope.of("competitive"), AddressMode.AS_GIVEN, false))
            .collect(Collectors.toUnmodifiableMap(SakuraKind::id, Function.identity()));

    private BuiltInKinds() {
    }

    private static SakuraKind kind(final String id, final PunishmentType type,
            final boolean shadow, final @Nullable AddressStrictness strictness,
            final @Nullable Scope defaultScope, final AddressMode addressMode,
            final boolean permanentOnly) {
        return new SakuraKind(id, id, type, shadow, strictness, defaultScope, addressMode,
                permanentOnly, Set.of(), true, 1);
    }

    public static Optional<SakuraKind> kind(final String id) {
        return Optional.ofNullable(KINDS.get(id));
    }

    public static String defaultFor(final PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

}
