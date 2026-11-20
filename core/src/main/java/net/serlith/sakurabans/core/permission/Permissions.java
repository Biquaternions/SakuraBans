package net.serlith.sakurabans.core.permission;

import java.util.Locale;
import net.serlith.sakurabans.api.model.PunishmentType;

public final class Permissions {

    private static final String ROOT = "sakurabans.";

    public static final String NOTIFY_ATTEMPT_JOIN = ROOT + "notify.attempt.join";
    public static final String NOTIFY_ATTEMPT_CHAT = ROOT + "notify.attempt.chat";
    public static final String VIEW_IPS = ROOT + "viewips";

    private Permissions() {
    }

    public static String notify(final PunishmentType type) {
        return node(type, "notify");
    }

    public static String notifySilent(final PunishmentType type) {
        return node(type, "notify.silent");
    }

    public static String notifyRevoke(final PunishmentType type) {
        return node(type, "notify.revoke");
    }

    private static String node(final PunishmentType type, final String suffix) {
        return ROOT + type.name().toLowerCase(Locale.ROOT) + "." + suffix;
    }

}
