package net.serlith.sakurabans.core.permission;

import java.util.Locale;
import net.serlith.sakurabans.api.model.PunishmentType;

public final class Permissions {

    private static final String ROOT = "sakurabans.";

    public static final String NOTIFY_ATTEMPT_JOIN = ROOT + "notify.attempt.join";
    public static final String NOTIFY_ATTEMPT_CHAT = ROOT + "notify.attempt.chat";
    public static final String VIEW_IPS = ROOT + "viewips";
    public static final String HELP = ROOT + "help";
    /** Applying a ladder (D37), whatever its step's type. */
    public static final String PUNISH = ROOT + "punish";
    public static final String INFO = ROOT + "info";
    public static final String LOCKDOWN_BYPASS = ROOT + "lockdown.bypass";
    public static final String ADMIN_LOCKDOWN = ROOT + "admin.lockdown";
    public static final String ADMIN_PRUNEHISTORY = ROOT + "admin.prunehistory";
    public static final String ADMIN_STATUS = ROOT + "admin.status";
    public static final String ADMIN_RELOAD = ROOT + "admin.reload";
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
    public static final String ALTS_UNLINK = ROOT + "alts.unlink";
    public static final String ALTS_NOTIFY = ROOT + "alts.notify";
    public static final String ALTS_IPREPORT = ROOT + "alts.ipreport";
    public static final String ADMIN_ALLOW = ROOT + "admin.allow";
    public static final String ADMIN_TEMPLATES = ROOT + "admin.templates";
    public static final String LIMITS_UNLIMITED = ROOT + "limits.unlimited";
    public static final String ADMIN_STAFFROLLBACK = ROOT + "admin.staffrollback";
    public static final String ADMIN_GDPR = ROOT + "admin.gdpr";
    public static final String ADMIN_SETTINGS = ROOT + "admin.settings";
    public static final String ADMIN_MESSAGES = ROOT + "admin.messages";
    public static final String ADMIN_IMPORT = ROOT + "admin.import";
    public static final String ADMIN_AUDIT = ROOT + "admin.audit";
    public static final String EDITOR = ROOT + "editor";
    public static final String COOLDOWN_BYPASS = ROOT + "cooldown.bypass";

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

    /** {@code sakurabans.punish.<flag>}: a flag of {@code /punish}. */
    public static String punishFlag(final String flag) {
        return PUNISH + "." + flag;
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

    public static String exempt(final PunishmentType type) {
        return node(type, "exempt");
    }

    public static String exemptBypass(final PunishmentType type) {
        return node(type, "exempt.bypass");
    }

    public static String edit(final PunishmentType type) {
        return node(type, "edit");
    }

    public static String editOwn(final PunishmentType type) {
        return node(type, "edit.own");
    }

    public static String voidPunishment(final PunishmentType type) {
        return node(type, "void");
    }

    public static String delete(final PunishmentType type) {
        return node(type, "delete");
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

    public static String template(final String name) {
        return ROOT + "template." + name;
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
