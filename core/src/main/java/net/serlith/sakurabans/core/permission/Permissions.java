package net.serlith.sakurabans.core.permission;

import java.util.Locale;
import net.serlith.sakurabans.api.model.PunishmentType;

public final class Permissions {

    private static final String ROOT = "sakurabans.";

    public static final String NOTIFY_ATTEMPT_JOIN = ROOT + "notify.attempt.join";
    public static final String NOTIFY_ATTEMPT_CHAT = ROOT + "notify.attempt.chat";
    public static final String VIEW_IPS = ROOT + "viewips";
    public static final String HELP = ROOT + "help";
    public static final String SCOPE_GLOBAL = ROOT + "scope.global";
    public static final String SCOPE_DEFAULT = ROOT + "scope.default";
    public static final String LIST_BANLIST = ROOT + "list.banlist";
    public static final String LIST_MUTELIST = ROOT + "list.mutelist";
    public static final String LIST_WARNLIST = ROOT + "list.warnlist";
    public static final String LIST_HISTORY = ROOT + "list.history";
    public static final String LIST_STAFFHISTORY = ROOT + "list.staffhistory";
    public static final String LIST_WARNS = ROOT + "list.warns";
    public static final String LIST_WARNS_SELF = ROOT + "list.warns.self";
    public static final String ALTS_CHECK = ROOT + "alts.check";
    public static final String ALTS_IPHISTORY = ROOT + "alts.iphistory";
    public static final String ALTS_NAMEHISTORY = ROOT + "alts.namehistory";
    public static final String ALTS_LASTUUID = ROOT + "alts.lastuuid";

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

    public static String punish(final PunishmentType type) {
        return ROOT + key(type);
    }

    public static String temporary(final PunishmentType type) {
        return node(type, "temporary");
    }

    public static String flag(final PunishmentType type, final String flag) {
        return node(type, flag);
    }

    public static String range(final PunishmentType type) {
        return node(type, "range");
    }

    public static String revoke(final PunishmentType type) {
        return node(type, "revoke");
    }

    public static String revokeOwn(final PunishmentType type) {
        return node(type, "revoke.own");
    }

    public static String check(final PunishmentType type) {
        return node(type, "check");
    }

    public static String scopeServer(final String server) {
        return ROOT + "scope.server." + server;
    }

    public static String scopeCategory(final String category) {
        return ROOT + "scope.category." + category;
    }

    private static String key(final PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String node(final PunishmentType type, final String suffix) {
        return ROOT + key(type) + "." + suffix;
    }

}
