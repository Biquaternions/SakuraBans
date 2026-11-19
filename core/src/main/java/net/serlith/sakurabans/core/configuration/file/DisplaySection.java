package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import net.serlith.sakurabans.core.configuration.settings.IdStyle;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class DisplaySection {

    private String defaultLocale = "en_US";
    private String dateFormat = "dd MMM yyyy HH:mm";
    private String timezone = "default";
    private IdStyle idStyle = IdStyle.NUMERIC;
    private boolean censorIpAddresses;
}
