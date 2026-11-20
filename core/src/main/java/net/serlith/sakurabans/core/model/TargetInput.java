package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;

public sealed interface TargetInput {

    record NameInput(String name) implements TargetInput {
        public NameInput {
            Preconditions.checkNotNull(name, "name");
        }
    }

    record UuidInput(UUID uuid) implements TargetInput {
        public UuidInput {
            Preconditions.checkNotNull(uuid, "uuid");
        }
    }

    record AddressInput(InetAddress address) implements TargetInput {
        public AddressInput {
            Preconditions.checkNotNull(address, "address");
        }
    }

    record RangeInput(AddressRangeVictim range) implements TargetInput {
        public RangeInput {
            Preconditions.checkNotNull(range, "range");
        }
    }

    record IdInput(long id) implements TargetInput {
        public IdInput {
            Preconditions.checkArgument(id >= 1, "id must be positive: %s", id);
        }
    }

}
