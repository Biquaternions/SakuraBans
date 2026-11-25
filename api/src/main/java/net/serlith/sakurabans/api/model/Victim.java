package net.serlith.sakurabans.api.model;

/**
 * Who or what a punishment applies to. Create one with the {@code of} factory of a subtype, for
 * example {@link PlayerVictim#of(java.util.UUID)}. {@link ErasedAddressVictim} only comes from a
 * data erase; a draft can't name one (name the address instead).
 */
public sealed interface Victim permits PlayerVictim, AddressVictim, CompositeVictim,
        AddressRangeVictim, ErasedAddressVictim {
}
