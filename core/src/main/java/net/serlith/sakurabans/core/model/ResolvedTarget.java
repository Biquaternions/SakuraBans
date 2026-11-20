package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import org.jspecify.annotations.Nullable;

public sealed interface ResolvedTarget {

    record Player(UUID uuid, @Nullable String name, @Nullable InetAddress lastAddress)
            implements ResolvedTarget {
        public Player {
            Preconditions.checkNotNull(uuid, "uuid");
        }
    }

    record Address(InetAddress address) implements ResolvedTarget {
        public Address {
            Preconditions.checkNotNull(address, "address");
        }
    }

    record Range(AddressRangeVictim range) implements ResolvedTarget {
        public Range {
            Preconditions.checkNotNull(range, "range");
        }
    }

    record PunishmentId(long id) implements ResolvedTarget {
    }

    record Unknown(TargetInput input) implements ResolvedTarget {
        public Unknown {
            Preconditions.checkNotNull(input, "input");
        }
    }

}
