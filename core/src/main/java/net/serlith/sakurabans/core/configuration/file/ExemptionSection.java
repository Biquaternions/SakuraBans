package net.serlith.sakurabans.core.configuration.file;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.serlith.sakurabans.core.configuration.settings.ExemptionProvider;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class ExemptionSection {

    private ExemptionProvider provider = ExemptionProvider.PERMISSION;
    private boolean permitSameWeight = true;
    private boolean protectsEdits = true;
    private List<String> players = new ArrayList<>();
    private List<String> groups = new ArrayList<>();
}
