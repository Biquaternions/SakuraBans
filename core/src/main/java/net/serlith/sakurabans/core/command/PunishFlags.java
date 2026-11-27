package net.serlith.sakurabans.core.command;

import java.time.Duration;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.command.parser.DurationParser;
import net.serlith.sakurabans.core.command.parser.ScopeFilter;
import net.serlith.sakurabans.core.command.parser.ScopeParser;
import net.serlith.sakurabans.core.permission.Permissions;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class PunishFlags {

    public static final String SILENT = "silent";
    public static final String HIDE = "hide";
    public static final String QUIET = "quiet";
    public static final String NO_RECORD = "no-record";
    public static final String SERVER_ORIGIN = "server-origin";
    public static final String IP = "ip";
    public static final String SHADOW = "shadow";
    public static final String GLOBAL = "global";
    public static final String SERVER = "server";
    public static final String CATEGORY = "category";
    public static final String SCOPE = "scope";
    public static final String OVERRIDE = "override";
    public static final String IF_LONGER = "if-longer";
    public static final String SENDER = "sender";
    public static final String SENDER_UUID = "sender-uuid";
    public static final String SKIP = "skip";
    public static final String EVIDENCE = "evidence";
    public static final String TEMPLATE = "template";
    public static final String REASON = "reason";
    public static final String DURATION = "duration";
    public static final String VOID = "void";

    private final ScopeParser scopes;

    public PunishFlags(final ScopeParser scopes) {
        this.scopes = scopes;
    }

    // The String overloads take the flag's permission node: /punish uses sakurabans.punish.<flag>.

    public CommandFlag<Void> silent(final PunishmentType type) {
        return this.silent(Permissions.flag(type, SILENT));
    }

    public CommandFlag<Void> silent(final String permission) {
        return CommandFlag.<CommandActor>builder(SILENT).withAliases("s")
                .withPermission(permission).build();
    }

    public CommandFlag<Void> hide(final PunishmentType type) {
        return this.hide(Permissions.flag(type, HIDE));
    }

    public CommandFlag<Void> hide(final String permission) {
        return CommandFlag.<CommandActor>builder(HIDE).withPermission(permission).build();
    }

    public CommandFlag<Void> quiet(final PunishmentType type) {
        return this.quiet(Permissions.flag(type, QUIET));
    }

    public CommandFlag<Void> quiet(final String permission) {
        return CommandFlag.<CommandActor>builder(QUIET).withPermission(permission).build();
    }

    public CommandFlag<Void> noRecord() {
        return CommandFlag.<CommandActor>builder(NO_RECORD)
                .withPermission(Permissions.flag(PunishmentType.KICK, "norecord")).build();
    }

    public CommandFlag<String> serverOrigin() {
        return CommandFlag.<CommandActor>builder(SERVER_ORIGIN)
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(SERVER_ORIGIN)
                        .parser(StringParser.<CommandActor>stringParser())
                        .suggestionProvider(this.scopes.servers()))
                .withPermission(Permissions.ADMIN_SERVER_ORIGIN)
                .build();
    }

    public CommandFlag<Void> ip(final PunishmentType type) {
        return this.ip(Permissions.flag(type, IP));
    }

    public CommandFlag<Void> ip(final String permission) {
        return CommandFlag.<CommandActor>builder(IP).withAliases("I")
                .withPermission(permission).build();
    }

    public CommandFlag<Void> shadow(final PunishmentType type) {
        return CommandFlag.<CommandActor>builder(SHADOW)
                .withPermission(Permissions.flag(type, SHADOW)).build();
    }

    public CommandFlag<Void> global() {
        return CommandFlag.<CommandActor>builder(GLOBAL).withAliases("g").build();
    }

    public CommandFlag<String> server() {
        return CommandFlag.<CommandActor>builder(SERVER)
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(SERVER)
                        .parser(StringParser.<CommandActor>stringParser())
                        .suggestionProvider(this.scopes.servers()))
                .build();
    }

    public CommandFlag<String> category() {
        return CommandFlag.<CommandActor>builder(CATEGORY)
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(CATEGORY)
                        .parser(StringParser.<CommandActor>stringParser())
                        .suggestionProvider(this.scopes.categories()))
                .build();
    }

    public CommandFlag<Scope> scope() {
        return CommandFlag.<CommandActor>builder(SCOPE).withComponent(this.scopes.descriptor())
                .build();
    }

    public CommandFlag<ScopeFilter> scopeFilter() {
        return CommandFlag.<CommandActor>builder(SCOPE)
                .withComponent(this.scopes.filterDescriptor())
                .build();
    }

    public CommandFlag<Void> override(final PunishmentType type) {
        return this.override(Permissions.flag(type, OVERRIDE));
    }

    public CommandFlag<Void> override(final String permission) {
        return CommandFlag.<CommandActor>builder(OVERRIDE).withAliases("o")
                .withPermission(permission).build();
    }

    public CommandFlag<Void> ifLonger(final PunishmentType type) {
        return this.ifLonger(Permissions.flag(type, OVERRIDE));
    }

    public CommandFlag<Void> ifLonger(final String permission) {
        return CommandFlag.<CommandActor>builder(IF_LONGER).withAliases("N")
                .withPermission(permission).build();
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

    public CommandFlag<String> template(final SuggestionProvider<CommandActor> names) {
        return CommandFlag.<CommandActor>builder(TEMPLATE).withAliases("t")
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(TEMPLATE)
                        .parser(StringParser.<CommandActor>stringParser())
                        .suggestionProvider(names))
                .build();
    }

    public CommandFlag<String> evidence(final PunishmentType type) {
        return this.evidence(Permissions.flag(type, EVIDENCE));
    }

    public CommandFlag<String> evidence(final String permission) {
        return CommandFlag.<CommandActor>builder(EVIDENCE).withAliases("e")
                .withComponent(StringParser.<CommandActor>stringParser())
                .withPermission(permission)
                .asRepeatable().build();
    }

    public CommandFlag<String> reason() {
        return CommandFlag.<CommandActor>builder(REASON).withAliases("r")
                .withComponent(CommandComponent.<CommandActor, String>builder()
                        .name(REASON)
                        .parser(StringParser.<CommandActor>greedyStringParser()))
                .build();
    }

    public CommandFlag<Duration> duration() {
        return CommandFlag.<CommandActor>builder(DURATION).withAliases("d")
                .withComponent(DurationParser.descriptor())
                .build();
    }

    // No flag permission: the revoke command checks the void permission and replies itself.
    public CommandFlag<Void> voidFlag() {
        return CommandFlag.<CommandActor>builder(VOID).build();
    }

    public CommandFlag<Void> skip(final PunishmentType type) {
        return this.skip(Permissions.flag(type, SKIP));
    }

    public CommandFlag<Void> skip(final String permission) {
        return CommandFlag.<CommandActor>builder(SKIP).withPermission(permission).build();
    }

    private static Permission consoleOnly() {
        return PredicatePermission.<CommandActor>of(CommandActor::console);
    }

}
