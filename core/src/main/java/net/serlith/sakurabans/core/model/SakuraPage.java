package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Page;
import org.jspecify.annotations.Nullable;

public record SakuraPage<T>(List<T> items, long total, int page, int pageSize,
        @Nullable String nextCursorOrNull) implements Page<T> {

    public SakuraPage {
        items = List.copyOf(items);
        Preconditions.checkArgument(total >= items.size(), "total < items: %s", total);
        Preconditions.checkArgument(page >= 1 && pageSize >= 1,
                "page and pageSize must be positive");
    }

    @Override
    public Optional<String> nextCursor() {
        return Optional.ofNullable(this.nextCursorOrNull);
    }
}
