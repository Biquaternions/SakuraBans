package net.serlith.sakurabans.core.data;

import java.net.InetAddress;
import java.time.Duration;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.event.PlayerDataErasedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.CustomOperator;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.audit.SakuraAuditService;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Exporting and erasing a player's data by the rules of D22 (plan S37e). */
class PlayerDataTest extends ServiceTest {

    private static final Actor STEVE_STAFF = Actor.of(PlayerOperator.of(STEVE, "Steve"),
            "in-game");

    private Punishment punish(final PunishmentType type, final Victim victim, final Actor by) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type, victim,
                by).duration(Duration.ofDays(30)).build(), PunishOptions.defaults()).join())
                .punishment();
    }

    private static <T> T ok(final AdminResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<T>) result).value();
    }

    @Test
    void anExportHasEverythingAboutTheAccount() throws Exception {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, "lobby").join();
        final Punishment ban = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR);
        final Punishment issued = this.punish(PunishmentType.WARN, PlayerVictim.of(ALEX),
                STEVE_STAFF);
        this.appeals.submit(ban.id(), STEVE, "please").join();
        this.allowList.allow(STEVE, MODERATOR, "streamer").join();

        final PlayerDataExport export = ok(this.playerData.export(STEVE, MODERATOR).join());
        Assertions.assertThat(export.profile()).hasValueSatisfying(profile ->
                Assertions.assertThat(profile.name()).isEqualTo("Steve"));
        Assertions.assertThat(export.names()).hasSize(1);
        Assertions.assertThat(export.addresses()).singleElement().satisfies(address ->
                Assertions.assertThat(address.address()).isEqualTo(this.home));
        Assertions.assertThat(export.received()).extracting(Punishment::id)
                .containsExactly(ban.id());
        Assertions.assertThat(export.issued()).extracting(Punishment::id)
                .containsExactly(issued.id());
        Assertions.assertThat(export.appeals()).hasSize(1);
        Assertions.assertThat(export.allowEntries()).hasSize(1);
        Assertions.assertThat(export.auditEntries()).isNotEmpty();
    }

    @Test
    void erasingKeepsActivePunishmentsWithoutPersonalData() throws Exception {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, "lobby").join();
        final InetAddress other = InetAddress.getByName("198.51.100.4");
        // Active composite ban: stays, but as a player ban.
        final Punishment ban = this.punish(PunishmentType.BAN,
                CompositeVictim.of(STEVE, this.home), MODERATOR);
        this.service.attachEvidence(ban.id(), EvidenceRef.of("https://example.org/e"),
                MODERATOR).join();
        // Ended mute: deleted.
        final Punishment mute = this.punish(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                MODERATOR);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(mute.id()),
                MODERATOR).build()).join();
        // An address ban and what Steve issued.
        final Punishment address = this.punish(PunishmentType.BAN, AddressVictim.of(other),
                MODERATOR);
        final Punishment issued = this.punish(PunishmentType.WARN, PlayerVictim.of(ALEX),
                STEVE_STAFF);
        this.appeals.submit(ban.id(), STEVE, "sorry").join();
        this.names.put(STEVE, "Steve");

        final EraseResult erased = ok(this.playerData.erase(STEVE, MODERATOR).join());
        Assertions.assertThat(erased.namesDeleted()).isEqualTo(1);
        Assertions.assertThat(erased.addressesDeleted()).isEqualTo(1);
        Assertions.assertThat(erased.punishmentsExpunged()).isEqualTo(1);
        Assertions.assertThat(erased.punishmentsAnonymised()).isEqualTo(1);
        Assertions.assertThat(erased.issuedAnonymised()).isEqualTo(1);
        Assertions.assertThat(erased.otherDeleted()).isEqualTo(1);

        Assertions.assertThat(this.service.byId(mute.id()).join()).isEmpty();
        Assertions.assertThat(this.service.byId(ban.id()).join()).hasValueSatisfying(kept -> {
            Assertions.assertThat(kept.victim()).isEqualTo(PlayerVictim.of(STEVE));
            Assertions.assertThat(kept.evidence()).isEmpty();
            Assertions.assertThat(kept.isActiveAt(this.now.get())).isTrue();
        });
        Assertions.assertThat(this.service.activeBan(STEVE, null,
                GlobalScope.INSTANCE).join()).isPresent();
        Assertions.assertThat(this.service.byId(address.id()).join()).isPresent();
        Assertions.assertThat(this.service.byId(issued.id()).join().orElseThrow().operator())
                .isEqualTo(CustomOperator.of("erased", null));
        Assertions.assertThat(this.players.profile(STEVE).join()).isEmpty();
        Assertions.assertThat(this.names.name(STEVE)).isEmpty();

        // No trace of his address or UUID in the audit values (only the erase names him).
        final String audit = String.join("\n", new SakuraAuditService(
                new AuditRepository(this.storage)).query(AuditQuery.builder().pageSize(500)
                        .build()).join().items().stream()
                .map(entry -> entry.oldValue().orElse("") + " " + entry.newValue().orElse(""))
                .toList());
        Assertions.assertThat(audit).doesNotContain(this.home.getHostAddress())
                .doesNotContain(STEVE.toString());
        Assertions.assertThat(this.published).anySatisfy(packet -> Assertions.assertThat(packet)
                .isInstanceOf(SyncPacket.PlayerDataErasedPacket.class));
        Assertions.assertThat(this.events(PlayerDataErasedEvent.class)).hasSize(1);
    }

    @Test
    void compositeBansWhosePlayerSlotIsTakenEnd() throws Exception {
        final Punishment player = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR);
        final Punishment composite = this.punish(PunishmentType.BAN, CompositeVictim.of(STEVE,
                this.home), MODERATOR);

        ok(this.playerData.erase(STEVE, MODERATOR).join());

        Assertions.assertThat(this.service.byId(player.id()).join().orElseThrow()
                .isActiveAt(this.now.get())).isTrue();
        Assertions.assertThat(this.service.byId(composite.id()).join().orElseThrow().victim())
                .isEqualTo(PlayerVictim.of(STEVE));
        Assertions.assertThat(this.service.byId(composite.id()).join().orElseThrow().end())
                .isPresent();
    }
}
