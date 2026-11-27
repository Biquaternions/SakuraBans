package net.serlith.sakurabans.core.template;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.LadderRequest;
import net.serlith.sakurabans.api.punish.LadderResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.StoredTemplate;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S62 on a backend ("lobby", in the categories pvp and games, in that order). */
class TemplateVariantsTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return
                """
                scopes:
                  categories:
                    pvp: [lobby]
                    games: [lobby, skywars]
                """;
    }

    @BeforeEach
    void spam() {
        this.create(mute("spam", "Global", 1, null));
        this.create(mute("spam", "Games", 2, CategoryScope.of("games")));
        this.create(mute("spam", "Pvp", 3, CategoryScope.of("pvp")));
        this.create(mute("spam", "Skywars", 4, ServerScope.of("skywars")));
    }

    static TemplateInfo mute(final String name, final String reason, final int hours,
            final @Nullable Scope variant) {
        return TemplateBuilder.preset(name, PunishmentType.MUTE)
                .reason(reason)
                .duration(TemplateDuration.fixed(Duration.ofHours(hours)))
                .message("<red>" + reason + " mute</red>")
                .variant(variant)
                .build();
    }

    private StoredTemplate create(final TemplateInfo definition) {
        final AdminResult<StoredTemplate> result =
                this.templates.create(definition, CONSOLE).join();
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<StoredTemplate>) result).value();
    }

    private Punishment issue(final @Nullable Scope scope) {
        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR)
                .template("spam").scope(scope).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        final Punishment punishment = ((PunishResult.Success) result).punishment();
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(punishment.id()),
                MODERATOR).build()).join();
        return punishment;
    }

    private String reason(final @Nullable Scope explicit) {
        return this.templates.resolve("spam", explicit, MODERATOR).orElseThrow().template()
                .reason();
    }

    @Test
    void useGetsTheVariantOfWhereItIsIssued() {
        Assertions.assertThat(this.reason(null)).as("this server's first category")
                .isEqualTo("Pvp");
        Assertions.assertThat(this.reason(ServerScope.of("skywars"))).isEqualTo("Skywars");
        Assertions.assertThat(this.reason(ServerScope.of("survival"))).as("no variant there")
                .isEqualTo("Global");
        Assertions.assertThat(this.reason(CategoryScope.of("games"))).isEqualTo("Games");
        Assertions.assertThat(this.reason(CategoryScope.of("creative"))).isEqualTo("Global");
        Assertions.assertThat(this.reason(GlobalScope.INSTANCE)).as("-g").isEqualTo("Global");
        Assertions.assertThat(this.templates.template("spam", ServerScope.of("lobby"))
                .orElseThrow().reason()).isEqualTo("Pvp");
        Assertions.assertThat(this.templates.template("spam").orElseThrow().reason())
                .isEqualTo("Global");

        final Punishment here = this.issue(null);
        Assertions.assertThat(here.reason()).isEqualTo("Pvp");
        Assertions.assertThat(Duration.between(here.createdAt(), here.expiresAt().orElseThrow()))
                .isEqualTo(Duration.ofHours(3));
        Assertions.assertThat(here.template().orElseThrow().variant())
                .hasValue(CategoryScope.of("pvp"));
        final Punishment skywars = this.issue(ServerScope.of("skywars"));
        Assertions.assertThat(skywars.reason()).isEqualTo("Skywars");
        Assertions.assertThat(skywars.scope()).isEqualTo(ServerScope.of("skywars"));
        Assertions.assertThat(this.issue(GlobalScope.INSTANCE).template().orElseThrow()
                .variant()).isEmpty();

        this.create(mute("spam", "Lobby", 5, ServerScope.of("lobby")));
        Assertions.assertThat(this.reason(null)).as("the server's own wins").isEqualTo("Lobby");
    }

    @Test
    void categoriesCountInTheOrderScopesCategoriesDeclaresThem() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"),
                """
                server:
                  name: lobby
                scopes:
                  categories:
                    games: [lobby, skywars]
                    pvp: [lobby]
                """);
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);

        Assertions.assertThat(this.reason(null)).isEqualTo("Games");
    }

    @Test
    void punishmentShowsItsVariantsMessageOnEveryServer() throws Exception {
        final Punishment issued = this.issue(ServerScope.of("skywars"));
        // Read back as another server would, with another server name.
        Files.writeString(this.dir.resolve("config.yml"), "server:\n  name: survival\n");
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        final Punishment stored = this.service.byId(issued.id()).join().orElseThrow();
        Assertions.assertThat(stored.template().orElseThrow().variant())
                .hasValue(ServerScope.of("skywars"));
        Assertions.assertThat(plain(this.enforcementMessages.muted(stored, Locale.US)))
                .isEqualTo("Skywars mute");

        final StoredTemplate variant = this.templates.find("spam", ServerScope.of("skywars"))
                .orElseThrow();
        this.templates.delete(ServerScope.of("skywars"), "spam", variant.version(), CONSOLE)
                .join();
        Assertions.assertThat(plain(this.enforcementMessages.muted(stored, Locale.US)))
                .as("a deleted variant falls back to the global template")
                .isEqualTo("Global mute");
    }

    @Test
    void globalBanFromVariantShowsTheVariantsScreenElsewhere() throws Exception {
        this.create(TemplateBuilder.preset("hacking", PunishmentType.BAN).reason("Hacking")
                .message("Global screen").build());
        this.create(TemplateBuilder.preset("hacking", PunishmentType.BAN).reason("Hacking")
                .message("Pvp screen").variant(CategoryScope.of("pvp")).build());
        final PunishResult result = this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).template("hacking")
                .build(), PunishOptions.defaults()).join();
        final Punishment ban = ((PunishResult.Success) result).punishment();
        Assertions.assertThat(ban.scope()).isEqualTo(GlobalScope.INSTANCE);

        Files.writeString(this.dir.resolve("config.yml"), "server:\n  name: skywars\n");
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        Assertions.assertThat(this.templates.resolve("hacking", null, MODERATOR).orElseThrow()
                .template().message()).as("skywars itself would use the global one")
                .hasValue("Global screen");
        Assertions.assertThat(plain(this.enforcementMessages.banScreen(
                this.service.byId(ban.id()).join().orElseThrow(), Locale.US)))
                .isEqualTo("Pvp screen");
    }

    @Test
    void ladderProgressIsSharedByNameAcrossVariants() {
        this.create(TemplateBuilder.ladder("misconduct").reason("Global")
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.KICK, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open())))
                .groups(Map.of("conduct", 1)).build());
        this.create(TemplateBuilder.ladder("misconduct").reason("Pvp")
                .steps(List.of(TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.MUTE,
                                TemplateDuration.fixed(Duration.ofDays(1)))))
                .variant(CategoryScope.of("pvp")).build());

        final LadderResult first = this.service.applyLadder(LadderRequest.builder("misconduct",
                PlayerVictim.of(STEVE), MODERATOR).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(first).isInstanceOfSatisfying(LadderResult.Punished.class, p -> {
            Assertions.assertThat(p.punishment().reason()).isEqualTo("Pvp");
            Assertions.assertThat(p.punishment().template().orElseThrow().variant())
                    .hasValue(CategoryScope.of("pvp"));
        });
        final LadderResult second = this.service.applyLadder(LadderRequest.builder("misconduct",
                PlayerVictim.of(STEVE), MODERATOR).scope(GlobalScope.INSTANCE).build(),
                PunishOptions.defaults()).join();
        Assertions.assertThat(second).as("the global ladder's second step")
                .isInstanceOfSatisfying(LadderResult.Punished.class, p -> {
                    Assertions.assertThat(p.step()).isEqualTo(1);
                    Assertions.assertThat(p.punishment().type()).isEqualTo(PunishmentType.KICK);
                });
        final LadderResult third = this.service.applyLadder(LadderRequest.builder("misconduct",
                PlayerVictim.of(STEVE), MODERATOR).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(third).as("the variant's third step")
                .isInstanceOfSatisfying(LadderResult.Punished.class, p ->
                        Assertions.assertThat(p.punishment().type())
                                .isEqualTo(PunishmentType.MUTE));
        this.read(dsl -> Assertions.assertThat(dsl.select(
                SakuraTables.TEMPLATE_USES.TEMPLATE_VARIANT_SCOPE_ID).from(
                SakuraTables.TEMPLATE_USES).where(SakuraTables.TEMPLATE_USES.TEMPLATE
                .eq("misconduct")).orderBy(SakuraTables.TEMPLATE_USES.ID).fetch().stream()
                .map(row -> row.value1() == null).toList())
                .containsExactly(false, true, false));
        Assertions.assertThat(this.templates.weights(this.templates.find("misconduct",
                CategoryScope.of("pvp")).orElseThrow().template()))
                .as("the global definition's groups")
                .containsOnlyKeys("misconduct");
    }

    private static List<FieldError> errors(final AdminResult<?> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.ValidationFailed.class);
        return ((AdminResult.ValidationFailed<?>) result).errors();
    }

    @Test
    void variantKeepsItsGlobalTemplatesKindAndGroups() {
        Assertions.assertThat(errors(this.templates.create(TemplateBuilder.preset("spam",
                        PunishmentType.BAN).reason("x").variant(ServerScope.of("lobby")).build(),
                CONSOLE).join())).extracting(FieldError::path).containsExactly("type");
        Assertions.assertThat(errors(this.templates.create(TemplateBuilder.preset("nothing",
                        PunishmentType.BAN).reason("x").variant(ServerScope.of("lobby")).build(),
                CONSOLE).join())).as("no global template").extracting(FieldError::path)
                .containsExactly("name");
        Assertions.assertThat(TemplateBuilder.preset("spam", PunishmentType.MUTE).reason("x")
                .groups(Map.of("chat", 1)).variant(ServerScope.of("lobby")).validate())
                .extracting(FieldError::path).containsExactly("groups");
        final StoredTemplate global = this.templates.find("spam").orElseThrow();
        Assertions.assertThat(errors(this.templates.update(TemplateBuilder.preset("spam",
                        PunishmentType.WARN).reason("x").build(), global.version(), CONSOLE)
                .join()).getFirst().message()).as("its variants are mutes")
                .contains("category:games", "server:skywars", "mute");
        Assertions.assertThat(errors(this.templates.create(mute("spam", "Again", 1,
                ServerScope.of("skywars")), CONSOLE).join())).isNotEmpty();
    }

    @Test
    void deletingTheGlobalTemplateDeletesItsVariants() {
        final StoredTemplate global = this.templates.find("spam").orElseThrow();
        Assertions.assertThat(this.templates.deleteWithVariants(null, "spam", global.version(),
                CONSOLE).join()).isInstanceOfSatisfying(AdminResult.Ok.class, ok ->
                        Assertions.assertThat(((SakuraTemplateService.Deletion) ok.value())
                                .variants()).containsExactly(CategoryScope.of("games"),
                                        CategoryScope.of("pvp"), ServerScope.of("skywars")));
        Assertions.assertThat(this.templates.definitions()).isEmpty();
        Assertions.assertThat(this.templateRepository.all().join()).isEmpty();
        this.read(dsl -> Assertions.assertThat(dsl.fetchCount(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.SUBJECT_ID.like("%spam"))).as("4 created, 4 deleted")
                .isEqualTo(8));
    }
}
