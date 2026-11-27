package net.serlith.sakurabans.core.template;

import java.nio.file.Files;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.data.PlayerDataResult;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.AuditEntry;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.VictimRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S63: an audit entry for action-only ladder steps; a preset's node checked in the service.
 * S64: an address is never its subject.
 */
class ActionOnlyAuditAndPresetPermissionTest extends ServiceTest {

    private static final UUID HELPER = UUID.fromString("3a7b4f1c-0f0e-4c1e-9d55-6c1f2b4a8e02");
    private static final Actor HELPER_ACTOR = Actor.of(PlayerOperator.of(HELPER, "Helper"),
            "in-game");

    @BeforeEach
    void templates() {
        this.templates.create(TemplateBuilder.ladder("probation").reason("Probation")
                .steps(List.of(TemplateStep.none(List.of("say {victim} is on probation")),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.ladder("probation").reason("Probation here")
                .steps(List.of(TemplateStep.none(List.of("say {victim} is on probation here")),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .variant(CategoryScope.of("pvp")).build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("secret", PunishmentType.BAN)
                .reason("Secret").permissionRequired(true).build(), CONSOLE).join();
    }

    private int applyCount() {
        return this.read(dsl -> dsl.fetchCount(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.ACTION.eq(AuditAction.APPLY)));
    }

    private List<AuditEntry> applies(final SubjectType type, final String subject) {
        return new AuditRepository(this.storage).about(type, subject, 100).join().stream()
                .filter(entry -> entry.action() == AuditAction.APPLY).toList();
    }

    private LadderResult apply(final String ladder, final Victim victim, final Actor actor) {
        return this.service.applyLadder(LadderRequest.builder(ladder, victim, actor).build(),
                PunishOptions.defaults()).join();
    }

    @Test
    void actionOnlyStepWritesOneAuditEntry() {
        Assertions.assertThat(this.apply("probation", PlayerVictim.of(STEVE), MODERATOR))
                .isInstanceOf(LadderResult.ActionsOnly.class);

        Assertions.assertThat(this.applyCount()).isOne();
        Assertions.assertThat(this.applies(SubjectType.PLAYER, STEVE.toString())).singleElement()
                .satisfies(entry -> {
                    Assertions.assertThat(entry.actor()).isEqualTo(MODERATOR);
                    Assertions.assertThat(entry.subjectType()).isEqualTo(SubjectType.PLAYER);
                    Assertions.assertThat(entry.subjectId()).isEqualTo(STEVE.toString());
                    Assertions.assertThat(entry.oldValue()).isNull();
                    Assertions.assertThat(entry.newValue())
                            .isEqualTo("template probation step 1 (no punishment)");
                });
        // The player's data export lists it, as an entry about the player.
        final PlayerDataResult<PlayerDataExport> export =
                this.playerData.export(STEVE, MODERATOR).join();
        Assertions.assertThat(((PlayerDataResult.Success<PlayerDataExport>) export).value()
                .auditEntries()).anySatisfy(entry -> Assertions.assertThat(entry.newValue())
                        .hasValue("template probation step 1 (no punishment)"));
        // The next step punishes: audited as a punishment, not as APPLY.
        this.apply("probation", PlayerVictim.of(STEVE), MODERATOR);
        Assertions.assertThat(this.applyCount()).isOne();
    }

    private String hash() {
        return this.hasher.hex(this.home);
    }

    /** No audit row holds the address as text (S64). */
    private void noLiteralAddress() {
        final String text = this.home.getHostAddress();
        Assertions.assertThat(this.read(dsl -> dsl.selectFrom(SakuraTables.AUDIT_LOG).fetch())
                .stream().filter(row -> text.equals(row.subjectId())
                        || row.newValue() != null && row.newValue().contains(text)
                        || row.oldValue() != null && row.oldValue().contains(text))
                .toList()).isEmpty();
    }

    @Test
    void theEntryNamesTheVariantAndAnAddressVictimByItsHash() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                """
                server:
                  name: lobby
                scopes:
                  categories:
                    pvp: [lobby]
                """);
        this.config.reload();
        this.apply("probation", AddressVictim.of(this.home), CONSOLE);

        Assertions.assertThat(this.applyCount()).isOne();
        Assertions.assertThat(this.applies(SubjectType.ADDRESS_HASH, this.hash()))
                .singleElement().satisfies(entry -> {
                    Assertions.assertThat(entry.actor()).isEqualTo(CONSOLE);
                    Assertions.assertThat(entry.subjectType())
                            .isEqualTo(SubjectType.ADDRESS_HASH);
                    Assertions.assertThat(entry.newValue()).isEqualTo(
                            "template probation step 1 variant category:pvp (no punishment)");
                });
        // The same hash as the victim row's.
        final byte[] stored = this.read(dsl -> dsl.select(SakuraTables.VICTIMS.ADDRESS_HASH)
                .from(SakuraTables.VICTIMS)
                .where(SakuraTables.VICTIMS.KIND.eq(VictimKind.ADDRESS))
                .fetchSingle(SakuraTables.VICTIMS.ADDRESS_HASH));
        Assertions.assertThat(HexFormat.of().formatHex(stored)).isEqualTo(this.hash());
        this.noLiteralAddress();
    }

    @Test
    void rangeIsAuditedByItsVictimId() {
        final AddressRangeVictim range = AddressRangeVictim.of(this.home, 24);
        this.apply("probation", range, CONSOLE);

        final long id = new VictimRepository(this.storage, this.hasher).find(range).join()
                .orElseThrow();
        Assertions.assertThat(this.applies(SubjectType.VICTIM, Long.toString(id)))
                .hasSize(1);
        final int literal = this.read(dsl -> dsl.fetchCount(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.SUBJECT_ID.like("%203.0.113%")));
        Assertions.assertThat(literal).isZero();
    }

    @Test
    void erasingThePlayerLeavesTheHashSubject() {
        this.players.recordConnection(STEVE, "Steve", this.clock.instant(), this.home, "lobby")
                .join();
        this.apply("probation", AddressVictim.of(this.home), CONSOLE);
        Assertions.assertThat(this.applies(SubjectType.ADDRESS_HASH, this.hash())).hasSize(1);

        Assertions.assertThat(this.playerData.erase(STEVE, MODERATOR).join())
                .isInstanceOf(PlayerDataResult.Success.class);
        // sb_victims keeps the hash on erase (D28), so the entry stays as it was.
        Assertions.assertThat(this.applies(SubjectType.ADDRESS_HASH, this.hash())).hasSize(1);
        this.noLiteralAddress();
    }

    @Test
    void refusedUseWritesNoEntry() {
        this.platform.join(HELPER, "Helper", this.home);
        Assertions.assertThat(this.apply("probation", PlayerVictim.of(STEVE), HELPER_ACTOR))
                .as("no sakurabans.punish").isInstanceOf(LadderResult.Failure.class);
        Assertions.assertThat(this.apply("nothing", PlayerVictim.of(STEVE), MODERATOR))
                .isInstanceOf(LadderResult.Failure.class);
        Assertions.assertThat(this.applyCount()).isZero();
        Assertions.assertThat(this.templateRepository.history(Set.of("probation"),
                PlayerVictim.of(STEVE), false, null, this.clock.instant()).join()).isEmpty();
    }

    @Test
    void presetsNodeIsCheckedInTheServiceToo() {
        final DraftPunishment draft = DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(STEVE), MODERATOR).template("secret").build();

        Assertions.assertThat(this.service.punish(draft, PunishOptions.defaults()).join())
                .isInstanceOfSatisfying(PunishResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(PunishResult.Failure.Reason.FORBIDDEN));
        Assertions.assertThat(this.service.query(PunishmentQuery.builder().build()).join()
                .items()).isEmpty();

        this.platform.online(MOD).orElseThrow().grant(Permissions.template("secret"));
        Assertions.assertThat(this.service.punish(draft, PunishOptions.defaults()).join())
                .isInstanceOf(PunishResult.Success.class);
        Assertions.assertThat(this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                PlayerVictim.of(ALEX), CONSOLE).template("secret").build(),
                PunishOptions.defaults()).join()).as("the console has every node")
                .isInstanceOf(PunishResult.Success.class);
    }
}
