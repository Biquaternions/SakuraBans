package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.serlith.sakurabans.api.event.PunishmentCreateEvent;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.PunishmentRevokeEvent;
import net.serlith.sakurabans.api.event.PunishmentRevokedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.SakuraRevokeResult;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraPunishmentServiceTest extends ServiceTest {

    private DraftPunishment.Builder draft(final PunishmentType type) {
        return DraftPunishment.builder(type, PlayerVictim.of(STEVE), MODERATOR);
    }

    private PunishResult punish(final DraftPunishment.Builder draft) {
        return this.punish(draft, PunishOptions.defaults());
    }

    private PunishResult punish(final DraftPunishment.Builder draft,
            final PunishOptions options) {
        return this.service.punish(draft.build(), options).join();
    }

    private Punishment punished(final DraftPunishment.Builder draft) {
        final PunishResult result = this.punish(draft);
        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        return ((PunishResult.Success) result).punishment();
    }

    private static PunishResult.Failure.Reason failure(final PunishResult result) {
        Assertions.assertThat(result).isInstanceOf(PunishResult.Failure.class);
        return ((PunishResult.Failure) result).reason();
    }

    private RevokeResult revoke(final RevocationRequest.Builder request) {
        return this.service.revoke(request.build()).join();
    }

    @Test
    void bansGetTheDefaultsAndAreStoredEnforcedAndAnnounced() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        final Punishment ban = this.punished(this.draft(PunishmentType.BAN));

        Assertions.assertThat(ban.shadow()).isFalse();
        Assertions.assertThat(ban.scope()).isEqualTo(GlobalScope.INSTANCE);
        Assertions.assertThat(ban.reason()).isEqualTo("No reason specified");
        Assertions.assertThat(ban.permanent()).isTrue();
        Assertions.assertThat(ban.originServer()).contains("lobby");
        Assertions.assertThat(ban.source()).isEqualTo("in-game");
        Assertions.assertThat(this.service.byId(ban.id()).join()).contains(ban);
        Assertions.assertThat(steve.disconnectReason()).hasValueSatisfying(screen ->
                Assertions.assertThat(plain(screen))
                        .contains("Staff: Mod", "Expiration: permanent"));
        Assertions.assertThat(this.published)
                .containsExactly(SyncPacket.PunishmentEnforcePacket.of(ban.id()));
        Assertions.assertThat(this.events).hasSize(2);
        Assertions.assertThat(this.events.get(0)).isInstanceOf(PunishmentCreateEvent.class);
        Assertions.assertThat(this.events.get(1)).isInstanceOfSatisfying(
                PunishmentCreatedEvent.class,
                event -> Assertions.assertThat(event.punishment()).isEqualTo(ban));
    }

    @Test
    void theOverridePolicyDecidesAboutAnExistingBan() {
        final Punishment first = this.punished(this.draft(PunishmentType.BAN)
                .duration(Duration.ofDays(1)));

        final PunishResult rejected = this.punish(this.draft(PunishmentType.BAN));
        Assertions.assertThat(failure(rejected))
                .isEqualTo(PunishResult.Failure.Reason.ALREADY_PUNISHED);
        Assertions.assertThat(((PunishResult.Failure) rejected).conflicting()).contains(first);

        this.published.clear();
        final PunishResult replaced = this.punish(this.draft(PunishmentType.BAN),
                PunishOptions.builder().override(OverridePolicy.REPLACE).build());
        Assertions.assertThat(replaced).isInstanceOfSatisfying(PunishResult.Success.class,
                success -> Assertions.assertThat(success.superseded()).hasValueSatisfying(old ->
                        Assertions.assertThat(old.end().orElseThrow().kind())
                                .isEqualTo(EndKind.SUPERSEDED)));
        // One packet: the new punishment, naming the one it replaced.
        Assertions.assertThat(this.published).singleElement()
                .isEqualTo(new SyncPacket.PunishmentEnforcePacket(
                        ((PunishResult.Success) replaced).punishment().id(), first.id()));
    }

    private void template(final TemplateBuilder template) {
        this.templates.create(template.reason("Reason").build(), CONSOLE).join();
    }

    @Test
    void draftsAndTemplatesDecideTheModifiers() {
        final Punishment shadow = this.punished(this.draft(PunishmentType.MUTE).shadow(true));
        Assertions.assertThat(shadow.shadow()).isTrue();

        this.template(TemplateBuilder.preset("quiet", PunishmentType.MUTE).shadow(true));
        final Punishment quiet = this.punished(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(ALEX), MODERATOR).template("quiet"));
        Assertions.assertThat(quiet.shadow()).isTrue();

        Assertions.assertThat(failure(this.punish(this.draft(PunishmentType.BAN)
                .template("quiet")))).isEqualTo(
                        PunishResult.Failure.Reason.TEMPLATE_TYPE_MISMATCH);
        Assertions.assertThatThrownBy(() -> this.punish(this.draft(PunishmentType.BAN)
                        .shadow(true)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void templatesCanHaveTheirOwnScope() {
        this.template(TemplateBuilder.preset("elimination", PunishmentType.BAN)
                .scope(CategoryScope.of("lifesteal")));

        final Punishment eliminated =
                this.punished(this.draft(PunishmentType.BAN).template("elimination"));
        final Punishment everywhere = this.punished(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(ALEX), MODERATOR).template("elimination")
                .scope(GlobalScope.INSTANCE));

        Assertions.assertThat(eliminated.scope()).isEqualTo(CategoryScope.of("lifesteal"));
        Assertions.assertThat(everywhere.scope()).isEqualTo(GlobalScope.INSTANCE);
    }

    @Test
    void anIpTemplateBansTheLastKnownAddress() {
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.home, null).join();
        this.template(TemplateBuilder.preset("ip-hacking", PunishmentType.BAN).ip(true));

        final Punishment ipBan =
                this.punished(this.draft(PunishmentType.BAN).template("ip-hacking"));

        Assertions.assertThat(ipBan.victim()).isEqualTo(AddressVictim.of(this.home));
        Assertions.assertThatThrownBy(() -> this.punish(DraftPunishment.builder(
                        PunishmentType.BAN, PlayerVictim.of(ALEX), MODERATOR)
                        .template("ip-hacking")))
                .hasCauseInstanceOf(ValidationException.class);
    }

    @Test
    void warningsExpireAfterTheConfiguredTime() {
        final Punishment warn = this.punished(this.draft(PunishmentType.WARN));

        Assertions.assertThat(warn.expiresAt()).contains(this.now.get().plus(Duration.ofDays(30)));
        Assertions.assertThat(this.punished(this.draft(PunishmentType.WARN)).id())
                .isNotEqualTo(warn.id());
    }

    @Test
    void mutesReachTheCacheAndTellThePlayerUnlessShadowed() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.mutes.load(STEVE, this.home).join();

        final Punishment mute = this.punished(this.draft(PunishmentType.MUTE).reason("spam"));

        Assertions.assertThat(this.service.cachedMute(STEVE)).contains(mute);
        Assertions.assertThat(steve.messages()).singleElement()
                .satisfies(message -> Assertions.assertThat(plain(message)).contains("spam"));

        final FakeOnlinePlayer alex = this.platform.join(ALEX, "Alex", this.home);
        final Punishment shadow = this.punished(DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(ALEX), MODERATOR).shadow(true));
        Assertions.assertThat(this.service.cachedMute(ALEX)).contains(shadow);
        Assertions.assertThat(alex.messages()).isEmpty();
    }

    @Test
    void addressBansReachEveryAccountThatUsedTheAddress() {
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.home, null).join();
        final FakeOnlinePlayer alex = this.platform.join(ALEX, "Alex",
                InetAddresses.parseLiteral("198.51.100.1")
                        .orElseThrow());
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.punished(DraftPunishment.builder(PunishmentType.BAN, AddressVictim.of(this.home),
                CONSOLE));

        Assertions.assertThat(steve.disconnectReason()).isPresent();
        Assertions.assertThat(alex.disconnectReason()).isPresent();
    }

    @Test
    void punishmentsInOtherScopesArentEnforcedHere() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.punished(this.draft(PunishmentType.BAN).scope(ServerScope.of("survival")));

        Assertions.assertThat(steve.disconnectReason()).isEmpty();
    }

    @Test
    void theEnforcementOptionDecidesWhereItTakesEffect() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.punished(this.draft(PunishmentType.KICK));
        Assertions.assertThat(steve.disconnectReason()).isPresent();

        final FakeOnlinePlayer alex = this.platform.join(ALEX, "Alex", this.home);
        this.published.clear();
        Assertions.assertThat(this.punish(DraftPunishment.builder(PunishmentType.BAN,
                        PlayerVictim.of(ALEX), MODERATOR),
                PunishOptions.builder().enforce(false).build()))
                .isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(alex.disconnectReason()).isEmpty();
        Assertions.assertThat(this.published).isEmpty();
    }

    @Test
    void createHandlersCanCancelButNotChangeThePunishment() {
        final List<DraftPunishment> seen = new CopyOnWriteArrayList<>();
        this.bus.subscribe(PunishmentCreateEvent.class, event -> {
            seen.add(event.draft());
            event.cancelled(event.draft().type() == PunishmentType.KICK);
        });

        Assertions.assertThat(failure(this.punish(this.draft(PunishmentType.KICK))))
                .isEqualTo(PunishResult.Failure.Reason.CANCELLED);
        final Punishment ban = this.punished(this.draft(PunishmentType.BAN).reason("original")
                .duration(Duration.ofDays(2)));

        // What is stored is exactly the draft the handlers saw.
        final DraftPunishment stored = seen.getLast();
        Assertions.assertThat(ban.reason()).isEqualTo(stored.reason().orElseThrow());
        Assertions.assertThat(ban.scope()).isEqualTo(stored.scope().orElseThrow());
        Assertions.assertThat(ban.victim()).isEqualTo(stored.victim());
        Assertions.assertThat(ban.operator()).isEqualTo(stored.actor().operator());
        Assertions.assertThat(ban.shadow()).isEqualTo(stored.shadow());
        Assertions.assertThat(ban.expiresAt()).contains(ban.createdAt()
                .plus(stored.duration().orElseThrow()));
        Assertions.assertThat(ban.visibility()).isEqualTo(Visibility.NORMAL);
    }

    @Test
    void theCreateEventSeesTheResolvedValues() {
        final List<DraftPunishment> seen = new CopyOnWriteArrayList<>();
        this.bus.subscribe(PunishmentCreateEvent.class, event -> seen.add(event.draft()));

        this.punished(this.draft(PunishmentType.WARN));

        Assertions.assertThat(seen).singleElement().satisfies(draft -> {
            Assertions.assertThat(draft.shadow()).isFalse();
            Assertions.assertThat(draft.scope()).contains(GlobalScope.INSTANCE);
            Assertions.assertThat(draft.reason()).contains("No reason specified");
            Assertions.assertThat(draft.duration()).contains(Duration.ofDays(30));
        });
    }

    @Test
    void deniedActionsAreForbidden() {
        // The moderator without permissions.
        this.platform.join(MOD, "Mod", this.home);

        Assertions.assertThat(failure(this.punish(this.draft(PunishmentType.BAN))))
                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN);
        Assertions.assertThat(this.events).isEmpty();
    }

    @Test
    void refusesWorkWhileNotRunning() {
        this.lifecycle.advanceTo(LifecycleState.STOPPING);

        Assertions.assertThatThrownBy(() -> this.punish(this.draft(PunishmentType.BAN)))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokesByIdAndAnnouncesIt() {
        this.platform.join(STEVE, "Steve", this.home);
        this.mutes.load(STEVE, this.home).join();
        final Punishment mute = this.punished(this.draft(PunishmentType.MUTE));
        this.events.clear();
        this.published.clear();

        final RevokeResult result = this.revoke(RevocationRequest.builder(
                RevocationTarget.ById.of(mute.id()), CONSOLE).reason("appeal accepted")
                .visibility(Visibility.SILENT));

        Assertions.assertThat(result).isInstanceOfSatisfying(RevokeResult.Success.class,
                success -> Assertions.assertThat(success.revoked()).singleElement()
                        .satisfies(revoked -> {
                            Assertions.assertThat(revoked.end().orElseThrow().kind())
                                    .isEqualTo(EndKind.REVOKED);
                            Assertions.assertThat(revoked.end().orElseThrow().reason())
                                    .contains("appeal accepted");
                        }));
        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        Assertions.assertThat(this.published)
                .containsExactly(
                        new SyncPacket.PunishmentRevokePacket(mute.id(), Visibility.SILENT));
        Assertions.assertThat(this.events).hasSize(2);
        Assertions.assertThat(this.events.get(0)).isInstanceOf(PunishmentRevokeEvent.class);
        Assertions.assertThat(this.events.get(1)).isInstanceOfSatisfying(
                SakuraPunishmentRevokedEvent.class, event ->
                        Assertions.assertThat(event.visibility()).isEqualTo(Visibility.SILENT));
        Assertions.assertThat(this.revoke(RevocationRequest.builder(
                RevocationTarget.ById.of(mute.id()), CONSOLE)))
                .isEqualTo(new SakuraRevokeResult.Failure(
                        RevokeResult.Failure.Reason.NOT_FOUND));
    }

    @Test
    void revokingOwnOnlyNeedsTheSameOperator() {
        final Punishment ban = this.punished(this.draft(PunishmentType.BAN));
        final Actor other = Actor.of(PlayerOperator.of(ALEX, "Other"), "in-game");

        Assertions.assertThat(this.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()), other).ownOnly(true)))
                .isInstanceOfSatisfying(RevokeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(RevokeResult.Failure.Reason.NOT_OWN));
        Assertions.assertThat(this.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()),
                        Actor.of(PlayerOperator.of(MOD, "Renamed"), "web")).ownOnly(true)))
                .isInstanceOf(RevokeResult.Success.class);
    }

    @Test
    void revokingByVictimEndsEveryScopeUnlessOneIsNamed() {
        this.punished(this.draft(PunishmentType.BAN));
        this.punished(this.draft(PunishmentType.BAN).scope(ServerScope.of("survival")));
        this.punished(this.draft(PunishmentType.BAN).scope(ServerScope.of("creative")));

        Assertions.assertThat(this.revoke(RevocationRequest.builder(RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), ServerScope.of("survival")),
                        CONSOLE)))
                .isInstanceOfSatisfying(RevokeResult.Success.class, success ->
                        Assertions.assertThat(success.revoked()).hasSize(1));
        Assertions.assertThat(this.revoke(RevocationRequest.builder(RevocationTarget.ByVictim.of(
                        PunishmentType.BAN, PlayerVictim.of(STEVE), null), CONSOLE)))
                .isInstanceOfSatisfying(RevokeResult.Success.class, success ->
                        Assertions.assertThat(success.revoked()).hasSize(2));
    }

    @Test
    void revokeHandlersCanCancel() {
        final Punishment ban = this.punished(this.draft(PunishmentType.BAN));
        this.bus.subscribe(PunishmentRevokeEvent.class, event -> event.cancelled(true));

        Assertions.assertThat(this.revoke(RevocationRequest.builder(
                        RevocationTarget.ById.of(ban.id()), CONSOLE)))
                .isInstanceOfSatisfying(RevokeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(RevokeResult.Failure.Reason.CANCELLED));
        Assertions.assertThat(this.service.byId(ban.id()).join().orElseThrow().end()).isEmpty();
        Assertions.assertThat(this.events(PunishmentRevokedEvent.class)).isEmpty();
    }

    @Test
    void checksBansAndMutesTheWayLoginDoes() {
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.home, null).join();
        final Punishment ban = this.punished(DraftPunishment.builder(PunishmentType.BAN,
                AddressVictim.of(this.home), CONSOLE).scope(ServerScope.of("survival")));

        Assertions.assertThat(this.service.activeBan(ALEX, null, ServerScope.of("survival"))
                .join()).contains(ban);
        Assertions.assertThat(this.service.activeBan(ALEX, null, GlobalScope.INSTANCE).join())
                .isEmpty();
        Assertions.assertThat(this.service.activeMute(ALEX, this.home, GlobalScope.INSTANCE)
                .join()).isEmpty();
        Assertions.assertThat(this.service.byDisplayId(Long.toString(ban.id())).join())
                .contains(ban);
        Assertions.assertThat(this.service.byDisplayId("not-an-id").join()).isEmpty();
    }
}
