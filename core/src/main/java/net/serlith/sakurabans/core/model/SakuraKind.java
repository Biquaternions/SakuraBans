package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public record SakuraKind(
        String id,
        String displayName,
        PunishmentType baseType,
        boolean shadow,
        @Nullable AddressStrictness strictnessOrNull,
        @Nullable Scope defaultScopeOrNull,
        AddressMode addressMode,
        boolean permanentOnly,
        Set<String> requiredEvidence,
        boolean builtIn,
        long version) implements Kind {

    public SakuraKind {
        id = ModelChecks.identifier(id, "id");
        ModelChecks.name(displayName, Lengths.MAX_IDENTIFIER, "displayName");
        Preconditions.checkNotNull(baseType, "baseType");
        Preconditions.checkArgument(!shadow || baseType == PunishmentType.MUTE,
                "only a mute kind can be a shadow mute");
        Preconditions.checkNotNull(addressMode, "addressMode");
        Preconditions.checkArgument(!permanentOnly || !baseType.instant(),
                "a kick can't be permanent only");
        requiredEvidence = Set.copyOf(requiredEvidence);
        Preconditions.checkArgument(version >= 1, "version must be positive: %s", version);
    }

    @Override
    public Optional<AddressStrictness> strictness() {
        return Optional.ofNullable(this.strictnessOrNull);
    }

    @Override
    public Optional<Scope> defaultScope() {
        return Optional.ofNullable(this.defaultScopeOrNull);
    }

}
