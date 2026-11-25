package net.serlith.sakurabans.core.message;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public record MessageKey(String key) {

    public static final MessageKey PREFIX = new MessageKey("prefix");

    public static final MessageKey ERROR_INTERNAL = new MessageKey("error.internal");
    public static final MessageKey ERROR_NOT_READY = new MessageKey("error.not_ready");
    public static final MessageKey ERROR_NO_PERMISSION = new MessageKey("error.no_permission");

    public static final MessageKey OPERATOR_CONSOLE = new MessageKey("operator.console");

    public static final MessageKey TYPE_BAN = new MessageKey("type.ban");
    public static final MessageKey TYPE_MUTE = new MessageKey("type.mute");
    public static final MessageKey TYPE_WARN = new MessageKey("type.warn");
    public static final MessageKey TYPE_KICK = new MessageKey("type.kick");

    public static final MessageKey SCOPE_GLOBAL = new MessageKey("scope.global");
    public static final MessageKey SCOPE_SERVER = new MessageKey("scope.server");
    public static final MessageKey SCOPE_CATEGORY = new MessageKey("scope.category");

    public static final MessageKey TIME_PERMANENT = new MessageKey("time.permanent");
    public static final MessageKey TIME_NEVER = new MessageKey("time.never");
    public static final MessageKey TIME_SEPARATOR = new MessageKey("time.separator");
    public static final MessageKey TIME_LAST_SEPARATOR = new MessageKey("time.last_separator");
    public static final MessageKey TIME_SECOND = new MessageKey("time.second");
    public static final MessageKey TIME_SECONDS = new MessageKey("time.seconds");
    public static final MessageKey TIME_MINUTE = new MessageKey("time.minute");
    public static final MessageKey TIME_MINUTES = new MessageKey("time.minutes");
    public static final MessageKey TIME_HOUR = new MessageKey("time.hour");
    public static final MessageKey TIME_HOURS = new MessageKey("time.hours");
    public static final MessageKey TIME_DAY = new MessageKey("time.day");
    public static final MessageKey TIME_DAYS = new MessageKey("time.days");
    public static final MessageKey TIME_YEAR = new MessageKey("time.year");
    public static final MessageKey TIME_YEARS = new MessageKey("time.years");

    public static final MessageKey SCREEN_BAN = new MessageKey("screen.ban");
    public static final MessageKey SCREEN_BAN_TEMPORARY = new MessageKey("screen.ban_temporary");
    public static final MessageKey SCREEN_KICK = new MessageKey("screen.kick");
    public static final MessageKey SCREEN_CHECK_FAILED = new MessageKey("screen.check_failed");
    public static final MessageKey SCREEN_CONNECTION_LIMIT =
            new MessageKey("screen.connection_limit");

    public static final MessageKey VICTIM_MUTED = new MessageKey("victim.muted");
    public static final MessageKey VICTIM_MUTED_TEMPORARY =
            new MessageKey("victim.muted_temporary");
    public static final MessageKey VICTIM_WARNED = new MessageKey("victim.warned");

    public static final MessageKey CHAT_MUTED = new MessageKey("chat.muted");
    public static final MessageKey CHAT_MUTED_TEMPORARY = new MessageKey("chat.muted_temporary");
    public static final MessageKey CHAT_COMMAND_MUTED = new MessageKey("chat.command_muted");
    public static final MessageKey CHAT_LOADING = new MessageKey("chat.loading");

    public static final MessageKey NOTIFY_SILENT_TAG = new MessageKey("notify.silent_tag");
    public static final MessageKey NOTIFY_BAN = new MessageKey("notify.ban");
    public static final MessageKey NOTIFY_MUTE = new MessageKey("notify.mute");
    public static final MessageKey NOTIFY_WARN = new MessageKey("notify.warn");
    public static final MessageKey NOTIFY_KICK = new MessageKey("notify.kick");
    public static final MessageKey NOTIFY_UNBAN = new MessageKey("notify.unban");
    public static final MessageKey NOTIFY_UNMUTE = new MessageKey("notify.unmute");
    public static final MessageKey NOTIFY_UNWARN = new MessageKey("notify.unwarn");
    public static final MessageKey NOTIFY_ATTEMPT_JOIN = new MessageKey("notify.attempt_join");
    public static final MessageKey NOTIFY_ATTEMPT_CHAT = new MessageKey("notify.attempt_chat");

    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_BOOLEAN =
            new MessageKey("command.caption.argument.parse.failure.boolean");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_NUMBER =
            new MessageKey("command.caption.argument.parse.failure.number");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_CHAR =
            new MessageKey("command.caption.argument.parse.failure.char");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_STRING =
            new MessageKey("command.caption.argument.parse.failure.string");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_UUID =
            new MessageKey("command.caption.argument.parse.failure.uuid");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_ENUM =
            new MessageKey("command.caption.argument.parse.failure.enum");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_REGEX =
            new MessageKey("command.caption.argument.parse.failure.regex");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN =
            new MessageKey("command.caption.argument.parse.failure.flag.unknown");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG =
            new MessageKey("command.caption.argument.parse.failure.flag.duplicate_flag");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED =
            new MessageKey("command.caption.argument.parse.failure.flag.no_flag_started");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT =
            new MessageKey("command.caption.argument.parse.failure.flag.missing_argument");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION =
            new MessageKey("command.caption.argument.parse.failure.flag.no_permission");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_COLOR =
            new MessageKey("command.caption.argument.parse.failure.color");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_DURATION =
            new MessageKey("command.caption.argument.parse.failure.duration");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_AGGREGATE_MISSING =
            new MessageKey("command.caption.argument.parse.failure.aggregate.missing");
    public static final MessageKey COMMAND_CAPTION_ARGUMENT_PARSE_FAILURE_EITHER =
            new MessageKey("command.caption.argument.parse.failure.either");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_UNEXPECTED =
            new MessageKey("command.caption.exception.unexpected");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_INVALID_ARGUMENT =
            new MessageKey("command.caption.exception.invalid_argument");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_NO_SUCH_COMMAND =
            new MessageKey("command.caption.exception.no_such_command");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_NO_PERMISSION =
            new MessageKey("command.caption.exception.no_permission");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_INVALID_SENDER =
            new MessageKey("command.caption.exception.invalid_sender");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_INVALID_SENDER_LIST =
            new MessageKey("command.caption.exception.invalid_sender_list");
    public static final MessageKey COMMAND_CAPTION_EXCEPTION_INVALID_SYNTAX =
            new MessageKey("command.caption.exception.invalid_syntax");
    public static final MessageKey COMMAND_CAPTION_SAKURABANS_TARGET =
            new MessageKey("command.caption.sakurabans.target");
    public static final MessageKey COMMAND_CAPTION_SAKURABANS_DURATION =
            new MessageKey("command.caption.sakurabans.duration");
    public static final MessageKey COMMAND_CAPTION_SAKURABANS_SCOPE =
            new MessageKey("command.caption.sakurabans.scope");
    public static final MessageKey COMMAND_DESCRIPTION_SAKURABANS =
            new MessageKey("command.description.sakurabans");
    public static final MessageKey COMMAND_DESCRIPTION_BAN =
            new MessageKey("command.description.ban");
    public static final MessageKey COMMAND_DESCRIPTION_TEMPBAN =
            new MessageKey("command.description.tempban");
    public static final MessageKey COMMAND_DESCRIPTION_IPBAN =
            new MessageKey("command.description.ipban");
    public static final MessageKey COMMAND_DESCRIPTION_MUTE =
            new MessageKey("command.description.mute");
    public static final MessageKey COMMAND_DESCRIPTION_TEMPMUTE =
            new MessageKey("command.description.tempmute");
    public static final MessageKey COMMAND_DESCRIPTION_IPMUTE =
            new MessageKey("command.description.ipmute");
    public static final MessageKey COMMAND_DESCRIPTION_WARN =
            new MessageKey("command.description.warn");
    public static final MessageKey COMMAND_DESCRIPTION_KICK =
            new MessageKey("command.description.kick");
    public static final MessageKey COMMAND_DESCRIPTION_UNBAN =
            new MessageKey("command.description.unban");
    public static final MessageKey COMMAND_DESCRIPTION_PARDON =
            new MessageKey("command.description.pardon");
    public static final MessageKey COMMAND_DESCRIPTION_ALLOW =
            new MessageKey("command.description.allow");
    public static final MessageKey COMMAND_DESCRIPTION_TEMPLATES =
            new MessageKey("command.description.templates");
    public static final MessageKey COMMAND_DESCRIPTION_KINDS =
            new MessageKey("command.description.kinds");
    public static final MessageKey COMMAND_DESCRIPTION_KIND =
            new MessageKey("command.description.kind");
    public static final MessageKey COMMAND_DESCRIPTION_UNLINK =
            new MessageKey("command.description.unlink");
    public static final MessageKey COMMAND_DESCRIPTION_UNMUTE =
            new MessageKey("command.description.unmute");
    public static final MessageKey COMMAND_DESCRIPTION_UNWARN =
            new MessageKey("command.description.unwarn");
    public static final MessageKey COMMAND_DESCRIPTION_CHECKBAN =
            new MessageKey("command.description.checkban");
    public static final MessageKey COMMAND_DESCRIPTION_CHECKMUTE =
            new MessageKey("command.description.checkmute");
    public static final MessageKey COMMAND_DESCRIPTION_CHECKWARN =
            new MessageKey("command.description.checkwarn");
    public static final MessageKey COMMAND_DESCRIPTION_HISTORY =
            new MessageKey("command.description.history");
    public static final MessageKey COMMAND_DESCRIPTION_STAFFHISTORY =
            new MessageKey("command.description.staffhistory");
    public static final MessageKey COMMAND_DESCRIPTION_WARNS =
            new MessageKey("command.description.warns");
    public static final MessageKey COMMAND_DESCRIPTION_BANLIST =
            new MessageKey("command.description.banlist");
    public static final MessageKey COMMAND_DESCRIPTION_MUTELIST =
            new MessageKey("command.description.mutelist");
    public static final MessageKey COMMAND_DESCRIPTION_WARNLIST =
            new MessageKey("command.description.warnlist");
    public static final MessageKey COMMAND_DESCRIPTION_ALTS =
            new MessageKey("command.description.alts");
    public static final MessageKey COMMAND_DESCRIPTION_IPHISTORY =
            new MessageKey("command.description.iphistory");
    public static final MessageKey COMMAND_DESCRIPTION_NAMEHISTORY =
            new MessageKey("command.description.namehistory");
    public static final MessageKey COMMAND_DESCRIPTION_LASTUUID =
            new MessageKey("command.description.lastuuid");
    public static final MessageKey COMMAND_INVALID = new MessageKey("command.invalid");
    public static final MessageKey COMMAND_UNKNOWN_TARGET =
            new MessageKey("command.unknown_target");
    public static final MessageKey COMMAND_ID_NOT_ALLOWED =
            new MessageKey("command.id_not_allowed");
    public static final MessageKey COMMAND_RANGE_NO_PERMISSION =
            new MessageKey("command.range_no_permission");
    public static final MessageKey COMMAND_NO_ADDRESS = new MessageKey("command.no_address");
    public static final MessageKey COMMAND_ADDRESS_NO_PERMISSION =
            new MessageKey("command.address_no_permission");
    public static final MessageKey COMMAND_SCOPE_NO_PERMISSION =
            new MessageKey("command.scope_no_permission");
    public static final MessageKey COMMAND_SCOPE_DEFAULT_NO_PERMISSION =
            new MessageKey("command.scope_default_no_permission");
    public static final MessageKey COMMAND_SENDER_NEEDS_NAME =
            new MessageKey("command.sender_needs_name");
    public static final MessageKey COMMAND_PLAYERS_ONLY = new MessageKey("command.players_only");
    public static final MessageKey COMMAND_PLAYER_REQUIRED =
            new MessageKey("command.player_required");
    public static final MessageKey PUNISH_BAN = new MessageKey("punish.ban");
    public static final MessageKey PUNISH_MUTE = new MessageKey("punish.mute");
    public static final MessageKey PUNISH_WARN = new MessageKey("punish.warn");
    public static final MessageKey PUNISH_KICK = new MessageKey("punish.kick");
    public static final MessageKey PUNISH_FAILURE_ALREADY_PUNISHED =
            new MessageKey("punish.failure.already_punished");
    public static final MessageKey PUNISH_FAILURE_EXEMPT = new MessageKey("punish.failure.exempt");
    public static final MessageKey PUNISH_FAILURE_CANCELLED =
            new MessageKey("punish.failure.cancelled");
    public static final MessageKey PUNISH_FAILURE_LIMIT_EXCEEDED =
            new MessageKey("punish.failure.limit_exceeded");
    public static final MessageKey PUNISH_FAILURE_COOLDOWN =
            new MessageKey("punish.failure.cooldown");
    public static final MessageKey PUNISH_FAILURE_REASON_REQUIRED =
            new MessageKey("punish.failure.reason_required");
    public static final MessageKey PUNISH_FAILURE_TEMPLATE_REQUIRED =
            new MessageKey("punish.failure.template_required");
    public static final MessageKey PUNISH_FAILURE_NO_TEMPLATE =
            new MessageKey("punish.failure.no_template");
    public static final MessageKey PUNISH_FAILURE_TEMPLATE_NO_PERMISSION =
            new MessageKey("punish.failure.template_no_permission");
    public static final MessageKey PUNISH_FAILURE_TEMPLATE_TYPE =
            new MessageKey("punish.failure.template_type");
    public static final MessageKey PUNISH_FAILURE_UNKNOWN_TEMPLATE =
            new MessageKey("punish.failure.unknown_template");
    public static final MessageKey PUNISH_FAILURE_UNKNOWN_KIND =
            new MessageKey("punish.failure.unknown_kind");
    public static final MessageKey PUNISH_FAILURE_EVIDENCE_REQUIRED =
            new MessageKey("punish.failure.evidence_required");
    public static final MessageKey PUNISH_FAILURE_FORBIDDEN =
            new MessageKey("punish.failure.forbidden");
    public static final MessageKey REVOKE_BAN = new MessageKey("revoke.ban");
    public static final MessageKey REVOKE_MUTE = new MessageKey("revoke.mute");
    public static final MessageKey REVOKE_WARN = new MessageKey("revoke.warn");
    public static final MessageKey REVOKE_FAILURE_NOT_FOUND =
            new MessageKey("revoke.failure.not_found");
    public static final MessageKey REVOKE_FAILURE_NOT_OWN =
            new MessageKey("revoke.failure.not_own");
    public static final MessageKey REVOKE_FAILURE_EXEMPT = new MessageKey("revoke.failure.exempt");
    public static final MessageKey REVOKE_FAILURE_CANCELLED =
            new MessageKey("revoke.failure.cancelled");
    public static final MessageKey REVOKE_FAILURE_REASON_REQUIRED =
            new MessageKey("revoke.failure.reason_required");
    public static final MessageKey REVOKE_FAILURE_FORBIDDEN =
            new MessageKey("revoke.failure.forbidden");
    public static final MessageKey REVOKE_FAILURE_SCOPE_NO_PERMISSION =
            new MessageKey("revoke.failure.scope_no_permission");
    public static final MessageKey REVOKE_SKIPPED_SCOPES = new MessageKey("revoke.skipped_scopes");
    public static final MessageKey REVOKE_FAILURE_WRONG_TYPE =
            new MessageKey("revoke.failure.wrong_type");
    public static final MessageKey CHECK_NONE = new MessageKey("check.none");
    public static final MessageKey CHECK_HEADER = new MessageKey("check.header");
    public static final MessageKey CHECK_DETAIL = new MessageKey("check.detail");
    public static final MessageKey HISTORY_HEADER = new MessageKey("history.header");
    public static final MessageKey STAFFHISTORY_HEADER = new MessageKey("staffhistory.header");
    public static final MessageKey WARNS_HEADER = new MessageKey("warns.header");
    public static final MessageKey LIST_HEADER_BANLIST = new MessageKey("list.header.banlist");
    public static final MessageKey LIST_HEADER_MUTELIST = new MessageKey("list.header.mutelist");
    public static final MessageKey LIST_HEADER_WARNLIST = new MessageKey("list.header.warnlist");
    public static final MessageKey LIST_ENTRY = new MessageKey("list.entry");
    public static final MessageKey LIST_EMPTY = new MessageKey("list.empty");
    public static final MessageKey LIST_NO_SUCH_PAGE = new MessageKey("list.no_such_page");
    public static final MessageKey LIST_FOOTER = new MessageKey("list.footer");
    public static final MessageKey LIST_PREVIOUS = new MessageKey("list.previous");
    public static final MessageKey LIST_NEXT = new MessageKey("list.next");
    public static final MessageKey LIST_ARROW_DISABLED = new MessageKey("list.arrow_disabled");
    public static final MessageKey LIST_STATUS_ACTIVE = new MessageKey("list.status.active");
    public static final MessageKey LIST_STATUS_EXPIRED = new MessageKey("list.status.expired");
    public static final MessageKey LIST_STATUS_ENDED = new MessageKey("list.status.ended");
    public static final MessageKey LIST_STATUS_INSTANT = new MessageKey("list.status.instant");
    public static final MessageKey ALTS_NONE = new MessageKey("alts.none");
    public static final MessageKey ALTS_HEADER = new MessageKey("alts.header");
    public static final MessageKey ALTS_ENTRY = new MessageKey("alts.entry");
    public static final MessageKey IPHISTORY_NONE = new MessageKey("iphistory.none");
    public static final MessageKey IPHISTORY_HEADER = new MessageKey("iphistory.header");
    public static final MessageKey IPHISTORY_ENTRY = new MessageKey("iphistory.entry");
    public static final MessageKey NAMEHISTORY_NONE = new MessageKey("namehistory.none");
    public static final MessageKey NAMEHISTORY_HEADER = new MessageKey("namehistory.header");
    public static final MessageKey NAMEHISTORY_ENTRY = new MessageKey("namehistory.entry");
    public static final MessageKey LASTUUID_RESULT = new MessageKey("lastuuid.result");
    public static final MessageKey LASTUUID_NONE = new MessageKey("lastuuid.none");

    public static final MessageKey ALLOW_ADDED = new MessageKey("allow.added");
    public static final MessageKey ALLOW_ALREADY = new MessageKey("allow.already");
    public static final MessageKey ALLOW_REMOVED = new MessageKey("allow.removed");
    public static final MessageKey ALLOW_NOT_LISTED = new MessageKey("allow.not_listed");
    public static final MessageKey ALLOW_TARGET = new MessageKey("allow.target");
    public static final MessageKey ALLOW_LIST_EMPTY = new MessageKey("allow.list.empty");
    public static final MessageKey ALLOW_LIST_HEADER = new MessageKey("allow.list.header");
    public static final MessageKey ALLOW_LIST_ENTRY = new MessageKey("allow.list.entry");
    public static final MessageKey UNLINK_DONE = new MessageKey("unlink.done");
    public static final MessageKey UNLINK_NONE = new MessageKey("unlink.none");
    public static final MessageKey UNLINK_NOT_ADDRESS = new MessageKey("unlink.not_address");

    public static final MessageKey NOTIFY_ALTS_JOIN = new MessageKey("notify.alts.join");
    public static final MessageKey NOTIFY_ALTS_NAME = new MessageKey("notify.alts.name");
    public static final MessageKey NOTIFY_ALTS_PUNISHED_NAME =
            new MessageKey("notify.alts.punished_name");

    public static final MessageKey ALTS_PUNISHED = new MessageKey("alts.punished");
    public static final MessageKey ALTS_SOURCE = new MessageKey("alts.source");
    public static final MessageKey IPREPORT_NONE = new MessageKey("ipreport.none");
    public static final MessageKey IPREPORT_HEADER = new MessageKey("ipreport.header");
    public static final MessageKey IPREPORT_ENTRY = new MessageKey("ipreport.entry");
    public static final MessageKey COMMAND_DESCRIPTION_IPREPORT =
            new MessageKey("command.description.ipreport");

    public static final MessageKey TEMPLATES_LIST_EMPTY = new MessageKey("templates.list.empty");
    public static final MessageKey TEMPLATES_LIST_HEADER =
            new MessageKey("templates.list.header");
    public static final MessageKey TEMPLATES_LIST_ENTRY = new MessageKey("templates.list.entry");
    public static final MessageKey TEMPLATES_IMPORT_CONFIRM =
            new MessageKey("templates.import.confirm");
    public static final MessageKey TEMPLATES_IMPORT_DONE = new MessageKey("templates.import.done");
    public static final MessageKey TEMPLATES_IMPORT_ERROR =
            new MessageKey("templates.import.error");
    public static final MessageKey TEMPLATES_EXPORT_DONE = new MessageKey("templates.export.done");

    public static final MessageKey KINDS_LIST_HEADER = new MessageKey("kinds.list.header");
    public static final MessageKey KINDS_LIST_ENTRY = new MessageKey("kinds.list.entry");

    public static final MessageKey VICTIM_WARNED_AWAY = new MessageKey("victim.warned_away");

    public static final MessageKey COMMAND_DESCRIPTION_INFO =
            new MessageKey("command.description.info");
    public static final MessageKey COMMAND_DESCRIPTION_EDIT =
            new MessageKey("command.description.edit");
    public static final MessageKey COMMAND_DESCRIPTION_VOID =
            new MessageKey("command.description.void");
    public static final MessageKey COMMAND_DESCRIPTION_DELETE =
            new MessageKey("command.description.delete");
    public static final MessageKey COMMAND_DESCRIPTION_EVIDENCE =
            new MessageKey("command.description.evidence");
    public static final MessageKey COMMAND_NOT_AN_ID = new MessageKey("command.not_an_id");
    public static final MessageKey EDIT_DONE = new MessageKey("edit.done");
    public static final MessageKey EDIT_PERMANENT = new MessageKey("edit.permanent");
    public static final MessageKey EDIT_FAILURE_NOT_FOUND =
            new MessageKey("edit.failure.not_found");
    public static final MessageKey EDIT_FAILURE_NOT_ACTIVE =
            new MessageKey("edit.failure.not_active");
    public static final MessageKey EDIT_FAILURE_INVALID_EXPIRY =
            new MessageKey("edit.failure.invalid_expiry");
    public static final MessageKey EDIT_FAILURE_ALREADY_PUNISHED =
            new MessageKey("edit.failure.already_punished");
    public static final MessageKey EDIT_FAILURE_EXEMPT = new MessageKey("edit.failure.exempt");
    public static final MessageKey EDIT_FAILURE_CANCELLED =
            new MessageKey("edit.failure.cancelled");
    public static final MessageKey EDIT_FAILURE_CONFLICT = new MessageKey("edit.failure.conflict");
    public static final MessageKey EDIT_FAILURE_EVIDENCE_ALREADY_ATTACHED =
            new MessageKey("edit.failure.evidence_already_attached");
    public static final MessageKey EDIT_FAILURE_EVIDENCE_NOT_ATTACHED =
            new MessageKey("edit.failure.evidence_not_attached");
    public static final MessageKey EDIT_FAILURE_FORBIDDEN =
            new MessageKey("edit.failure.forbidden");
    public static final MessageKey EDIT_FAILURE_ALREADY_VOIDED =
            new MessageKey("edit.failure.already_voided");
    public static final MessageKey EDIT_WHAT_REASON = new MessageKey("edit.what.reason");
    public static final MessageKey EDIT_WHAT_EXPIRY = new MessageKey("edit.what.expiry");
    public static final MessageKey EDIT_WHAT_SCOPE = new MessageKey("edit.what.scope");
    public static final MessageKey EDIT_WHAT_EVIDENCE_ADDED =
            new MessageKey("edit.what.evidence_added");
    public static final MessageKey EDIT_WHAT_EVIDENCE_REMOVED =
            new MessageKey("edit.what.evidence_removed");
    public static final MessageKey VOID_DONE = new MessageKey("void.done");
    public static final MessageKey DELETE_CONFIRM = new MessageKey("delete.confirm");
    public static final MessageKey DELETE_DONE = new MessageKey("delete.done");
    public static final MessageKey INFO_EVIDENCE = new MessageKey("info.evidence");
    public static final MessageKey INFO_ENDED = new MessageKey("info.ended");
    public static final MessageKey INFO_VOIDED = new MessageKey("info.voided");
    public static final MessageKey INFO_AUDIT_HEADER = new MessageKey("info.audit.header");
    public static final MessageKey INFO_AUDIT_ENTRY = new MessageKey("info.audit.entry");

    public static final MessageKey SCREEN_LOCKDOWN = new MessageKey("screen.lockdown");
    public static final MessageKey COMMAND_DESCRIPTION_LOCKDOWN =
            new MessageKey("command.description.lockdown");
    public static final MessageKey COMMAND_DESCRIPTION_STAFFROLLBACK =
            new MessageKey("command.description.staffrollback");
    public static final MessageKey COMMAND_DESCRIPTION_PRUNEHISTORY =
            new MessageKey("command.description.prunehistory");
    public static final MessageKey COMMAND_DESCRIPTION_BROADCAST =
            new MessageKey("command.description.broadcast");
    public static final MessageKey COMMAND_DESCRIPTION_STATUS =
            new MessageKey("command.description.status");
    public static final MessageKey COMMAND_DESCRIPTION_RELOAD =
            new MessageKey("command.description.reload");
    public static final MessageKey LOCKDOWN_STARTED = new MessageKey("lockdown.started");
    public static final MessageKey LOCKDOWN_ENDED = new MessageKey("lockdown.ended");
    public static final MessageKey LOCKDOWN_NOT_FOUND = new MessageKey("lockdown.not_found");
    public static final MessageKey LOCKDOWN_STATUS_NONE = new MessageKey("lockdown.status.none");
    public static final MessageKey LOCKDOWN_STATUS_HEADER =
            new MessageKey("lockdown.status.header");
    public static final MessageKey LOCKDOWN_STATUS_ENTRY = new MessageKey("lockdown.status.entry");
    public static final MessageKey ROLLBACK_CONFIRM = new MessageKey("rollback.confirm");
    public static final MessageKey ROLLBACK_DONE = new MessageKey("rollback.done");
    public static final MessageKey ROLLBACK_NONE = new MessageKey("rollback.none");
    public static final MessageKey ROLLBACK_FAILURE_REASON_REQUIRED =
            new MessageKey("rollback.failure.reason_required");
    public static final MessageKey ROLLBACK_FAILURE_FORBIDDEN =
            new MessageKey("rollback.failure.forbidden");
    public static final MessageKey PRUNE_CONFIRM = new MessageKey("prune.confirm");
    public static final MessageKey PRUNE_DONE = new MessageKey("prune.done");
    public static final MessageKey PRUNE_NONE = new MessageKey("prune.none");
    public static final MessageKey BROADCAST_FORMAT = new MessageKey("broadcast.format");
    public static final MessageKey STATUS_BODY = new MessageKey("status.body");
    public static final MessageKey RELOAD_DONE = new MessageKey("reload.done");
    public static final MessageKey RELOAD_RESTART = new MessageKey("reload.restart");
    public static final MessageKey RELOAD_ERROR = new MessageKey("reload.error");
    public static final MessageKey RELOAD_FAILED = new MessageKey("reload.failed");

    public static final MessageKey COMMAND_DESCRIPTION_BANWAVE =
            new MessageKey("command.description.banwave");
    public static final MessageKey BANWAVE_CREATED = new MessageKey("banwave.created");
    public static final MessageKey BANWAVE_ADDED = new MessageKey("banwave.added");
    public static final MessageKey BANWAVE_REMOVED = new MessageKey("banwave.removed");
    public static final MessageKey BANWAVE_LIST_EMPTY = new MessageKey("banwave.list.empty");
    public static final MessageKey BANWAVE_LIST_HEADER = new MessageKey("banwave.list.header");
    public static final MessageKey BANWAVE_LIST_ENTRY = new MessageKey("banwave.list.entry");
    public static final MessageKey BANWAVE_LIST_SCHEDULED =
            new MessageKey("banwave.list.scheduled");
    public static final MessageKey BANWAVE_INFO_ENTRY = new MessageKey("banwave.info.entry");
    public static final MessageKey BANWAVE_RESULT_PENDING =
            new MessageKey("banwave.result.pending");
    public static final MessageKey BANWAVE_RESULT_DONE = new MessageKey("banwave.result.done");
    public static final MessageKey BANWAVE_RESULT_FAILED = new MessageKey("banwave.result.failed");
    public static final MessageKey BANWAVE_SCHEDULED = new MessageKey("banwave.scheduled");
    public static final MessageKey BANWAVE_UNSCHEDULED = new MessageKey("banwave.unscheduled");
    public static final MessageKey BANWAVE_CONFIRM = new MessageKey("banwave.confirm");
    public static final MessageKey BANWAVE_RELEASED = new MessageKey("banwave.released");
    public static final MessageKey BANWAVE_CANCELLED = new MessageKey("banwave.cancelled");
    public static final MessageKey BANWAVE_FAILURE_NOT_FOUND =
            new MessageKey("banwave.failure.not_found");
    public static final MessageKey BANWAVE_FAILURE_INVALID =
            new MessageKey("banwave.failure.invalid");
    public static final MessageKey BANWAVE_FAILURE_CONFLICT =
            new MessageKey("banwave.failure.conflict");
    public static final MessageKey BANWAVE_FAILURE_FORBIDDEN =
            new MessageKey("banwave.failure.forbidden");

    public static final MessageKey NOTIFY_APPEAL = new MessageKey("notify.appeal");
    public static final MessageKey APPEAL_DECIDED_ACCEPTED =
            new MessageKey("appeal.decided.accepted");
    public static final MessageKey APPEAL_DECIDED_REJECTED =
            new MessageKey("appeal.decided.rejected");
    public static final MessageKey APPEAL_SUBMITTED = new MessageKey("appeal.submitted");
    public static final MessageKey APPEAL_FAILURE_NOT_YOURS =
            new MessageKey("appeal.failure.not_yours");
    public static final MessageKey APPEAL_FAILURE_OPEN = new MessageKey("appeal.failure.open");
    public static final MessageKey APPEAL_FAILURE_INVALID =
            new MessageKey("appeal.failure.invalid");
    public static final MessageKey APPEAL_FAILURE_NOT_FOUND =
            new MessageKey("appeal.failure.not_found");
    public static final MessageKey APPEAL_FAILURE_CONFLICT =
            new MessageKey("appeal.failure.conflict");
    public static final MessageKey APPEAL_FAILURE_FORBIDDEN =
            new MessageKey("appeal.failure.forbidden");
    public static final MessageKey APPEALS_LIST_EMPTY = new MessageKey("appeals.list.empty");
    public static final MessageKey APPEALS_LIST_HEADER = new MessageKey("appeals.list.header");
    public static final MessageKey APPEALS_LIST_ENTRY = new MessageKey("appeals.list.entry");
    public static final MessageKey APPEALS_DECIDED = new MessageKey("appeals.decided");
    public static final MessageKey COMMAND_DESCRIPTION_APPEAL =
            new MessageKey("command.description.appeal");
    public static final MessageKey COMMAND_DESCRIPTION_APPEALS =
            new MessageKey("command.description.appeals");

    public static final MessageKey COMMAND_DESCRIPTION_GDPR =
            new MessageKey("command.description.gdpr");
    public static final MessageKey GDPR_EXPORTED = new MessageKey("gdpr.exported");
    public static final MessageKey GDPR_ERASE_CONFIRM = new MessageKey("gdpr.erase.confirm");
    public static final MessageKey GDPR_ERASED = new MessageKey("gdpr.erased");
    public static final MessageKey GDPR_FAILURE_FORBIDDEN =
            new MessageKey("gdpr.failure.forbidden");

    private static final List<MessageKey> ALL = Arrays.stream(MessageKey.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers())
                    && field.getType() == MessageKey.class)
            .map(MessageKey::read)
            .toList();

    public static List<MessageKey> all() {
        return ALL;
    }

    private static MessageKey read(final Field field) {
        try {
            return (MessageKey) field.get(null);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
