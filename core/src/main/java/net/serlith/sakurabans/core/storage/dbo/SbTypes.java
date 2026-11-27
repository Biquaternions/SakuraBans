package net.serlith.sakurabans.core.storage.dbo;

import static java.util.Map.entry;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.storage.converter.DurationMillisConverter;
import net.serlith.sakurabans.core.storage.converter.EnumCode;
import net.serlith.sakurabans.core.storage.converter.EnumCodeConverter;
import net.serlith.sakurabans.core.storage.converter.FlagConverter;
import net.serlith.sakurabans.core.storage.converter.InetAddressBytesConverter;
import net.serlith.sakurabans.core.storage.converter.InstantMillisConverter;
import net.serlith.sakurabans.core.storage.converter.UuidBytesConverter;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

public final class SbTypes {

    public static final EnumCode<PunishmentType> PUNISHMENT_TYPE_CODES =
            EnumCode.of(PunishmentType.class, Map.of(
                    PunishmentType.BAN, 0,
                    PunishmentType.MUTE, 1,
                    PunishmentType.WARN, 2,
                    PunishmentType.KICK, 3));

    public static final EnumCode<TemplateKind> TEMPLATE_KIND_CODES =
            EnumCode.of(TemplateKind.class, Map.of(
                    TemplateKind.BAN, 1,
                    TemplateKind.MUTE, 2,
                    TemplateKind.WARN, 3,
                    TemplateKind.KICK, 4,
                    TemplateKind.LADDER, 5));

    public static final EnumCode<DurationMode> DURATION_MODE_CODES =
            EnumCode.of(DurationMode.class, Map.of(
                    DurationMode.OPEN, 0,
                    DurationMode.PERMANENT, 1,
                    DurationMode.FIXED, 2));

    public static final EnumCode<TemplateDisplay> TEMPLATE_DISPLAY_CODES =
            EnumCode.of(TemplateDisplay.class, Map.of(
                    TemplateDisplay.CHAT, 0,
                    TemplateDisplay.DIALOG, 1));

    public static final EnumCode<VictimKind> VICTIM_KIND_CODES = EnumCode.of(VictimKind.class,
            Map.of(
                    VictimKind.PLAYER, 0,
                    VictimKind.ADDRESS, 1,
                    VictimKind.COMPOSITE, 2,
                    VictimKind.ADDRESS_RANGE, 3));

    public static final EnumCode<ScopeKind> SCOPE_KIND_CODES = EnumCode.of(ScopeKind.class,
            Map.of(
                    ScopeKind.GLOBAL, 0,
                    ScopeKind.SERVER, 1,
                    ScopeKind.CATEGORY, 2));

    public static final EnumCode<OperatorKind> OPERATOR_KIND_CODES =
            EnumCode.of(OperatorKind.class, Map.of(
                    OperatorKind.CONSOLE, 0,
                    OperatorKind.PLAYER, 1,
                    OperatorKind.CUSTOM, 2));

    public static final EnumCode<Visibility> VISIBILITY_CODES = EnumCode.of(Visibility.class,
            Map.of(
                    Visibility.NORMAL, 0,
                    Visibility.SILENT, 1,
                    Visibility.HIDDEN, 2,
                    Visibility.QUIET, 3));

    public static final EnumCode<EndKind> END_KIND_CODES = EnumCode.of(EndKind.class, Map.of(
            EndKind.REVOKED, 0,
            EndKind.EXPIRED, 1,
            EndKind.SUPERSEDED, 2,
            EndKind.ROLLED_BACK, 3,
            EndKind.PRUNED, 4,
            EndKind.VOIDED, 5));

    public static final EnumCode<AuditAction> AUDIT_ACTION_CODES =
            EnumCode.of(AuditAction.class, Map.ofEntries(
                    entry(AuditAction.CREATE, 0),
                    entry(AuditAction.REVOKE, 1),
                    entry(AuditAction.EDIT_REASON, 2),
                    entry(AuditAction.EDIT_EXPIRY, 3),
                    entry(AuditAction.EDIT_SCOPE, 4),
                    entry(AuditAction.VOID, 5),
                    entry(AuditAction.EXPUNGE, 6),
                    entry(AuditAction.ROLLBACK, 7),
                    entry(AuditAction.EVIDENCE_ATTACH, 8),
                    entry(AuditAction.EVIDENCE_DETACH, 9),
                    entry(AuditAction.UPDATE, 10),
                    entry(AuditAction.DELETE, 11),
                    entry(AuditAction.FORGET_ADDRESS, 16),
                    entry(AuditAction.EXPORT, 17),
                    entry(AuditAction.ERASE, 18),
                    entry(AuditAction.ALLOW, 19),
                    entry(AuditAction.DISALLOW, 20),
                    entry(AuditAction.IMPORT, 21),
                    entry(AuditAction.RESET, 22),
                    entry(AuditAction.APPLY, 23)));

    public static final EnumCode<SubjectType> SUBJECT_TYPE_CODES =
            EnumCode.of(SubjectType.class, Map.of(
                    SubjectType.PUNISHMENT, 0,
                    SubjectType.TEMPLATE, 2,
                    SubjectType.SETTINGS, 3,
                    SubjectType.MESSAGE, 4,
                    SubjectType.PLAYER, 7,
                    SubjectType.ALLOW_LIST, 8,
                    SubjectType.IMPORT, 9,
                    SubjectType.ADDRESS_HASH, 10,
                    SubjectType.VICTIM, 11));

    public static final DataType<UUID> UUID =
            SQLDataType.BINARY(16).asConvertedDataType(new UuidBytesConverter());
    public static final DataType<InetAddress> INET =
            SQLDataType.VARBINARY(16).asConvertedDataType(new InetAddressBytesConverter());
    public static final DataType<Instant> INSTANT =
            SQLDataType.BIGINT.asConvertedDataType(new InstantMillisConverter());
    public static final DataType<Duration> DURATION =
            SQLDataType.BIGINT.asConvertedDataType(new DurationMillisConverter());
    public static final DataType<Boolean> FLAG =
            SQLDataType.SMALLINT.asConvertedDataType(new FlagConverter());

    public static final DataType<PunishmentType> PUNISHMENT_TYPE = enumType(PUNISHMENT_TYPE_CODES);
    public static final DataType<TemplateKind> TEMPLATE_KIND = enumType(TEMPLATE_KIND_CODES);
    public static final DataType<DurationMode> DURATION_MODE = enumType(DURATION_MODE_CODES);
    public static final DataType<TemplateDisplay> TEMPLATE_DISPLAY =
            enumType(TEMPLATE_DISPLAY_CODES);
    public static final DataType<VictimKind> VICTIM_KIND = enumType(VICTIM_KIND_CODES);
    public static final DataType<ScopeKind> SCOPE_KIND = enumType(SCOPE_KIND_CODES);
    public static final DataType<OperatorKind> OPERATOR_KIND = enumType(OPERATOR_KIND_CODES);
    public static final DataType<Visibility> VISIBILITY = enumType(VISIBILITY_CODES);
    public static final DataType<EndKind> END_KIND = enumType(END_KIND_CODES);
    public static final DataType<AuditAction> AUDIT_ACTION = enumType(AUDIT_ACTION_CODES);
    public static final DataType<SubjectType> SUBJECT_TYPE = enumType(SUBJECT_TYPE_CODES);

    private SbTypes() {
    }

    private static <E extends Enum<E>> DataType<E> enumType(final EnumCode<E> codes) {
        return SQLDataType.SMALLINT.asConvertedDataType(new EnumCodeConverter<>(codes));
    }
}
