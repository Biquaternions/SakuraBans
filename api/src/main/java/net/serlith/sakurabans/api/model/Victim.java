package net.serlith.sakurabans.api.model;

/**
 * Who or what a punishment applies to. Create one with the {@code of} factory of a subtype, for
 * example {@link PlayerVictim#of(java.util.UUID)}.
 */
public sealed interface Victim permits PlayerVictim, AddressVictim, CompositeVictim,
        AddressRangeVictim {
}
