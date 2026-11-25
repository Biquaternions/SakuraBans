package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
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

    public static SakuraKind of(final KindDefinition definition, final boolean builtIn,
            final long version) {
        return new SakuraKind(definition.id(), definition.displayName(), definition.baseType(),
                definition.shadow(), definition.strictness().orElse(null),
                definition.defaultScope().orElse(null), definition.addressMode(),
                definition.permanentOnly(), definition.requiredEvidence(), builtIn, version);
    }

    public String summary() {
        return "display-name=" + this.displayName
                + " base-type=" + this.baseType
                + " shadow=" + this.shadow
                + " strictness=" + (this.strictnessOrNull == null ? "default"
                        : this.strictnessOrNull)
                + " default-scope=" + (this.defaultScopeOrNull == null ? "default"
                        : scopeText(this.defaultScopeOrNull))
                + " address-mode=" + this.addressMode
                + " permanent-only=" + this.permanentOnly
                + " required-evidence=" + new TreeSet<>(this.requiredEvidence);
    }

    private static String scopeText(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> "global";
            case ServerScope server -> "server:" + server.server();
            case CategoryScope category -> "category:" + category.category();
        };
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
