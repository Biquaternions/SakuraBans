package net.serlith.sakurabans.core.storage.repository;

import java.util.Set;
import net.serlith.sakurabans.api.model.AddressStrictness;

@FunctionalInterface
public interface StrictnessLevels {

    StrictnessLevels NONE = Set::of;

    Set<AddressStrictness> levels();

}
