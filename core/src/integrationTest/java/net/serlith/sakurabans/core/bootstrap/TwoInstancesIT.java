package net.serlith.sakurabans.core.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.serlith.sakurabans.api.SakuraBans;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.admin.InstanceRegistry;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.storage.ContainerDatabase;
import net.serlith.sakurabans.core.storage.repository.InstanceRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Two complete instances ({@link CoreBootstrap}, each with its own data folder and fake
 * platform) on one database with SQL sync (S46).
 */
@ParameterizedClass
@EnumSource(ContainerDatabase.class)
class TwoInstancesIT {

    private static final Actor CONSOLE = Actor.of(ConsoleOperator.INSTANCE, "console");
    private static final String KEY = "chat.muted";

    @Parameter
    private ContainerDatabase container;

    @TempDir
    private Path first;

    @TempDir
    private Path second;

    private final RecordingLogger log = new RecordingLogger();
    private CoreComponents alpha;
    private CoreComponents beta;
    private SakuraBansCore coreA;
    private SakuraBansCore coreB;

    private CoreComponents components(final Path dir, final String server, final String reason)
            throws Exception {
        Files.writeString(dir.resolve("config.yml"), "server:\n  name: " + server
                + "\nreasons:\n  default: '" + reason + "'\n");
        return new CoreComponents(new CoreEnvironment(dir, this.log.logger(), false),
                InstantSource.system());
    }

    @BeforeEach
    void start() throws Exception {
        final String storage = this.container.freshStorageYaml().orElseThrow()
                + "sync:\n  mode: SQL\n  poll-interval: 250ms\n";
        Files.writeString(this.first.resolve("storage.yml"), storage);
        Files.writeString(this.second.resolve("storage.yml"), storage);
        this.alpha = this.components(this.first, "alpha", "Reason of A");
        this.beta = this.components(this.second, "beta", "Reason of B");
        this.coreA = CoreBootstrap.create(this.alpha);
        this.coreB = CoreBootstrap.create(this.beta);
        this.coreA.start(new FakePlatform());
        // The API holder takes one instance per JVM; a real network has one per server.
        SakuraBans.unregister(SakuraBans.api());
        this.coreB.start(new FakePlatform());
    }

    @AfterEach
    void stop() {
        this.coreA.stop();
        this.coreB.stop();
    }

    private static void eventually(final BooleanSupplier condition) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            Assertions.assertThat(System.nanoTime()).as("within 15 s").isLessThan(deadline);
            try {
                Thread.sleep(25);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static <T> T ok(final AdminResult<T> result) {
        Assertions.assertThat(result).isInstanceOf(AdminResult.Ok.class);
        return ((AdminResult.Ok<T>) result).value();
    }

    @Test
    void settingOverridesReachTheOtherInstanceAndResetsRestoreItsFile() {
        ok(this.alpha.settingsService().update(Map.of("reasons.default", "From the network"),
                this.alpha.settingsService().version(), CONSOLE).join());

        eventually(() -> this.beta.config().settings().reasons().defaultReason()
                .equals("From the network"));
        Assertions.assertThat(this.beta.settingsService().version())
                .isEqualTo(this.alpha.settingsService().version());

        ok(this.alpha.settingsService().reset(Set.of("reasons.default"),
                this.alpha.settingsService().version(), CONSOLE).join());

        eventually(() -> this.beta.config().settings().reasons().defaultReason()
                .equals("Reason of B"));
        Assertions.assertThat(this.alpha.config().settings().reasons().defaultReason())
                .isEqualTo("Reason of A");
        Assertions.assertThat(this.beta.settingsService().overrides()).isEmpty();
    }

    @Test
    void messageOverridesReachTheOtherInstanceAndResetsRemoveThem() {
        final MessageService messages = this.beta.messages();
        final String shipped = messages.find(KEY, Locale.US).orElseThrow();
        ok(this.alpha.messageTemplates().update(Locale.US, Map.of(KEY, "<red>Hush</red>"),
                this.alpha.messageTemplates().version(Locale.US), CONSOLE).join());

        eventually(() -> messages.find(KEY, Locale.US).orElseThrow().equals("<red>Hush</red>"));
        Assertions.assertThat(this.beta.messageTemplates().version(Locale.US)).isOne();

        ok(this.alpha.messageTemplates().reset(Locale.US, Set.of(KEY),
                this.alpha.messageTemplates().version(Locale.US), CONSOLE).join());
        eventually(() -> messages.find(KEY, Locale.US).orElseThrow().equals(shipped));
    }

    @Test
    void localesWithoutFilesFallBackPerKey() {
        final Locale french = Locale.FRANCE;
        ok(this.alpha.messageTemplates().update(french, Map.of(KEY, "<red>Chut</red>"), 0, CONSOLE)
                .join());

        final MessageService messages = this.beta.messages();
        eventually(() -> messages.find(KEY, french).orElseThrow().equals("<red>Chut</red>"));
        Assertions.assertThat(messages.find("screen.kick", french))
                .isEqualTo(messages.find("screen.kick", Locale.US));
        Assertions.assertThat(this.beta.messageTemplates().locales()).contains(french, Locale.US);
    }

    @Test
    void templatesAreCreatedUpdatedAndDeletedOnBoth() {
        final TemplateInfo spam = TemplateBuilder.preset("chatter", PunishmentType.MUTE)
                .reason("Spamming").build();
        final long created = ok(this.alpha.templates().create(spam, CONSOLE).join()).version();
        eventually(() -> this.beta.templates().find("chatter").isPresent());

        ok(this.alpha.templates().update(TemplateBuilder.from(spam).reason("Flooding").build(),
                created, CONSOLE).join());
        eventually(() -> this.beta.templates().find("chatter").orElseThrow().template().reason()
                .equals("Flooding"));

        ok(this.beta.templates().delete(null, "chatter", created + 1, CONSOLE).join());
        eventually(() -> this.alpha.templates().find("chatter").isEmpty());
        Assertions.assertThat(List.copyOf(this.alpha.templates().templates()))
                .noneMatch(template -> template.name().equals("chatter"));
    }

    @Test
    void bothServersAreInTheRegistryAndAReloadUpdatesTheirRow() throws Exception {
        this.beta.registry().refresh().join();
        this.alpha.registry().refresh().join();
        Assertions.assertThat(this.alpha.registry().instances())
                .extracting(InstanceRepository.Instance::serverName)
                .containsExactlyInAnyOrder("alpha", "beta");
        Assertions.assertThat(this.alpha.registry().instances()).filteredOn(row ->
                row.serverName().equals("beta")).singleElement().satisfies(row ->
                Assertions.assertThat(InstanceRegistry.fileValues(row))
                        .containsEntry("reasons.default", "Reason of B"));

        Files.writeString(this.second.resolve("config.yml"),
                "server:\n  name: beta\n  categories: [arena]\n");
        Assertions.assertThat(this.coreB.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        this.beta.registry().refresh().join();
        this.alpha.registry().refresh().join();
        Assertions.assertThat(this.alpha.registry().instances()).filteredOn(row ->
                row.serverName().equals("beta")).singleElement().satisfies(row ->
                Assertions.assertThat(row.categories()).containsExactly("arena"));
    }

    private void set(final CoreComponents on, final Scope scope, final String key,
            final Object value) {
        ok(on.settingsService().change(scope, Map.of(key, value), Set.of(),
                on.settingsService().version(scope), CONSOLE).join());
    }

    private static String reason(final CoreComponents on) {
        return on.config().settings().reasons().defaultReason();
    }

    @Test
    void scopedOverridesReachTheServersTheyName() {
        this.set(this.alpha, ServerScope.of("alpha"), "reasons.default", "Alpha only");
        eventually(() -> this.beta.settingsService().version(ServerScope.of("alpha")) == 1);
        Assertions.assertThat(reason(this.alpha)).isEqualTo("Alpha only");
        Assertions.assertThat(reason(this.beta)).isEqualTo("Reason of B");

        // A category with both: both get it; two categories with one key: the first declared.
        this.set(this.alpha, GlobalScope.INSTANCE, "scopes.categories",
                Map.of("arena", List.of("alpha", "beta")));
        this.set(this.alpha, CategoryScope.of("arena"), "reasons.default", "Arena");
        eventually(() -> reason(this.beta).equals("Arena"));
        Assertions.assertThat(reason(this.alpha)).isEqualTo("Alpha only");
        // A third server outside the category, resolved as it would resolve itself.
        final InstanceRepository.Instance gamma = new InstanceRepository.Instance("gamma-id",
                "gamma", List.of(), "paper", "1", Instant.EPOCH, Instant.EPOCH,
                "{\"reasons.default\":\"Reason of C\"}");
        Assertions.assertThat(this.alpha.settingsService().effectiveOn(gamma)
                .get("reasons.default").value()).isEqualTo("Reason of C");

        final Map<String, List<String>> two = new LinkedHashMap<>();
        two.put("first", List.of("beta"));
        two.put("arena", List.of("alpha", "beta"));
        this.set(this.alpha, GlobalScope.INSTANCE, "scopes.categories", two);
        this.set(this.alpha, CategoryScope.of("first"), "reasons.default", "First");
        eventually(() -> reason(this.beta).equals("First"));

        // A global-only key isn't accepted in a server scope.
        Assertions.assertThat(this.alpha.settingsService().change(ServerScope.of("beta"),
                Map.of("enforcement.address-strictness", "STRICT"), Set.of(), 0, CONSOLE)
                .join()).isInstanceOf(AdminResult.ValidationFailed.class);
    }

    @Test
    void publishedFileValuesBecomeEveryonesGlobalValue() {
        final InstanceRepository.Instance self = this.alpha.registry().self();
        Assertions.assertThat(this.alpha.settingsService().publishable(self,
                Set.of("reasons.default"))).containsOnlyKeys("reasons.default");

        ok(this.alpha.settingsService().publish(self, Set.of("reasons.default"),
                this.alpha.settingsService().version(), CONSOLE).join());

        eventually(() -> reason(this.beta).equals("Reason of A"));
    }

    @Test
    void scopedMessagesReachTheServersTheyName() {
        final String key = "screen.ban";
        final String shipped = this.beta.messages().find(key, Locale.US).orElseThrow();
        ok(this.alpha.messageTemplates().change(ServerScope.of("alpha"), Locale.US,
                Map.of(key, "<red>Banned from Alpha.</red>"), Set.of(), 0, CONSOLE).join());
        eventually(() -> this.beta.messageTemplates().version(ServerScope.of("alpha"),
                Locale.US) == 1);
        Assertions.assertThat(this.alpha.messages().find(key, Locale.US))
                .contains("<red>Banned from Alpha.</red>");
        Assertions.assertThat(this.beta.messages().find(key, Locale.US)).contains(shipped);

        this.set(this.alpha, GlobalScope.INSTANCE, "scopes.categories",
                Map.of("arena", List.of("alpha", "beta")));
        ok(this.alpha.messageTemplates().change(CategoryScope.of("arena"), Locale.US,
                Map.of(key, "<red>Banned from the arena.</red>"), Set.of(), 0, CONSOLE).join());
        eventually(() -> this.beta.messages().find(key, Locale.US).orElseThrow()
                .equals("<red>Banned from the arena.</red>"));
        // The server's own override still wins over its category's.
        Assertions.assertThat(this.alpha.messages().find(key, Locale.US))
                .contains("<red>Banned from Alpha.</red>");
    }
}
