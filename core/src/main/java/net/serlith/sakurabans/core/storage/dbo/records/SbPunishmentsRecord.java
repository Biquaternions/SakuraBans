package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbPunishmentsRecord extends UpdatableRecordImpl<SbPunishmentsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbPunishmentsRecord() {
        super(SakuraTables.PUNISHMENTS);
    }

    public Long id() {
        return this.get(SakuraTables.PUNISHMENTS.ID);
    }

    public SbPunishmentsRecord id(final Long value) {
        this.set(SakuraTables.PUNISHMENTS.ID, value);
        return this;
    }

    public PunishmentType type() {
        return this.get(SakuraTables.PUNISHMENTS.TYPE);
    }

    public SbPunishmentsRecord type(final PunishmentType value) {
        this.set(SakuraTables.PUNISHMENTS.TYPE, value);
        return this;
    }

    public String kind() {
        return this.get(SakuraTables.PUNISHMENTS.KIND);
    }

    public SbPunishmentsRecord kind(final String value) {
        this.set(SakuraTables.PUNISHMENTS.KIND, value);
        return this;
    }

    public Boolean shadow() {
        return this.get(SakuraTables.PUNISHMENTS.SHADOW);
    }

    public SbPunishmentsRecord shadow(final Boolean value) {
        this.set(SakuraTables.PUNISHMENTS.SHADOW, value);
        return this;
    }

    public @Nullable AddressStrictness strictness() {
        return this.get(SakuraTables.PUNISHMENTS.STRICTNESS);
    }

    public SbPunishmentsRecord strictness(final @Nullable AddressStrictness value) {
        this.set(SakuraTables.PUNISHMENTS.STRICTNESS, value);
        return this;
    }

    public Long victimId() {
        return this.get(SakuraTables.PUNISHMENTS.VICTIM_ID);
    }

    public SbPunishmentsRecord victimId(final Long value) {
        this.set(SakuraTables.PUNISHMENTS.VICTIM_ID, value);
        return this;
    }

    public Integer scopeId() {
        return this.get(SakuraTables.PUNISHMENTS.SCOPE_ID);
    }

    public SbPunishmentsRecord scopeId(final Integer value) {
        this.set(SakuraTables.PUNISHMENTS.SCOPE_ID, value);
        return this;
    }

    public String reason() {
        return this.get(SakuraTables.PUNISHMENTS.REASON);
    }

    public SbPunishmentsRecord reason(final String value) {
        this.set(SakuraTables.PUNISHMENTS.REASON, value);
        return this;
    }

    public OperatorKind operatorKind() {
        return this.get(SakuraTables.PUNISHMENTS.OPERATOR_KIND);
    }

    public SbPunishmentsRecord operatorKind(final OperatorKind value) {
        this.set(SakuraTables.PUNISHMENTS.OPERATOR_KIND, value);
        return this;
    }

    public @Nullable UUID operatorUuid() {
        return this.get(SakuraTables.PUNISHMENTS.OPERATOR_UUID);
    }

    public SbPunishmentsRecord operatorUuid(final @Nullable UUID value) {
        this.set(SakuraTables.PUNISHMENTS.OPERATOR_UUID, value);
        return this;
    }

    public String operatorName() {
        return this.get(SakuraTables.PUNISHMENTS.OPERATOR_NAME);
    }

    public SbPunishmentsRecord operatorName(final String value) {
        this.set(SakuraTables.PUNISHMENTS.OPERATOR_NAME, value);
        return this;
    }

    public String source() {
        return this.get(SakuraTables.PUNISHMENTS.SOURCE);
    }

    public SbPunishmentsRecord source(final String value) {
        this.set(SakuraTables.PUNISHMENTS.SOURCE, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.PUNISHMENTS.CREATED_AT);
    }

    public SbPunishmentsRecord createdAt(final Instant value) {
        this.set(SakuraTables.PUNISHMENTS.CREATED_AT, value);
        return this;
    }

    public @Nullable Instant expiresAt() {
        return this.get(SakuraTables.PUNISHMENTS.EXPIRES_AT);
    }

    public SbPunishmentsRecord expiresAt(final @Nullable Instant value) {
        this.set(SakuraTables.PUNISHMENTS.EXPIRES_AT, value);
        return this;
    }

    public @Nullable String originServer() {
        return this.get(SakuraTables.PUNISHMENTS.ORIGIN_SERVER);
    }

    public SbPunishmentsRecord originServer(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.ORIGIN_SERVER, value);
        return this;
    }

    public Visibility visibility() {
        return this.get(SakuraTables.PUNISHMENTS.VISIBILITY);
    }

    public SbPunishmentsRecord visibility(final Visibility value) {
        this.set(SakuraTables.PUNISHMENTS.VISIBILITY, value);
        return this;
    }

    public @Nullable String template() {
        return this.get(SakuraTables.PUNISHMENTS.TEMPLATE);
    }

    public SbPunishmentsRecord template(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.TEMPLATE, value);
        return this;
    }

    public @Nullable Short templateStep() {
        return this.get(SakuraTables.PUNISHMENTS.TEMPLATE_STEP);
    }

    public SbPunishmentsRecord templateStep(final @Nullable Short value) {
        this.set(SakuraTables.PUNISHMENTS.TEMPLATE_STEP, value);
        return this;
    }

    public @Nullable Instant endedAt() {
        return this.get(SakuraTables.PUNISHMENTS.ENDED_AT);
    }

    public SbPunishmentsRecord endedAt(final @Nullable Instant value) {
        this.set(SakuraTables.PUNISHMENTS.ENDED_AT, value);
        return this;
    }

    public @Nullable EndKind endKind() {
        return this.get(SakuraTables.PUNISHMENTS.END_KIND);
    }

    public SbPunishmentsRecord endKind(final @Nullable EndKind value) {
        this.set(SakuraTables.PUNISHMENTS.END_KIND, value);
        return this;
    }

    public @Nullable OperatorKind revokerKind() {
        return this.get(SakuraTables.PUNISHMENTS.REVOKER_KIND);
    }

    public SbPunishmentsRecord revokerKind(final @Nullable OperatorKind value) {
        this.set(SakuraTables.PUNISHMENTS.REVOKER_KIND, value);
        return this;
    }

    public @Nullable UUID revokerUuid() {
        return this.get(SakuraTables.PUNISHMENTS.REVOKER_UUID);
    }

    public SbPunishmentsRecord revokerUuid(final @Nullable UUID value) {
        this.set(SakuraTables.PUNISHMENTS.REVOKER_UUID, value);
        return this;
    }

    public @Nullable String revokerName() {
        return this.get(SakuraTables.PUNISHMENTS.REVOKER_NAME);
    }

    public SbPunishmentsRecord revokerName(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.REVOKER_NAME, value);
        return this;
    }

    public @Nullable String revokerSource() {
        return this.get(SakuraTables.PUNISHMENTS.REVOKER_SOURCE);
    }

    public SbPunishmentsRecord revokerSource(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.REVOKER_SOURCE, value);
        return this;
    }

    public @Nullable String revokeReason() {
        return this.get(SakuraTables.PUNISHMENTS.REVOKE_REASON);
    }

    public SbPunishmentsRecord revokeReason(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.REVOKE_REASON, value);
        return this;
    }

    public @Nullable Instant voidedAt() {
        return this.get(SakuraTables.PUNISHMENTS.VOIDED_AT);
    }

    public SbPunishmentsRecord voidedAt(final @Nullable Instant value) {
        this.set(SakuraTables.PUNISHMENTS.VOIDED_AT, value);
        return this;
    }

    public @Nullable OperatorKind voiderKind() {
        return this.get(SakuraTables.PUNISHMENTS.VOIDER_KIND);
    }

    public SbPunishmentsRecord voiderKind(final @Nullable OperatorKind value) {
        this.set(SakuraTables.PUNISHMENTS.VOIDER_KIND, value);
        return this;
    }

    public @Nullable UUID voiderUuid() {
        return this.get(SakuraTables.PUNISHMENTS.VOIDER_UUID);
    }

    public SbPunishmentsRecord voiderUuid(final @Nullable UUID value) {
        this.set(SakuraTables.PUNISHMENTS.VOIDER_UUID, value);
        return this;
    }

    public @Nullable String voiderName() {
        return this.get(SakuraTables.PUNISHMENTS.VOIDER_NAME);
    }

    public SbPunishmentsRecord voiderName(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.VOIDER_NAME, value);
        return this;
    }

    public @Nullable String voiderSource() {
        return this.get(SakuraTables.PUNISHMENTS.VOIDER_SOURCE);
    }

    public SbPunishmentsRecord voiderSource(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.VOIDER_SOURCE, value);
        return this;
    }

    public @Nullable String voidReason() {
        return this.get(SakuraTables.PUNISHMENTS.VOID_REASON);
    }

    public SbPunishmentsRecord voidReason(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.VOID_REASON, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.PUNISHMENTS.VERSION);
    }

    public SbPunishmentsRecord version(final Long value) {
        this.set(SakuraTables.PUNISHMENTS.VERSION, value);
        return this;
    }

    public @Nullable String externalId() {
        return this.get(SakuraTables.PUNISHMENTS.EXTERNAL_ID);
    }

    public SbPunishmentsRecord externalId(final @Nullable String value) {
        this.set(SakuraTables.PUNISHMENTS.EXTERNAL_ID, value);
        return this;
    }
}
