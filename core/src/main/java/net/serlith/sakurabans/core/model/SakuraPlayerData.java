package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.data.AuditRecord;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.data.PlayerDataResult;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.Punishment;
import org.jspecify.annotations.Nullable;

public final class SakuraPlayerData {

    private SakuraPlayerData() {
    }

    public record Export(UUID uuid, Instant exportedAt, @Nullable PlayerProfileView profileOrNull,
            List<NameRecord> names, List<AddressRecord> addresses, List<Punishment> received,
            List<Punishment> issued, List<AllowEntry> allowEntries,
            List<AuditRecord> auditEntries) implements PlayerDataExport {

        public Export {
            names = List.copyOf(names);
            addresses = List.copyOf(addresses);
            received = List.copyOf(received);
            issued = List.copyOf(issued);
            allowEntries = List.copyOf(allowEntries);
            auditEntries = List.copyOf(auditEntries);
        }

        @Override
        public Optional<PlayerProfileView> profile() {
            return Optional.ofNullable(this.profileOrNull);
        }
    }

    public record Success<T>(T value) implements PlayerDataResult.Success<T> {
    }

    public record Forbidden<T>() implements PlayerDataResult.Forbidden<T> {
    }

    public record Audit(Instant at, Actor actor, String action, String subject,
            @Nullable String oldValueOrNull, @Nullable String newValueOrNull)
            implements AuditRecord {

        public static List<AuditRecord> all(final List<AuditEntry> entries) {
            return entries.stream().<AuditRecord>map(entry -> new Audit(entry.at(), entry.actor(),
                    entry.action().name(), entry.subjectType() + " " + entry.subjectId(),
                    entry.oldValue(), entry.newValue())).toList();
        }

        @Override
        public Optional<String> oldValue() {
            return Optional.ofNullable(this.oldValueOrNull);
        }

        @Override
        public Optional<String> newValue() {
            return Optional.ofNullable(this.newValueOrNull);
        }
    }

    public record Erase(UUID uuid, long namesRemoved, long addressesRemoved,
            long allowedAddressesRemoved, long victimAddressesCleared, long evidenceRemoved,
            long punishmentsKept, long namesRedacted) implements EraseResult {

        public String summary() {
            return "names=" + this.namesRemoved + ", addresses=" + this.addressesRemoved
                    + ", allowed_addresses=" + this.allowedAddressesRemoved
                    + ", victim_addresses_cleared=" + this.victimAddressesCleared
                    + ", evidence=" + this.evidenceRemoved
                    + ", punishments_kept=" + this.punishmentsKept
                    + ", names_redacted=" + this.namesRedacted;
        }
    }

}
