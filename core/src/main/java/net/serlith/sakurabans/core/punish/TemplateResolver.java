package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.template.TemplateInfo;
import org.jspecify.annotations.Nullable;

public interface TemplateResolver {

    Optional<TemplateInfo> template(String name);

    CompletableFuture<Optional<Resolution>> resolve(String name, Victim victim);

    record Resolution(TemplateInfo template, @Nullable Duration duration, int step) {
    }

}
