package net.serlith.sakurabans.core.storage.dbo;

import static java.util.Map.entry;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.core.platform.PlatformType;
import net.serlith.sakurabans.core.storage.converter.DurationMillisConverter;
import net.serlith.sakurabans.core.storage.converter.EnumCode;
import net.serlith.sakurabans.core.storage.converter.EnumCodeConverter;
import net.serlith.sakurabans.core.storage.converter.FlagConverter;
import net.serlith.sakurabans.core.storage.converter.InetAddressBytesConverter;
import net.serlith.sakurabans.core.storage.converter.InstantMillisConverter;
import net.serlith.sakurabans.core.storage.converter.UuidBytesConverter;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

/**
 * Converted column types (DESIGN 7.1). The enum codes are part of the schema: never change an
 * existing code, only add new ones.
 */
public final class SbTypes {

    public static final EnumCode<PunishmentType> PUNISHMENT_TYPE_CODES =
            EnumCode.of(PunishmentType.class, Map.of(
                    PunishmentType.BAN, 0,
                    PunishmentType.MUTE, 1,
                    PunishmentType.WARN, 2,
                    PunishmentType.KICK, 3));

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
                    Visibility.HIDDEN, 2));

    public static final EnumCode<EndKind> END_KIND_CODES = EnumCode.of(EndKind.class, Map.of(
            EndKind.REVOKED, 0,
            EndKind.EXPIRED, 1,
            EndKind.SUPERSEDED, 2,
            EndKind.ROLLED_BACK, 3,
            EndKind.PRUNED, 4,
            EndKind.VOIDED, 5));

    public static final EnumCode<AddressStrictness> STRICTNESS_CODES =
            EnumCode.of(AddressStrictness.class, Map.of(
                    AddressStrictness.LENIENT, 0,
                    AddressStrictness.NORMAL, 1,
                    AddressStrictness.STERN, 2,
                    AddressStrictness.STRICT, 3));

    public static final EnumCode<AddressMode> ADDRESS_MODE_CODES =
            EnumCode.of(AddressMode.class, Map.of(
                    AddressMode.AS_GIVEN, 0,
                    AddressMode.ADDRESS, 1,
                    AddressMode.COMPOSITE, 2));

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
                    entry(AuditAction.SCHEDULE, 12),
                    entry(AuditAction.RELEASE, 13),
                    entry(AuditAction.CANCEL, 14),
                    entry(AuditAction.DECIDE, 15),
                    entry(AuditAction.FORGET_ADDRESS, 16),
                    entry(AuditAction.EXPORT, 17),
                    entry(AuditAction.ERASE, 18),
                    entry(AuditAction.ALLOW, 19),
                    entry(AuditAction.DISALLOW, 20),
                    entry(AuditAction.IMPORT, 21)));

    public static final EnumCode<SubjectType> SUBJECT_TYPE_CODES =
            EnumCode.of(SubjectType.class, Map.of(
                    SubjectType.PUNISHMENT, 0,
                    SubjectType.KIND, 1,
                    SubjectType.TEMPLATE, 2,
                    SubjectType.SETTINGS, 3,
                    SubjectType.MESSAGE, 4,
                    SubjectType.BAN_WAVE, 5,
                    SubjectType.APPEAL, 6,
                    SubjectType.PLAYER, 7,
                    SubjectType.ALLOW_LIST, 8,
                    SubjectType.IMPORT, 9));

    public static final EnumCode<BanWave.Status> WAVE_STATUS_CODES =
            EnumCode.of(BanWave.Status.class, Map.of(
                    BanWave.Status.DRAFT, 0,
                    BanWave.Status.SCHEDULED, 1,
                    BanWave.Status.RELEASING, 2,
                    BanWave.Status.RELEASED, 3,
                    BanWave.Status.CANCELLED, 4));

    public static final EnumCode<Appeal.Status> APPEAL_STATUS_CODES =
            EnumCode.of(Appeal.Status.class, Map.of(
                    Appeal.Status.OPEN, 0,
                    Appeal.Status.ACCEPTED, 1,
                    Appeal.Status.REJECTED, 2));

    public static final EnumCode<AppealOutcome> APPEAL_OUTCOME_CODES =
            EnumCode.of(AppealOutcome.class, Map.of(
                    AppealOutcome.REJECT, 0,
                    AppealOutcome.REVOKE, 1,
                    AppealOutcome.VOID, 2));

    public static final EnumCode<PlatformType> PLATFORM_CODES =
            EnumCode.of(PlatformType.class, Map.of(
                    PlatformType.PAPER, 0,
                    PlatformType.VELOCITY, 1));

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
    public static final DataType<VictimKind> VICTIM_KIND = enumType(VICTIM_KIND_CODES);
    public static final DataType<ScopeKind> SCOPE_KIND = enumType(SCOPE_KIND_CODES);
    public static final DataType<OperatorKind> OPERATOR_KIND = enumType(OPERATOR_KIND_CODES);
    public static final DataType<Visibility> VISIBILITY = enumType(VISIBILITY_CODES);
    public static final DataType<EndKind> END_KIND = enumType(END_KIND_CODES);
    public static final DataType<AddressStrictness> STRICTNESS = enumType(STRICTNESS_CODES);
    public static final DataType<AddressMode> ADDRESS_MODE = enumType(ADDRESS_MODE_CODES);
    public static final DataType<AuditAction> AUDIT_ACTION = enumType(AUDIT_ACTION_CODES);
    public static final DataType<SubjectType> SUBJECT_TYPE = enumType(SUBJECT_TYPE_CODES);
    public static final DataType<BanWave.Status> WAVE_STATUS = enumType(WAVE_STATUS_CODES);
    public static final DataType<Appeal.Status> APPEAL_STATUS = enumType(APPEAL_STATUS_CODES);
    public static final DataType<AppealOutcome> APPEAL_OUTCOME = enumType(APPEAL_OUTCOME_CODES);
    public static final DataType<PlatformType> PLATFORM = enumType(PLATFORM_CODES);

    private SbTypes() {
    }

    private static <E extends Enum<E>> DataType<E> enumType(final EnumCode<E> codes) {
        return SQLDataType.SMALLINT.asConvertedDataType(new EnumCodeConverter<>(codes));
    }
}
