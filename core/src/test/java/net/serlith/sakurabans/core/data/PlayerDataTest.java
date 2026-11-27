package net.serlith.sakurabans.core.data;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.data.PlayerDataResult;
import net.serlith.sakurabans.api.event.PlayerDataErasedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
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
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Exporting and erasing a player's data. */
class PlayerDataTest extends ServiceTest {

    private static final Actor STEVE_STAFF = Actor.of(PlayerOperator.of(STEVE, "Steve"),
            "in-game");

    private InetAddress other;
    private InetAddress elsewhere;

    @BeforeEach
    void addresses() throws Exception {
        this.other = InetAddress.getByName("198.51.100.4");
        this.elsewhere = InetAddress.getByName("192.0.2.200");
    }

    private Punishment punish(final PunishmentType type, final Victim victim, final Actor by) {
        return this.punish(type, victim, by, Duration.ofDays(30));
    }

    private Punishment punish(final PunishmentType type, final Victim victim, final Actor by,
            final Duration duration) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type, victim,
                by).duration(duration).build(), PunishOptions.defaults()).join())
                .punishment();
    }

    private Punishment reload(final Punishment punishment) {
        return this.service.byId(punishment.id()).join().orElseThrow();
    }

    private LoginDecision login(final UUID uuid, final InetAddress address) {
        return this.loginGate.check(new LoginAttempt(uuid, "Someone", address, null)).join();
    }

    private void connect(final UUID uuid, final String name, final InetAddress address) {
        this.players.recordConnection(uuid, name, this.now.get(), address, "lobby").join();
    }

    private static <T> T ok(final PlayerDataResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(PlayerDataResult.Success.class);
        return ((PlayerDataResult.Success<T>) result).value();
    }

    private String auditValues() {
        final List<String> values = this.read(dsl -> dsl.select(
                        SakuraTables.AUDIT_LOG.OLD_VALUE, SakuraTables.AUDIT_LOG.NEW_VALUE)
                .from(SakuraTables.AUDIT_LOG)
                .fetch(row -> Objects.toString(row.value1(), "") + " "
                        + Objects.toString(row.value2(), "")));
        return String.join("\n", values);
    }

    @Test
    void anExportHasEverythingAboutTheAccount() throws Exception {
        this.connect(STEVE, "Steve", this.home);
        final Punishment ban = this.punish(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR);
        // Steve acts as staff, online.
        this.platform.join(STEVE, "Steve", this.home).grant(staffPermissions());
        final Punishment issued = this.punish(PunishmentType.WARN, PlayerVictim.of(ALEX),
                STEVE_STAFF);
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
        Assertions.assertThat(export.allowEntries()).hasSize(1);
        Assertions.assertThat(export.auditEntries()).isNotEmpty();
    }

    @Test
    void erasingKeepsPunishmentsAndUuidsAndRemovesNamesAndAddresses() {
        this.connect(STEVE, "Steve", this.other);
        this.connect(STEVE, "Steve", this.home);
        // Composite ban with evidence: kept, address cleared, evidence gone.
        final Punishment composite = this.punish(PunishmentType.BAN,
                CompositeVictim.of(STEVE, this.home), MODERATOR);
        this.service.attachEvidence(composite.id(), EvidenceRef.of("https://example.org/e"),
                MODERATOR).join();
        // Ended mute: kept.
        final Punishment mute = this.punish(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                MODERATOR);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(mute.id()),
                MODERATOR).build()).join();
        // An address ban only he was seen on: cleared. One on a stranger's: untouched.
        final Punishment address = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        final Punishment stranger = this.punish(PunishmentType.BAN,
                AddressVictim.of(this.elsewhere), MODERATOR);
        // What Steve did as staff keeps his UUID but not his name.
        // Steve acts as staff, online.
        this.platform.join(STEVE, "Steve", this.home).grant(staffPermissions());
        final Punishment issued = this.punish(PunishmentType.WARN, PlayerVictim.of(ALEX),
                STEVE_STAFF);
        this.service.attachEvidence(issued.id(), EvidenceRef.of("https://example.org/w"),
                STEVE_STAFF).join();
        // His template use as staff keeps his UUID but not his name either.
        this.templates.create(TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spam").build(), CONSOLE).join();
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(ALEX), STEVE_STAFF).template("spam").build(),
                PunishOptions.defaults()).join()).isInstanceOf(PunishResult.Success.class);
        this.allowList.allow(this.elsewhere, STEVE_STAFF, null).join();
        this.allowList.allow(this.home, MODERATOR, null).join();
        this.allowList.allow(STEVE, MODERATOR, "streamer").join();
        this.names.put(STEVE, "Steve");

        final EraseResult erased = ok(this.playerData.erase(STEVE, MODERATOR).join());

        Assertions.assertThat(erased.namesRemoved()).isEqualTo(1);
        Assertions.assertThat(erased.addressesRemoved()).isEqualTo(2);
        Assertions.assertThat(erased.allowedAddressesRemoved()).isEqualTo(1);
        Assertions.assertThat(erased.victimAddressesCleared()).isEqualTo(2);
        Assertions.assertThat(erased.evidenceRemoved()).isEqualTo(1);
        Assertions.assertThat(erased.punishmentsKept()).isEqualTo(2);
        Assertions.assertThat(erased.namesRedacted()).isGreaterThanOrEqualTo(4);

        Assertions.assertThat(this.reload(mute).end()).isPresent();
        final Punishment kept = this.reload(composite);
        Assertions.assertThat(kept.victim()).isInstanceOfSatisfying(ErasedAddressVictim.class,
                victim -> Assertions.assertThat(victim.uuid()).contains(STEVE));
        Assertions.assertThat(kept.evidence()).isEmpty();
        Assertions.assertThat(kept.isActiveAt(this.now.get())).isTrue();
        Assertions.assertThat(this.reload(address).victim())
                .isInstanceOfSatisfying(ErasedAddressVictim.class,
                        victim -> Assertions.assertThat(victim.uuid()).isEmpty());
        Assertions.assertThat(this.reload(stranger).victim())
                .isEqualTo(AddressVictim.of(this.elsewhere));
        final Punishment warn = this.reload(issued);
        Assertions.assertThat(warn.operator()).isEqualTo(PlayerOperator.of(STEVE, "erased"));
        Assertions.assertThat(warn.evidence()).singleElement().satisfies(evidence ->
                Assertions.assertThat(evidence.addedBy().operator())
                        .isEqualTo(PlayerOperator.of(STEVE, "erased")));
        Assertions.assertThat(this.service.activeBan(STEVE, null,
                GlobalScope.INSTANCE).join()).isPresent();

        Assertions.assertThat(this.players.profile(STEVE).join()).isEmpty();
        Assertions.assertThat(this.names.name(STEVE)).isEmpty();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_NAMES)).isZero();
        Assertions.assertThat(this.count(SakuraTables.PLAYER_ADDRESSES)).isZero();
        Assertions.assertThat(this.count(SakuraTables.VICTIMS,
                SakuraTables.VICTIMS.ADDRESS.in(this.home.getAddress(),
                        this.other.getAddress()))).isZero();
        Assertions.assertThat(this.count(SakuraTables.TEMPLATE_USES,
                SakuraTables.TEMPLATE_USES.OPERATOR_UUID.eq(STEVE)
                        .and(SakuraTables.TEMPLATE_USES.OPERATOR_NAME.eq("erased"))))
                .isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.ALLOWED_PLAYERS,
                SakuraTables.ALLOWED_PLAYERS.UUID.eq(STEVE))).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.ALLOWED_ADDRESSES,
                SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_NAME.eq("erased"))).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.ACTOR_KIND.eq(OperatorKind.PLAYER)
                        .and(SakuraTables.AUDIT_LOG.ACTOR_UUID.eq(STEVE))))
                .isPositive()
                .isEqualTo(this.count(SakuraTables.AUDIT_LOG,
                        SakuraTables.AUDIT_LOG.ACTOR_NAME.eq("erased")));

        // His addresses are gone from the audit values; his UUID stays.
        Assertions.assertThat(this.auditValues())
                .doesNotContain(this.home.getHostAddress())
                .doesNotContain(this.other.getHostAddress())
                .contains(STEVE.toString());
        Assertions.assertThat(this.published).anySatisfy(packet -> Assertions.assertThat(packet)
                .isInstanceOf(SyncPacket.PlayerDataErasedPacket.class));
        Assertions.assertThat(this.events(PlayerDataErasedEvent.class)).hasSize(1);
    }

    @Test
    void anAddressOthersWereSeenOnKeepsItsAddress() {
        this.connect(STEVE, "Steve", this.other);
        this.connect(ALEX, "Alex", this.other);
        this.connect(STEVE, "Steve", this.home);
        final Punishment shared = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        // Alex's own composite on Steve's address belongs to Alex.
        final Punishment alexs = this.punish(PunishmentType.MUTE,
                CompositeVictim.of(ALEX, this.home), MODERATOR);

        final EraseResult erased = ok(this.playerData.erase(STEVE, MODERATOR).join());

        Assertions.assertThat(erased.victimAddressesCleared()).isZero();
        Assertions.assertThat(this.reload(shared).victim())
                .isEqualTo(AddressVictim.of(this.other));
        Assertions.assertThat(this.reload(alexs).victim())
                .isEqualTo(CompositeVictim.of(ALEX, this.home));
    }

    @Test
    void anErasedAddressBanStillDeniesThatAddressAndGetsItBackAtLogin() {
        this.connect(STEVE, "Steve", this.other);
        final Punishment ban = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        ok(this.playerData.erase(STEVE, MODERATOR).join());
        // Seen on the address after the erase: under NORMAL strictness that history counts.
        this.connect(ALEX, "Alex", this.other);

        // Matched through Alex's history, not his connection: the row stays erased.
        Assertions.assertThat(this.login(ALEX, this.elsewhere))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.reload(ban).victim()).isInstanceOf(ErasedAddressVictim.class);
        Assertions.assertThat(this.login(UUID.randomUUID(), this.elsewhere))
                .isInstanceOf(LoginDecision.Allowed.class);

        Assertions.assertThat(this.login(UUID.randomUUID(), this.other))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.reload(ban).victim()).isEqualTo(AddressVictim.of(this.other));
    }

    @Test
    void anErasedCompositeBanStillDeniesAndGetsItsAddressBackFromItsAccount() {
        this.connect(STEVE, "Steve", this.home);
        final Punishment ban = this.punish(PunishmentType.BAN,
                CompositeVictim.of(STEVE, this.home), MODERATOR);
        ok(this.playerData.erase(STEVE, MODERATOR).join());

        Assertions.assertThat(this.login(STEVE, this.elsewhere))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.login(UUID.randomUUID(), this.home))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.reload(ban).victim()).isInstanceOf(ErasedAddressVictim.class);
        Assertions.assertThat(this.service.query(PunishmentQuery.builder()
                .applicableTo(PunishmentQuery.Applicability.of(STEVE, null,
                        this.config.settings().enforcement().addressStrictness()))
                .build()).join().items()).hasSize(1);

        Assertions.assertThat(this.login(STEVE, this.home))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.reload(ban).victim())
                .isEqualTo(CompositeVictim.of(STEVE, this.home));
    }

    @ParameterizedTest
    @EnumSource(value = AddressStrictness.class, names = {"NORMAL", "STERN", "STRICT"})
    void anErasedAddressMatchesThroughAddressHistories(final AddressStrictness strictness)
            throws Exception {
        final UUID bob = UUID.randomUUID();
        final InetAddress shared = InetAddress.getByName("192.0.2.77");
        this.connect(STEVE, "Steve", this.other);
        final Punishment ban = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        ok(this.playerData.erase(STEVE, MODERATOR).join());
        // Bob used the address; Alex shares another address with Bob.
        this.connect(bob, "Bob", this.other);
        this.connect(bob, "Bob", shared);
        this.connect(ALEX, "Alex", shared);

        Assertions.assertThat(this.applicable(bob, strictness)).containsExactly(ban.id());
        if (strictness == AddressStrictness.NORMAL) {
            Assertions.assertThat(this.applicable(ALEX, strictness)).isEmpty();
        } else {
            Assertions.assertThat(this.applicable(ALEX, strictness)).containsExactly(ban.id());
        }
        Assertions.assertThat(this.reload(ban).victim()).isInstanceOf(ErasedAddressVictim.class);
    }

    private List<Long> applicable(final UUID uuid, final AddressStrictness strictness) {
        return this.service.query(PunishmentQuery.builder()
                        .applicableTo(PunishmentQuery.Applicability.of(uuid, this.elsewhere,
                                strictness))
                        .build()).join().items().stream()
                .map(Punishment::id)
                .toList();
    }

    @Test
    void anErasedAddressBanIsFoundAndRevokedByItsAddress() {
        this.connect(STEVE, "Steve", this.other);
        final Punishment ban = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        ok(this.playerData.erase(STEVE, MODERATOR).join());

        Assertions.assertThat(this.service.query(PunishmentQuery.builder()
                .victim(AddressVictim.of(this.other)).build()).join().items())
                .extracting(Punishment::id).containsExactly(ban.id());
        final RevokeResult result = this.service.revoke(RevocationRequest.builder(
                RevocationTarget.ByVictim.of(PunishmentType.BAN, AddressVictim.of(this.other),
                        null), MODERATOR).build()).join();

        Assertions.assertThat(result).isInstanceOfSatisfying(RevokeResult.Success.class,
                success -> Assertions.assertThat(success.revoked()).extracting(Punishment::id)
                        .containsExactly(ban.id()));
        Assertions.assertThat(this.login(UUID.randomUUID(), this.other))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void punishingTheAddressAgainRestoresTheSameVictim() {
        this.connect(STEVE, "Steve", this.other);
        final Punishment ban = this.punish(PunishmentType.BAN, AddressVictim.of(this.other),
                MODERATOR);
        ok(this.playerData.erase(STEVE, MODERATOR).join());
        Assertions.assertThat(this.reload(ban).victim()).isInstanceOf(ErasedAddressVictim.class);

        final Punishment mute = this.punish(PunishmentType.MUTE, AddressVictim.of(this.other),
                MODERATOR);

        Assertions.assertThat(this.reload(ban).victim()).isEqualTo(AddressVictim.of(this.other));
        Assertions.assertThat(mute.victim()).isEqualTo(AddressVictim.of(this.other));
        Assertions.assertThat(this.count(SakuraTables.VICTIMS,
                SakuraTables.VICTIMS.KIND.eq(VictimKind.ADDRESS))).isEqualTo(1);
    }

    @Test
    void draftsCantNameAnErasedVictim() {
        Assertions.assertThatThrownBy(() -> this.service.punish(DraftPunishment.builder(
                        PunishmentType.BAN, ErasedAddressVictim.of("ab".repeat(32), null),
                        MODERATOR).build(), PunishOptions.defaults()).join())
                .isInstanceOf(ValidationException.class);
    }
}
