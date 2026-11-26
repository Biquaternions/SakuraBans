package net.serlith.sakurabans.core.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.model.ImportedPlayer;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.SchemaManager;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.ImportRepository;
import net.serlith.sakurabans.core.storage.repository.ImportRow;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import net.serlith.sakurabans.core.storage.repository.NewPunishment;
import net.serlith.sakurabans.core.storage.repository.PunishmentMapper;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.storage.repository.TemplateRepository;
import org.assertj.core.api.Assertions;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/** {@code /sb import self}: another SakuraBans database (H2) into this empty one (plan S38). */
class SelfImportTest extends ImportTest {

    private static final String SECRET = "ab".repeat(32);

    private Instant ago(final Duration duration) {
        return this.now.get().minus(duration);
    }

    private NewPunishment punishment(final PunishmentType type, final Scope scope,
            final String externalId) {
        return new NewPunishment(type, false, PlayerVictim.of(STEVE), scope,
                "reason " + externalId, MODERATOR, this.ago(Duration.ofDays(2)), null, null,
                Visibility.NORMAL, null, null, List.of(), "old:" + externalId);
    }

    /** Sets up the other database in its own H2 file and points {@code self} at it. */
    private void source(final Consumer<StorageService> fill) throws Exception {
        final Path old = this.dir.resolve("old");
        Files.createDirectories(old);
        final StorageService source = new StorageService(old,
                TestConfig.load(old, "", this.log), new SchemaManager(this.log.logger()),
                this.executors, this.log.logger());
        source.start();
        try {
            new TemplateRepository(source, AltPolicy.NONE).create(TemplateDefinition.builder(
                            "lifesteal", PunishmentType.BAN)
                    .ip(true)
                    .scope(CategoryScope.of("lifesteal"))
                    .reason("Out of hearts")
                    .build(), CONSOLE, this.ago(Duration.ofDays(8))).join();
            new ImportRepository(source).write(List.of(
                            ImportedPlayer.named(STEVE, "Steve", this.ago(Duration.ofDays(5))),
                            ImportedPlayer.at(STEVE, this.home, this.ago(Duration.ofDays(5)))),
                    List.of(new ImportRow(this.punishment(PunishmentType.BAN,
                                    GlobalScope.INSTANCE, "1"), null, null, null, null),
                            new ImportRow(this.punishment(PunishmentType.BAN,
                                    ServerScope.of("survival"), "2"), null, null, null, null),
                            new ImportRow(this.punishment(PunishmentType.MUTE,
                                    GlobalScope.INSTANCE, "3"), EndKind.REVOKED,
                                    this.ago(Duration.ofDays(1)), PlayerOperator.of(MOD, "Mod"),
                                    "appeal")),
                    this.now.get()).join();
            new AllowListRepository(source).add(ALEX, CONSOLE, this.ago(Duration.ofDays(3)),
                    "trusted").join();
            new MetaRepository(source).put("display_id_secret", SECRET).join();
            // A gap in the ids, which the copy must keep.
            source.write(configuration -> {
                final long first = configuration.dsl().select(DSL.min(SakuraTables.PUNISHMENTS.ID))
                        .from(SakuraTables.PUNISHMENTS).fetchSingle().value1();
                configuration.dsl().deleteFrom(SakuraTables.ACTIVE_PUNISHMENTS)
                        .where(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID.eq(first))
                        .execute();
                return configuration.dsl().deleteFrom(SakuraTables.PUNISHMENTS)
                        .where(SakuraTables.PUNISHMENTS.ID.eq(first)).execute();
            }).join();
            final PunishmentRepository punishments =
                    new PunishmentRepository(source, new PunishmentMapper(Long::toString));
            for (final long id : List.of(2L, 3L)) {
                punishments.evidence(id, EvidenceRef.of("https://example.com/" + id), true,
                        CONSOLE, this.ago(Duration.ofDays(1))).join();
            }
            fill.accept(source);
        } finally {
            source.stop();
        }
        this.section("self", "  jdbc-url: 'jdbc:h2:file:" + old.resolve("sakurabans")
                + "'\n  username: sa\n  password: ''\n  table-prefix: sb_\n");
    }

    private List<Long> punishmentIds() {
        return this.read(dsl -> dsl.select(SakuraTables.PUNISHMENTS.ID)
                .from(SakuraTables.PUNISHMENTS).orderBy(SakuraTables.PUNISHMENTS.ID)
                .fetch(SakuraTables.PUNISHMENTS.ID));
    }

    @Test
    void everyTableIsCopiedWithItsIdsAndNewRowsComeAfterThem() throws Exception {
        this.source(source -> { });

        final ImportReport report = this.importFrom("self");

        Assertions.assertThat(report.imported()).isEqualTo(2);
        Assertions.assertThat(report.players()).isEqualTo(1);
        Assertions.assertThat(report.tables()).containsEntry("punishments", 2L)
                .containsEntry("allowed_players", 1L).containsEntry("templates", 1L);
        Assertions.assertThat(this.punishmentIds()).containsExactly(2L, 3L);
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENT_EVIDENCE)).isEqualTo(2);
        Assertions.assertThat(this.count(SakuraTables.PLAYER_ADDRESSES)).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.SCOPES,
                SakuraTables.SCOPES.VALUE.eq("survival"))).isEqualTo(1);
        Assertions.assertThat(this.templates.template("lifesteal")).hasValueSatisfying(
                template -> {
                    Assertions.assertThat(template.ip()).isTrue();
                    Assertions.assertThat(template.scope())
                            .contains(CategoryScope.of("lifesteal"));
                });
        Assertions.assertThat(this.allowList.isAllowed(ALEX)).isTrue();
        final DisplayIdCodec expected = new DisplayIdCodec(new MetaRepository(this.storage),
                this.config);
        expected.secret(HexFormat.of().parseHex(SECRET));
        Assertions.assertThat(this.displayIds.display(2)).isEqualTo(expected.display(2));

        new ImportRepository(this.storage).write(List.of(), List.of(new ImportRow(
                this.punishment(PunishmentType.WARN, GlobalScope.INSTANCE, "4"), null,
                null, null, null)), this.now.get()).join();
        Assertions.assertThat(this.punishmentIds()).containsExactly(2L, 3L, 4L);

        Assertions.assertThat(this.outcome("self", false))
                .isInstanceOfSatisfying(ImportOutcome.Refused.class, refused ->
                        Assertions.assertThat(refused.problem())
                                .isEqualTo(ImportOutcome.Problem.TARGET_NOT_EMPTY));
    }

    @Test
    void dryRunsCountAndCopyNothing() throws Exception {
        this.source(source -> { });

        final ImportReport report = this.importFrom("self", true);

        Assertions.assertThat(report.dryRun()).isTrue();
        Assertions.assertThat(report.tables()).containsEntry("punishments", 2L);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
        Assertions.assertThat(this.jobs().survey("self").join())
                .isInstanceOfSatisfying(ImportOutcome.Surveyed.class, surveyed ->
                        Assertions.assertThat(surveyed.rows()).containsEntry("players", 1L));
    }

    @Test
    void anotherSchemaVersionIsRefused() throws Exception {
        this.source(source -> new MetaRepository(source).put("schema_version", "99").join());

        Assertions.assertThat(this.outcome("self", false)).isEqualTo(new ImportOutcome.Refused(
                ImportOutcome.Problem.OTHER_VERSION, "99"));
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
    }

    @Test
    void targetsWithDataAndSourcesWithoutTablesAreRefused() throws Exception {
        this.source(source -> { });
        this.players.recordConnection(ALEX, "Alex", this.now.get(), null, null).join();

        Assertions.assertThat(this.outcome("self", false)).isEqualTo(new ImportOutcome.Refused(
                ImportOutcome.Problem.TARGET_NOT_EMPTY, "players, player_names"));

        this.section("self", "  jdbc-url: 'jdbc:h2:file:" + this.dir.resolve("old")
                .resolve("sakurabans") + "'\n  username: sa\n  password: ''\n"
                + "  table-prefix: other_\n");
        Assertions.assertThat(this.outcome("self", false)).isEqualTo(new ImportOutcome.Refused(
                ImportOutcome.Problem.NOT_SAKURABANS, "other_"));
    }
}
