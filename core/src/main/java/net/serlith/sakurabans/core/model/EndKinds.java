package net.serlith.sakurabans.core.model;

import com.google.common.collect.Sets;
import java.util.Set;
import net.serlith.sakurabans.api.model.EndKind;

public final class EndKinds {

    public static final Set<EndKind> WITHOUT_ACTOR =
            Sets.immutableEnumSet(EndKind.EXPIRED, EndKind.SUPERSEDED);

    private EndKinds() {
    }
}
