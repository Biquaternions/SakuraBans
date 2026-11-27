package net.serlith.sakurabans.core.permission;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.serlith.sakurabans.api.model.TemplateRef;

public interface TemplateGuard {

    TemplateGuard NONE = new TemplateGuard() {
        @Override
        public Optional<String> guarding(final TemplateRef ref) {
            return Optional.empty();
        }

        @Override
        public Set<String> guardedNames() {
            return Set.of();
        }
    };

    static TemplateGuard lazy(final Supplier<? extends TemplateGuard> guard) {
        return new TemplateGuard() {
            @Override
            public Optional<String> guarding(final TemplateRef ref) {
                return guard.get().guarding(ref);
            }

            @Override
            public Set<String> guardedNames() {
                return guard.get().guardedNames();
            }
        };
    }

    Optional<String> guarding(TemplateRef ref);

    Set<String> guardedNames();

}
