package net.serlith.sakurabans.core.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.PunishmentVoid;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import org.jspecify.annotations.Nullable;

public final class ValueCodec {

    private static final int CONSOLE = 0;
    private static final int PLAYER = 1;
    private static final int CUSTOM = 2;

    private ValueCodec() {
    }

    public static void writeActor(final DataOutputStream out,
            final Actor actor) throws IOException {
        writeOperator(out, actor.operator());
        out.writeUTF(actor.source());
    }

    public static Actor readActor(final DataInputStream in) throws IOException {
        final Operator operator = readOperator(in);
        return Actor.of(operator, in.readUTF());
    }

    public static void writeOperator(final DataOutputStream out, final Operator operator)
            throws IOException {
        switch (operator) {
            case ConsoleOperator console -> out.writeByte(CONSOLE);
            case PlayerOperator player -> {
                out.writeByte(PLAYER);
                writeUuid(out, player.uuid());
                out.writeUTF(player.name());
            }
            case CustomOperator custom -> {
                out.writeByte(CUSTOM);
                out.writeUTF(custom.name());
                out.writeBoolean(custom.uuid().isPresent());
                if (custom.uuid().isPresent()) {
                    writeUuid(out, custom.uuid().get());
                }
            }
        }
    }

    public static Operator readOperator(final DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case CONSOLE -> ConsoleOperator.INSTANCE;
            case PLAYER -> PlayerOperator.of(readUuid(in), in.readUTF());
            case CUSTOM -> {
                final String name = in.readUTF();
                yield CustomOperator.of(name, in.readBoolean() ? readUuid(in) : null);
            }
            default -> throw new IllegalArgumentException("unknown operator kind");
        };
    }

    public static void writeScope(final DataOutputStream out,
            final Scope scope) throws IOException {
        switch (scope) {
            case GlobalScope global -> out.writeByte(0);
            case ServerScope server -> {
                out.writeByte(1);
                out.writeUTF(server.server());
            }
            case CategoryScope category -> {
                out.writeByte(2);
                out.writeUTF(category.category());
            }
        }
    }

    public static Scope readScope(final DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> GlobalScope.INSTANCE;
            case 1 -> ServerScope.of(in.readUTF());
            case 2 -> CategoryScope.of(in.readUTF());
            default -> throw new IllegalArgumentException("unknown scope kind");
        };
    }

    public static void writeVictim(final DataOutputStream out,
            final Victim victim) throws IOException {
        switch (victim) {
            case PlayerVictim player -> {
                out.writeByte(0);
                writeUuid(out, player.uuid());
            }
            case AddressVictim address -> {
                out.writeByte(1);
                writeAddress(out, address.address());
            }
            case CompositeVictim composite -> {
                out.writeByte(2);
                writeUuid(out, composite.uuid());
                writeAddress(out, composite.address());
            }
            case AddressRangeVictim range -> {
                out.writeByte(3);
                writeAddress(out, range.network());
                out.writeByte(range.prefixLength());
            }
        }
    }

    public static Victim readVictim(final DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> PlayerVictim.of(readUuid(in));
            case 1 -> AddressVictim.of(readAddress(in));
            case 2 -> CompositeVictim.of(readUuid(in), readAddress(in));
            case 3 -> AddressRangeVictim.of(readAddress(in), in.readUnsignedByte());
            default -> throw new IllegalArgumentException("unknown victim kind");
        };
    }

    public static void writeEdit(final DataOutputStream out, final PunishmentEdit edit)
            throws IOException {
        switch (edit) {
            case PunishmentEdit.NewReason reason -> {
                out.writeByte(0);
                out.writeUTF(reason.reason());
            }
            case PunishmentEdit.NewExpiry expiry -> {
                out.writeByte(1);
                writeInstant(out, expiry.expiresAt().orElse(null));
            }
            case PunishmentEdit.NewScope scope -> {
                out.writeByte(2);
                writeScope(out, scope.scope());
            }
        }
    }

    public static PunishmentEdit readEdit(final DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> PunishmentEdit.NewReason.of(in.readUTF());
            case 1 -> PunishmentEdit.NewExpiry.of(readInstant(in));
            case 2 -> PunishmentEdit.NewScope.of(readScope(in));
            default -> throw new IllegalArgumentException("unknown edit kind");
        };
    }

    public static void writeEvidence(final DataOutputStream out, final Evidence evidence)
            throws IOException {
        out.writeUTF(evidence.ref().uri());
        writeActor(out, evidence.addedBy());
        out.writeLong(evidence.addedAt().toEpochMilli());
    }

    public static Evidence readEvidence(final DataInputStream in) throws IOException {
        return new SakuraEvidence(EvidenceRef.of(in.readUTF()), readActor(in),
                Instant.ofEpochMilli(in.readLong()));
    }

    /** Everything but the evidence, which a deleted punishment no longer needs. */
    public static void writePunishment(final DataOutputStream out, final Punishment punishment)
            throws IOException {
        out.writeLong(punishment.id());
        out.writeUTF(punishment.displayId());
        out.writeUTF(punishment.type().name());
        out.writeUTF(punishment.kind());
        out.writeBoolean(punishment.shadow());
        writeText(out, punishment.strictness().map(Enum::name).orElse(null));
        writeVictim(out, punishment.victim());
        writeOperator(out, punishment.operator());
        out.writeUTF(punishment.source());
        writeScope(out, punishment.scope());
        out.writeUTF(punishment.reason());
        writeInstant(out, punishment.createdAt());
        writeInstant(out, punishment.expiresAt().orElse(null));
        writeText(out, punishment.originServer().orElse(null));
        out.writeUTF(punishment.visibility().name());
        final TemplateRef template = punishment.template().orElse(null);
        writeText(out, template == null ? null : template.name());
        out.writeInt(template == null ? 0 : template.step());
        final PunishmentEnd end = punishment.end().orElse(null);
        out.writeBoolean(end != null);
        if (end != null) {
            out.writeUTF(end.kind().name());
            writeInstant(out, end.at());
            out.writeBoolean(end.actor().isPresent());
            if (end.actor().isPresent()) {
                writeActor(out, end.actor().get());
            }
            writeText(out, end.reason().orElse(null));
        }
        final PunishmentVoid voiding = punishment.voiding().orElse(null);
        out.writeBoolean(voiding != null);
        if (voiding != null) {
            writeInstant(out, voiding.at());
            writeActor(out, voiding.actor());
            writeText(out, voiding.reason().orElse(null));
        }
        out.writeLong(punishment.version());
    }

    public static Punishment readPunishment(final DataInputStream in) throws IOException {
        final long id = in.readLong();
        final String displayId = in.readUTF();
        final PunishmentType type = PunishmentType.valueOf(in.readUTF());
        final String kind = in.readUTF();
        final boolean shadow = in.readBoolean();
        final String strictness = readText(in);
        final Victim victim = readVictim(in);
        final Operator operator = readOperator(in);
        final String source = in.readUTF();
        final Scope scope = readScope(in);
        final String reason = in.readUTF();
        final Instant createdAt = readInstant(in);
        final Instant expiresAt = readInstant(in);
        final String origin = readText(in);
        final Visibility visibility = Visibility.valueOf(in.readUTF());
        final String template = readText(in);
        final int step = in.readInt();
        PunishmentEnd end = null;
        if (in.readBoolean()) {
            final EndKind endKind = EndKind.valueOf(in.readUTF());
            final Instant at = readInstant(in);
            final Actor actor = in.readBoolean() ? readActor(in) : null;
            end = new SakuraPunishmentEnd(endKind, nonNull(at), actor, readText(in));
        }
        PunishmentVoid voiding = null;
        if (in.readBoolean()) {
            final Instant at = readInstant(in);
            voiding = new SakuraPunishmentVoid(nonNull(at), readActor(in), readText(in));
        }
        return new SakuraPunishment(id, displayId, type, kind, shadow,
                strictness == null ? null : AddressStrictness.valueOf(strictness), victim,
                operator, source, scope, reason, nonNull(createdAt), expiresAt, origin,
                visibility, template == null ? null : new SakuraTemplateRef(template, step), end,
                voiding, List.of(), in.readLong());
    }

    private static void writeText(final DataOutputStream out, final @Nullable String text)
            throws IOException {
        out.writeBoolean(text != null);
        if (text != null) {
            out.writeUTF(text);
        }
    }

    private static @Nullable String readText(final DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeInstant(final DataOutputStream out, final @Nullable Instant at)
            throws IOException {
        out.writeLong(at == null ? Long.MIN_VALUE : at.toEpochMilli());
    }

    private static @Nullable Instant readInstant(final DataInputStream in) throws IOException {
        final long millis = in.readLong();
        return millis == Long.MIN_VALUE ? null : Instant.ofEpochMilli(millis);
    }

    private static Instant nonNull(final @Nullable Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("a required instant is missing");
        }
        return at;
    }

    private static void writeUuid(final DataOutputStream out, final UUID uuid)
            throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(final DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeAddress(final DataOutputStream out, final InetAddress address)
            throws IOException {
        final byte[] bytes = address.getAddress();
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private static InetAddress readAddress(final DataInputStream in) throws IOException {
        final byte[] bytes = new byte[in.readUnsignedByte()];
        in.readFully(bytes);
        return InetAddress.getByAddress(bytes);
    }

    /** A draft with its options, as base64 text (ban wave entries, S37c). */
    public static String encodeDraft(final DraftPunishment draft, final PunishOptions options) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(1);
            out.writeUTF(draft.type().name());
            writeVictim(out, draft.victim());
            writeActor(out, draft.actor());
            writeText(out, draft.kind().orElse(null));
            out.writeBoolean(draft.scope().isPresent());
            if (draft.scope().isPresent()) {
                writeScope(out, draft.scope().get());
            }
            writeText(out, draft.reason().orElse(null));
            out.writeLong(draft.duration().map(Duration::toMillis).orElse(-1L));
            writeText(out, draft.template().orElse(null));
            out.writeInt(draft.evidence().size());
            for (final EvidenceRef ref : draft.evidence()) {
                out.writeUTF(ref.uri());
            }
            out.writeUTF(options.visibility().name());
            out.writeUTF(options.override().name());
            out.writeUTF(options.enforcement().name());
            out.writeBoolean(options.bypassLimits());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public record StoredDraft(DraftPunishment draft, PunishOptions options) {
    }

    public static StoredDraft decodeDraft(final String text) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(text)))) {
            if (in.readUnsignedByte() != 1) {
                throw new IllegalArgumentException("unknown draft format");
            }
            final DraftPunishment.Builder draft = DraftPunishment.builder(
                    PunishmentType.valueOf(in.readUTF()), readVictim(in), readActor(in));
            draft.kind(readText(in));
            if (in.readBoolean()) {
                draft.scope(readScope(in));
            }
            draft.reason(readText(in));
            final long duration = in.readLong();
            if (duration >= 0) {
                draft.duration(Duration.ofMillis(duration));
            }
            draft.template(readText(in));
            final int evidence = in.readInt();
            final List<EvidenceRef> refs = new ArrayList<>();
            for (int i = 0; i < evidence; i++) {
                refs.add(EvidenceRef.of(in.readUTF()));
            }
            draft.evidence(refs);
            final PunishOptions options = PunishOptions.builder()
                    .visibility(Visibility.valueOf(in.readUTF()))
                    .override(OverridePolicy.valueOf(in.readUTF()))
                    .enforcement(Enforcement.valueOf(in.readUTF()))
                    .bypassLimits(in.readBoolean())
                    .build();
            return new StoredDraft(draft.build(), options);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
