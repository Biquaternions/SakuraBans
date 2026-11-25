package net.serlith.sakurabans.core.punish;

import java.util.Optional;
import net.serlith.sakurabans.core.model.SakuraKind;
/
@FunctionalInterface
public interface KindLookup {

    KindLookup BUILT_IN = BuiltInKinds::kind;

    Optional<SakuraKind> find(String id);
}
