package net.serlith.sakurabans.core.command;

import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.permission.Permissions;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PredicatePermission;

public final class PunishFlags {

    public static final String SILENT = "silent";
    public static final String PUBLIC = "public";
    public static final String HIDE = "hide";
    public static final String IP = "ip";
    public static final String GLOBAL = "global";
    public static final String SERVER = "server";
    public static final String CATEGORY = "category";
    public static final String SCOPE = "scope";
    public static final String OVERRIDE = "override";
    public static final String IF_LONGER = "if-longer";
    public static final String SENDER = "sender";
    public static final String SENDER_UUID = "sender-uuid";
    public static final String SKIP = "skip";

    private final ScopeParser scopes;

    public PunishFlags(final ScopeParser scopes) {
        this.scopes = scopes;
    }

    public CommandFlag<Void> silent(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(SILENT).withAliases("s")
                .withPermission(Permissions.flag(type, SILENT)).build();
    }

    public CommandFlag<Void> publicFlag(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(PUBLIC).withAliases("p")
                .withPermission(Permissions.flag(type, PUBLIC)).build();
    }

    public CommandFlag<Void> hide(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(HIDE)
                .withPermission(Permissions.flag(type, HIDE)).build();
    }

    public CommandFlag<Void> ip(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(IP).withAliases("I")
                .withPermission(Permissions.flag(type, IP)).build();
    }

    public CommandFlag<Void> global() {
        return CommandFlag.<CommandActor>builder(GLOBAL).withAliases("g").build();
    }

    public CommandFlag<String> server() {
        return CommandFlag.<CommandActor>builder(SERVER)
                .withComponent(StringParser.<CommandActor>stringParser()).build();
    }

    public CommandFlag<String> category() {
        return CommandFlag.<CommandActor>builder(CATEGORY)
                .withComponent(StringParser.<CommandActor>stringParser()).build();
    }

    public CommandFlag<Scope> scope() {
        return CommandFlag.<CommandActor>builder(SCOPE).withComponent(this.scopes.descriptor())
                .build();
    }

    public CommandFlag<Void> override(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(OVERRIDE).withAliases("o")
                .withPermission(Permissions.flag(type, OVERRIDE)).build();
    }

    public CommandFlag<Void> ifLonger(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(IF_LONGER).withAliases("N")
                .withPermission(Permissions.flag(type, OVERRIDE)).build();
    }

    public CommandFlag<String> sender() {
        return CommandFlag.<CommandActor>builder(SENDER)
                .withComponent(StringParser.<CommandActor>stringParser())
                .withPermission(consoleOnly()).build();
    }

    public CommandFlag<UUID> senderUuid() {
        return CommandFlag.<CommandActor>builder(SENDER_UUID)
                .withComponent(UUIDParser.<CommandActor>uuidParser())
                .withPermission(consoleOnly()).build();
    }

    public CommandFlag<Void> skip(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(SKIP)
                .withPermission(Permissions.flag(type, SKIP)).build();
    }

    private static Permission consoleOnly() {
        return PredicatePermission.<CommandActor>of(CommandActor::console);
    }

}
